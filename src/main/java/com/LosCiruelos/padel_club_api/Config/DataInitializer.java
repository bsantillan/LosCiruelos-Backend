package com.LosCiruelos.padel_club_api.Config;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.DiaApertura;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ConfiguracionRepository configuracionRepository;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
        crearAdminSiNoExiste();
        crearConfiguracionSiNoExiste();
    }

    private void crearAdminSiNoExiste() {
        if (usuarioRepository.findByEmail(adminEmail).isEmpty()) {
            Usuario admin = Usuario.builder()
                    .email(adminEmail)
                    .nombre("Admin")
                    .apellido("Los Ciruelos")
                    .passwordHash(passwordEncoder.encode(adminPassword))
                    .rol(Role.ADMIN)
                    .provider(AuthProvider.LOCAL)
                    .termsAccepted(true)
                    .termsAcceptedAt(LocalDateTime.now())
                    .emailVerificado(true)
                    .enabled(true)
                    .build();

            usuarioRepository.save(admin);
            log.info("Admin creado con email: {}", adminEmail);
        } else {
            log.info("Admin ya existe, no se crea uno nuevo.");
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
}
