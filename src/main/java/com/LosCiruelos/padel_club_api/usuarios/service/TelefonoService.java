package com.LosCiruelos.padel_club_api.usuarios.service;

import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.usuarios.entity.EnvioCodigoTelefono;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.VerificationToken;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.TokenType;
import com.LosCiruelos.padel_club_api.Exceptions.CodigoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.LimiteExcedidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.repository.EnvioCodigoTelefonoRepository;
import com.LosCiruelos.padel_club_api.usuarios.repository.VerificationTokenRepository;
import com.LosCiruelos.padel_club_api.Services.Email.EnvioPostCommit;
import com.LosCiruelos.padel_club_api.Services.WhatsApp.WhatsAppServiceFactory;
import com.LosCiruelos.padel_club_api.common.Reloj;

import lombok.extern.slf4j.Slf4j;

/**
 * Verificación del teléfono con un código de 6 dígitos que llega por WhatsApp (igual que el
 * código del mail). Ingresar el código prueba que el número existe, tiene WhatsApp y es del
 * usuario. Solo con el teléfono verificado se le mandan avisos por WhatsApp.
 */
@Slf4j
@Service
public class TelefonoService {

    static final Duration VIGENCIA_CODIGO = Duration.ofMinutes(10);

    static final Duration VENTANA_TOPES = Duration.ofHours(24);

    private final VerificationTokenRepository tokenRepository;
    private final EnvioCodigoTelefonoRepository envios;
    private final UsuarioService usuarioService;
    private final UsuarioRepository usuarioRepository;
    private final WhatsAppServiceFactory whatsApp;
    private final Reloj reloj;
    private final String plantillaCodigo;
    private final Topes topes;

    /**
     * Topes de códigos por WhatsApp (cada uno cuesta y no se puede usar para molestar a un número
     * ajeno): por usuario y por número en 24 h, y un mínimo de segundos entre pedidos del usuario.
     */
    public record Topes(int porUsuarioPorDia, int porNumeroPorDia, int segundosEntrePedidos) {
    }

    public TelefonoService(VerificationTokenRepository tokenRepository, EnvioCodigoTelefonoRepository envios,
            UsuarioService usuarioService, UsuarioRepository usuarioRepository, WhatsAppServiceFactory whatsApp,
            Reloj reloj,
            @Value("${whatsapp.plantilla-codigo:codigo_verificacion_v2}") String plantillaCodigo,
            @Value("${telefono.codigos-por-dia-usuario:5}") int porUsuarioPorDia,
            @Value("${telefono.codigos-por-dia-numero:5}") int porNumeroPorDia,
            @Value("${telefono.segundos-entre-codigos:60}") int segundosEntrePedidos) {
        this.tokenRepository = tokenRepository;
        this.envios = envios;
        this.usuarioService = usuarioService;
        this.usuarioRepository = usuarioRepository;
        this.whatsApp = whatsApp;
        this.reloj = reloj;
        this.plantillaCodigo = plantillaCodigo;
        this.topes = new Topes(porUsuarioPorDia, porNumeroPorDia, segundosEntrePedidos);
    }

    /** Genera un código y lo manda por WhatsApp al teléfono del usuario (después del commit). */
    @Transactional
    public void enviarCodigo(Usuario usuario) {
        if (usuario.getTelefono() == null) {
            throw new ReservaException("Cargá tu teléfono antes de verificarlo.");
        }
        if (Boolean.TRUE.equals(usuario.getTelefonoVerificado())) {
            throw new EstadoInvalidoException("Tu teléfono ya está verificado.");
        }
        if (!whatsApp.getService().estaConfigurado()) {
            throw new EstadoInvalidoException("Los avisos por WhatsApp no están disponibles por ahora.");
        }
        // no se gasta un mensaje en un celular que ya verificó otra cuenta activa
        if (usuarioService.telefonoVerificadoEnOtraCuenta(usuario.getTelefono(), usuario.getId())) {
            throw new EstadoInvalidoException(UsuarioService.MENSAJE_CELULAR_EN_USO);
        }
        LocalDateTime ahora = reloj.ahora();
        validarTopes(usuario, ahora);
        envios.save(EnvioCodigoTelefono.builder().usuarioId(usuario.getId()).telefono(usuario.getTelefono())
                .enviadoAt(ahora).build());
        envios.borrarAnterioresA(ahora.minus(VENTANA_TOPES.multipliedBy(2))); // ya no cuentan para ningún tope

        tokenRepository.deleteByUsuarioAndType(usuario, TokenType.VERIFY_PHONE);
        VerificationToken token = tokenRepository.save(VerificationToken.builder()
                .usuario(usuario)
                .token(VerificationToken.generarCodigo())
                .type(TokenType.VERIFY_PHONE)
                .fechaExpiracion(reloj.instante().plus(VIGENCIA_CODIGO))
                .build());

        String telefono = usuario.getTelefono();
        String codigo = token.getToken();
        EnvioPostCommit.ejecutar("código de WhatsApp a " + telefono, () -> {
            if (!whatsApp.getService().enviarCodigo(telefono, plantillaCodigo, codigo)) {
                log.warn("No llegó a enviarse el código de verificación a {}", telefono);
            }
        });
    }

    /**
     * Igual que {@link #enviarCodigo} pero sin errores (al elegir WhatsApp en el perfil): si se
     * llegó a un tope no se manda, y el usuario puede pedirlo después y ver el motivo.
     */
    @Transactional
    public void enviarCodigoSiCorresponde(Usuario usuario) {
        if (usuario != null && usuario.getTelefono() != null && !Boolean.TRUE.equals(usuario.getTelefonoVerificado())
                && whatsApp.getService().estaConfigurado()) {
            try {
                enviarCodigo(usuario);
            } catch (LimiteExcedidoException e) {
                log.info("No se manda el código a {}: {}", usuario.getTelefono(), e.getMessage());
            }
        }
    }

    /** Antes de mandar: espera entre pedidos, tope por usuario y tope por número (24 h móviles). */
    private void validarTopes(Usuario usuario, LocalDateTime ahora) {
        envios.findFirstByUsuarioIdOrderByEnviadoAtDesc(usuario.getId()).ifPresent(ultimo -> {
            long faltan = Duration.between(ahora, ultimo.getEnviadoAt().plusSeconds(topes.segundosEntrePedidos()))
                    .toSeconds();
            if (faltan > 0) {
                throw new LimiteExcedidoException("Esperá " + faltan + " segundos para pedir otro código.");
            }
        });

        LocalDateTime desde = ahora.minus(VENTANA_TOPES);
        if (envios.countByUsuarioIdAndEnviadoAtAfter(usuario.getId(), desde) >= topes.porUsuarioPorDia()) {
            throw new LimiteExcedidoException("Llegaste al máximo de " + topes.porUsuarioPorDia()
                    + " códigos por día. Podés pedir otro "
                    + cuando(envios.primeroDelUsuarioDesde(usuario.getId(), desde), ahora) + ".");
        }
        if (envios.countByTelefonoAndEnviadoAtAfter(usuario.getTelefono(), desde) >= topes.porNumeroPorDia()) {
            log.warn("Tope de códigos alcanzado para el número {} (pedido por {})", usuario.getTelefono(),
                    usuario.getEmail());
            throw new LimiteExcedidoException("Ese número ya recibió el máximo de códigos por hoy. Podés pedir otro "
                    + cuando(envios.primeroDelNumeroDesde(usuario.getTelefono(), desde), ahora) + ".");
        }
    }

    /** "en 3 h" / "en 15 minutos": cuándo sale del tope el pedido más viejo de la ventana. */
    private static String cuando(Optional<LocalDateTime> primero, LocalDateTime ahora) {
        long segundos = primero.map(p -> Duration.between(ahora, p.plus(VENTANA_TOPES)).toSeconds()).orElse(0L);
        long minutos = Math.max((segundos + 59) / 60, 1); // redondeado para arriba
        return minutos > 60 ? "en " + (minutos + 59) / 60 + " h" : "en " + minutos + " minutos";
    }

    /** Los intentos fallidos se guardan aunque falle (5 intentos, como el código del mail). */
    @Transactional(noRollbackFor = CodigoInvalidoException.class)
    public void verificar(Usuario usuario, String codigo) {
        if (Boolean.TRUE.equals(usuario.getTelefonoVerificado())) {
            return;
        }
        VerificationToken token = VerificationService.validarCodigo(tokenRepository, usuario,
                TokenType.VERIFY_PHONE, codigo);
        // otra cuenta activa lo pudo haber verificado mientras tanto
        if (usuarioService.telefonoVerificadoEnOtraCuenta(usuario.getTelefono(), usuario.getId())) {
            throw new EstadoInvalidoException(UsuarioService.MENSAJE_CELULAR_EN_USO);
        }
        usuarioService.quitarVerificacionDeOtrasCuentas(usuario);
        usuario.setTelefonoVerificado(true);
        usuarioRepository.save(usuario);
        tokenRepository.delete(token);
        log.info("Teléfono {} verificado para {}", usuario.getTelefono(), usuario.getEmail());
    }
}
