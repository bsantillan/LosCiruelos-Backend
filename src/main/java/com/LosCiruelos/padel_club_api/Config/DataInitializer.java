package com.LosCiruelos.padel_club_api.Config;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.entity.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Categoria;
import com.LosCiruelos.padel_club_api.Entities.Enum.Posicion;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.club.service.ConfiguracionService;
import com.LosCiruelos.padel_club_api.club.service.CanchaService;
import com.LosCiruelos.padel_club_api.Services.ClienteProfileService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

        private final UsuarioService usuarioService;
        private final CanchaService canchaService;
        private final ClienteProfileService clienteProfileService;
        private final ConfiguracionService configuracionService;

        @Value("${admin.email}")
        private String adminEmail;

        // Usuario de prueba: solo en desarrollo (APP_SEED_USUARIO_PRUEBA=true)
        @Value("${app.seed.usuario-prueba:false}")
        private boolean crearUsuarioPrueba;

        private static final String USER_PRUEBA_EMAIL = "user.prueba@losciruelos.com";
        private static final String USER_PRUEBA_PASSWORD = "UserPassword123!";

        @Value("${admin.password}")
        private String adminPassword;

        @Override
        public void run(ApplicationArguments args) {
                crearAdminSiNoExiste();
                if (crearUsuarioPrueba) {
                        crearUsuarioPruebaSiNoExiste();
                }
                crearConfiguracionSiNoExiste();
                crearCanchasSiNoExisten();
        }

        private void crearAdminSiNoExiste() {
                if (usuarioService.findByEmail(adminEmail) == null) {
                        usuarioService.crearUsuario(
                                        adminEmail,
                                        "Admin",
                                        "Los Ciruelos",
                                        null,
                                        adminPassword,
                                        Role.ADMIN,
                                        AuthProvider.LOCAL,
                                        true,
                                        true,
                                        true);
                        log.info("Admin creado con email: {}", adminEmail);
                } else {
                        log.info("Admin ya existe, no se crea uno nuevo.");
                }
        }

        private void crearCanchasSiNoExisten() {
                if (canchaService.crearCanchasSiNoHay(4, "Cancha de césped sintético con iluminación LED.")) {
                        log.info("4 canchas creadas.");
                } else {
                        log.info("Canchas ya existen, no se crean nuevas.");
                }
        }

        private void crearConfiguracionSiNoExiste() {
                List<DiaApertura> dias = List.of(
                                DiaApertura.builder().dia("Lunes").horario_inicio(LocalTime.of(8, 0))
                                                .horario_fin(LocalTime.of(23, 0)).build(),
                                DiaApertura.builder().dia("Martes").horario_inicio(LocalTime.of(8, 0))
                                                .horario_fin(LocalTime.of(23, 0)).build(),
                                DiaApertura.builder().dia("Miércoles").horario_inicio(LocalTime.of(8, 0))
                                                .horario_fin(LocalTime.of(23, 0)).build(),
                                DiaApertura.builder().dia("Jueves").horario_inicio(LocalTime.of(8, 0))
                                                .horario_fin(LocalTime.of(23, 0)).build(),
                                DiaApertura.builder().dia("Viernes").horario_inicio(LocalTime.of(8, 0))
                                                .horario_fin(LocalTime.of(23, 0)).build(),
                                DiaApertura.builder().dia("Sábado").horario_inicio(LocalTime.of(9, 0))
                                                .horario_fin(LocalTime.of(22, 0)).build(),
                                DiaApertura.builder().dia("Domingo").horario_inicio(LocalTime.of(9, 0))
                                                .horario_fin(LocalTime.of(20, 0)).build());

                Configuracion configuracion = Configuracion.builder()
                                .id(1L)
                                .montoReserva(BigDecimal.ZERO)
                                .porcentajeSenia(new BigDecimal("50"))
                                .duracionMinimaTurno(90)
                                .duracionMaximaTurno(180)
                                .montoXMediaHora(new BigDecimal("5000"))
                                .minutosReembolsoTotal(1440)
                                .minutosReembolsoSenia(720)
                                .dias_apertura(dias)
                                .build();

                if (configuracionService.crearSiNoExiste(configuracion)) {
                        log.info("Configuración inicial creada.");
                } else {
                        log.info("Configuración ya existe, no se crea una nueva.");
                }
        }

        private void crearUsuarioPruebaSiNoExiste() {
                if (usuarioService.findByEmail(USER_PRUEBA_EMAIL) == null) {
                        Usuario usuarioPrueba = usuarioService.crearUsuario(
                                        USER_PRUEBA_EMAIL,
                                        "User",
                                        "Prueba",
                                        "5491122334455", // ya normalizado (formato de WhatsApp)
                                        USER_PRUEBA_PASSWORD,
                                        Role.CLIENTE,
                                        AuthProvider.LOCAL,
                                        true,
                                        true,
                                        true);
                        // los clientes necesitan su perfil de jugador y el celular verificado para poder reservar
                        usuarioPrueba.setTelefonoVerificado(true);
                        usuarioService.save(usuarioPrueba);
                        clienteProfileService.crearClienteProfile(usuarioPrueba, Categoria.Cuarta, Posicion.Drive);
                        log.info("User creado con email: {}", USER_PRUEBA_EMAIL);
                } else {
                        log.info("User ya existe, no se crea uno nuevo.");
                        // creado antes de exigir el celular verificado: se marca para que pueda seguir reservando
                        Usuario existente = usuarioService.findByEmail(USER_PRUEBA_EMAIL);
                        if (existente.getTelefono() != null && !Boolean.TRUE.equals(existente.getTelefonoVerificado())) {
                                existente.setTelefonoVerificado(true);
                                usuarioService.save(existente);
                        }
                }
        }
}
