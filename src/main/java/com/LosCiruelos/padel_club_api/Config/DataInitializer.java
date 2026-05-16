package com.LosCiruelos.padel_club_api.Config;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.Entities.Enum.AuthProvider;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Repository.UsuarioRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${admin.email}")
    private String adminEmail;

    @Value("${admin.password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) {
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
}
