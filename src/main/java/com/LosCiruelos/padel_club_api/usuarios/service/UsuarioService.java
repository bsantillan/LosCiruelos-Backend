package com.LosCiruelos.padel_club_api.usuarios.service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Exceptions.CredencialesInvalidasException;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.AuthProvider;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.Exceptions.UsuarioNotFoundException;
import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;
import com.LosCiruelos.padel_club_api.usuarios.repository.UsuarioRepository;
import com.LosCiruelos.padel_club_api.common.Telefonos;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    public static final String MENSAJE_CELULAR_EN_USO =
            "Ese celular ya está verificado en otra cuenta. Si es tuyo, iniciá sesión con esa cuenta.";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public Usuario findByEmail(String email) {
        return usuarioRepository.findByEmail(email).orElse(null);
    }

    /** {@code telefono} null: no se toca el teléfono ni su verificación. */
    public Usuario updateUsuario(Usuario user, String nombre, String apellido, String telefono) {
        user.setNombre(capitalizar(nombre));
        user.setApellido(capitalizar(apellido));
        if (telefono != null) {
            cambiarTelefono(user, telefono);
        }
        return usuarioRepository.save(user);
    }

    /**
     * Guarda el teléfono normalizado (formato de WhatsApp). Si cambia, deja de estar verificado:
     * hay que volver a confirmarlo con un código antes de mandarle mensajes.
     */
    public void cambiarTelefono(Usuario user, String telefono) {
        String normalizado = Telefonos.normalizar(telefono);
        // se compara contra el guardado también normalizado: el mismo número escrito de otra forma
        // (o guardado en un formato viejo) no es un cambio y no pierde la verificación
        if (!Objects.equals(normalizado, Telefonos.normalizarSinError(user.getTelefono()))) {
            validarCelularDisponible(normalizado, user.getId());
            user.setTelefonoVerificado(false);
        }
        user.setTelefono(normalizado);
    }

    /**
     * Un celular verificado identifica a una sola cuenta activa: si otra cuenta activa ya lo
     * verificó, no se puede cargar en esta. Si esa cuenta está desactivada (ej. el cliente se
     * hizo una cuenta nueva), sí se permite.
     */
    public void validarCelularDisponible(String telefonoNormalizado, Long idActual) {
        if (telefonoVerificadoEnOtraCuenta(telefonoNormalizado, idActual)) {
            throw new ValidationException(Map.of("telefono", MENSAJE_CELULAR_EN_USO));
        }
    }

    public boolean telefonoVerificadoEnOtraCuenta(String telefonoNormalizado, Long idActual) {
        return telefonoNormalizado != null && usuarioRepository.existeCelularVerificadoEnOtraCuentaActiva(
                telefonoNormalizado, idActual != null ? idActual : -1L);
    }

    /** Al verificarse en esta cuenta, las demás (desactivadas) que lo tenían verificado lo pierden. */
    public void quitarVerificacionDeOtrasCuentas(Usuario usuario) {
        usuarioRepository.quitarVerificacionDeOtrasCuentas(usuario.getTelefono(), usuario.getId());
    }

    /** ADMIN activos: reciben los avisos de reservas. */
    public List<Usuario> adminsActivos() {
        return usuarioRepository.findByRolAndEnabledTrue(Role.ADMIN);
    }

    public void desactivarUsuario(Usuario usuario) {
        usuario.setEnabled(false);
        usuarioRepository.save(usuario);
    }

    public Usuario crearUsuario(String email, String nombre, String apellido,
            String telefono, String password, Role rol, AuthProvider provider,
            Boolean termsAccepted, Boolean emailVerificado, Boolean enabled) {
        String telefonoNormalizado = Telefonos.normalizar(telefono);
        validarCelularDisponible(telefonoNormalizado, null);
        return usuarioRepository.save(Usuario.builder()
                .email(email)
                .nombre(capitalizar(nombre))
                .apellido(capitalizar(apellido))
                .telefono(telefonoNormalizado)
                .passwordHash(password != null ? passwordEncoder.encode(password) : null)
                .rol(rol)
                .provider(provider)
                .termsAccepted(termsAccepted)
                .termsAcceptedAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS))
                .emailVerificado(emailVerificado)
                .enabled(enabled)
                .build());
    }

    /** El usuario de la sesión (por el email del token). */
    public Usuario autenticado(String email) {
        return usuarioRepository.findByEmail(email)
                .orElseThrow(() -> new CredencialesInvalidasException("Usuario no encontrado"));
    }

    public Usuario findByIdOrThrow(Long id) {
        return usuarioRepository.findById(id)
                .orElseThrow(() -> new UsuarioNotFoundException("Usuario no encontrado: " + id));
    }

    /** Lo usan los otros dominios (notificaciones, carga inicial); dentro de usuarios se usa el repository. */
    public Usuario save(Usuario usuario) {
        return usuarioRepository.save(usuario);
    }

    private String capitalizar(String texto) {
        if (texto == null || texto.isEmpty())
            return texto;
        return texto.substring(0, 1).toUpperCase() + texto.substring(1).toLowerCase();
    }
}
