package com.LosCiruelos.padel_club_api.security;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.LosCiruelos.padel_club_api.common.Reloj;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity // activa los @PreAuthorize de los controllers
@RequiredArgsConstructor
public class SecurityConfig {

    private final JWTFilter jwtFilter;
    private final Reloj reloj;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/auth/logout").authenticated()

                        // Públicas
                        .requestMatchers("/auth/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/pagos/notificacion").permitAll() // webhook de Mercado Pago
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Un visitante sin cuenta puede ver canchas, horarios libres y precios (no reservar).
                        // La disponibilidad solo informa horarios: no expone datos de las reservas ni de clientes
                        .requestMatchers(HttpMethod.GET, "/canchas", "/canchas/*/disponibilidad",
                                "/productos", "/productos/disponibilidad")
                        .permitAll()

                        // Stock de productos: el personal puede verlo y ajustarlo; el resto es del ADMIN
                        .requestMatchers(HttpMethod.GET, "/admin/configuracion/productos")
                        .hasAnyRole("ADMIN", "EMPLEADO")
                        .requestMatchers(HttpMethod.PATCH, "/admin/configuracion/productos/*/stock")
                        .hasAnyRole("ADMIN", "EMPLEADO")

                        // Panel del personal: reservas y búsqueda de clientes
                        // Bonificar (no cobrarle al cliente): solo ADMIN
                        .requestMatchers(HttpMethod.POST, "/admin/reservas/*/bonificacion").hasRole("ADMIN")
                        // Armar y modificar partidos: solo ADMIN
                        .requestMatchers("/admin/reservas/partidos", "/admin/reservas/partidos/**").hasRole("ADMIN")
                        .requestMatchers("/admin/reservas", "/admin/reservas/**").hasAnyRole("ADMIN", "EMPLEADO")
                        .requestMatchers(HttpMethod.GET, "/admin/usuarios/clientes").hasAnyRole("ADMIN", "EMPLEADO")
                        // Reservas del cliente (las del personal están en /admin/reservas)
                        .requestMatchers("/reservas", "/reservas/**").hasRole("CLIENTE")
                        // Pagos online del cliente (el webhook es público, más arriba)
                        .requestMatchers("/pagos/**").hasRole("CLIENTE")

                        // Solo ADMIN
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        .anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex
                        // sin token o con token vencido: 401, así el front sabe que tiene que refrescar
                        .authenticationEntryPoint((request, response, e) -> escribirError(response,
                                HttpStatus.UNAUTHORIZED, "No autenticado o token vencido"))
                        .accessDeniedHandler((request, response, e) -> escribirError(response,
                                HttpStatus.FORBIDDEN, "No tenés permisos para esta operación")));

        http.addFilterBefore(new RateLimitFilter(reloj), UsernamePasswordAuthenticationFilter.class);
        http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOriginPatterns(allowedOrigins);
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cfg.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    private static void escribirError(HttpServletResponse response, HttpStatus status, String mensaje)
            throws java.io.IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"error\":\"" + mensaje + "\"}");
    }
}
