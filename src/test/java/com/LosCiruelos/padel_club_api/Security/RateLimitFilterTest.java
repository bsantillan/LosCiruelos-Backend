package com.LosCiruelos.padel_club_api.Security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.LosCiruelos.padel_club_api.common.Reloj;

class RateLimitFilterTest {

    private int status(RateLimitFilter filtro, String ruta) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", ruta);
        request.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filtro.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    @Test
    void limitaLosIntentosDeLoginPorMinuto() throws Exception {
        RateLimitFilter filtro = new RateLimitFilter(new Reloj(Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC)));

        for (int i = 0; i < 10; i++) {
            assertThat(status(filtro, "/auth/login")).isEqualTo(200);
        }
        assertThat(status(filtro, "/auth/login")).isEqualTo(429);
        // otras rutas no se ven afectadas
        assertThat(status(filtro, "/reservas")).isEqualTo(200);
    }

    @Test
    void laVentanaSeReiniciaAlMinuto() throws Exception {
        AtomicReference<Instant> ahora = new AtomicReference<>(Instant.parse("2026-10-13T10:00:00Z"));
        Clock reloj = new Clock() {
            @Override
            public ZoneId getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return ahora.get();
            }
        };
        RateLimitFilter filtro = new RateLimitFilter(new Reloj(reloj));

        for (int i = 0; i < 3; i++) {
            assertThat(status(filtro, "/auth/resend-token")).isEqualTo(200);
        }
        assertThat(status(filtro, "/auth/resend-token")).isEqualTo(429);

        ahora.set(ahora.get().plusSeconds(61));
        assertThat(status(filtro, "/auth/resend-token")).isEqualTo(200);
    }
}
