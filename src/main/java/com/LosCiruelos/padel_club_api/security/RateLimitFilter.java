package com.LosCiruelos.padel_club_api.security;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import com.LosCiruelos.padel_club_api.common.Reloj;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Límite simple de pedidos por IP y por minuto para los endpoints de autenticación
 * (fuerza bruta de contraseñas y códigos, y abuso del envío de emails).
 *
 * Es en memoria: alcanza para una sola instancia. Con varias instancias hay que
 * moverlo a Redis o al proxy. Detrás de un proxy, configurar
 * server.forward-headers-strategy=framework para que getRemoteAddr() sea la IP real.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private static final long VENTANA_MS = 60_000;

    // pedidos permitidos por minuto y por IP
    private static final Map<String, Integer> LIMITES = Map.ofEntries(
            Map.entry("/auth/login", 10),
            Map.entry("/auth/google", 20),
            Map.entry("/auth/register", 5),
            Map.entry("/auth/verificar-token", 10),
            Map.entry("/auth/resend-token", 3),
            Map.entry("/auth/password-reset/request", 3),
            Map.entry("/auth/password-reset/confirm", 5),
            Map.entry("/auth/refresh", 30),
            // cada código por WhatsApp cuesta: pocos reenvíos
            Map.entry("/perfil/telefono/codigo", 3),
            Map.entry("/perfil/telefono/verificar", 10));

    private final Reloj reloj;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();
    private volatile long ultimaLimpieza;

    public RateLimitFilter(Reloj reloj) {
        this.reloj = reloj;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !LIMITES.containsKey(request.getRequestURI()) || "OPTIONS".equals(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long ahora = reloj.milisegundos();
        limpiarSiCorresponde(ahora);

        String ruta = request.getRequestURI();
        String clave = request.getRemoteAddr() + "|" + ruta;
        Ventana ventana = ventanas.compute(clave,
                (k, v) -> v == null || ahora - v.inicio >= VENTANA_MS ? new Ventana(ahora) : v);

        if (ventana.pedidos.incrementAndGet() > LIMITES.get(ruta)) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader("Retry-After", String.valueOf((VENTANA_MS - (ahora - ventana.inicio)) / 1000 + 1));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"error\":\"Demasiados intentos. Esperá un minuto y volvé a probar.\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private void limpiarSiCorresponde(long ahora) {
        if (ahora - ultimaLimpieza > VENTANA_MS) {
            ultimaLimpieza = ahora;
            ventanas.entrySet().removeIf(e -> ahora - e.getValue().inicio >= VENTANA_MS);
        }
    }

    private static final class Ventana {
        private final long inicio;
        private final AtomicInteger pedidos = new AtomicInteger();

        private Ventana(long inicio) {
            this.inicio = inicio;
        }
    }
}
