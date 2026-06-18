package com.LosCiruelos.padel_club_api.Config;

import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.Services.CanchaService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

        private final UsuarioService usuarioService;
        private final CanchaService canchaService;
        private final ConfiguracionRepository configuracionRepository;

        @Value("${admin.email}")
        private String adminEmail;

        private String userPruebaEmail = "user.prueba@losciruelos.com";
        private String userPruebaPassword = "UserPassword123!";

        @Value("${admin.password}")
        private String adminPassword;

        @Override
        public void run(ApplicationArguments args) {
                crearAdminSiNoExiste();
                crearUsuarioPruebaSiNoExiste();
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
                if (canchaService.count() == 0) {
                        canchaService.crearCancha(1, "Cancha de césped sintético con iluminación LED.");
                        canchaService.crearCancha(2, "Cancha de césped sintético con iluminación LED.");
                        canchaService.crearCancha(3, "Cancha de césped sintético con iluminación LED.");
                        canchaService.crearCancha(4, "Cancha de césped sintético con iluminación LED.");

                        log.info("4 canchas creadas.");
                } else {
                        log.info("Canchas ya existen, no se crean nuevas.");
                }
        }

        private void crearConfiguracionSiNoExiste() {
                if (configuracionRepository.findById(1L).isEmpty()) {

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
                                        .montoReserva(0f)
                                        .porcentajeSeña(50f)
                                        .duracionMinimaTurno(90)
                                        .duracionMaximaTurno(180)
                                        .montoXMediHora(5000f)
                                        .dias_apertura(dias)
                                        .build();

                        configuracionRepository.save(configuracion);
                        log.info("Configuración inicial creada.");
                } else {
                        log.info("Configuración ya existe, no se crea una nueva.");
                }
        }

        private void crearUsuarioPruebaSiNoExiste() {
                if (usuarioService.findByEmail(userPruebaEmail) == null) {
                        usuarioService.crearUsuario(
                                        userPruebaEmail,
                                        "User",
                                        "Prueba",
                                        null,
                                        userPruebaPassword,
                                        Role.CLIENTE,
                                        AuthProvider.LOCAL,
                                        true,
                                        true,
                                        true);
                        log.info("User creado con email: {}", userPruebaEmail);
                } else {
                        log.info("User ya existe, no se crea uno nuevo.");
                }
        }
}
