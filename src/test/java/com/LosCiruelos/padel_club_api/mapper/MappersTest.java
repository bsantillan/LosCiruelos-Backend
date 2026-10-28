package com.LosCiruelos.padel_club_api.mapper;

import static com.LosCiruelos.padel_club_api.TestData.AHORA;
import static com.LosCiruelos.padel_club_api.TestData.MANIANA;
import static com.LosCiruelos.padel_club_api.TestData.usuario;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.notificaciones.entity.AvisosAdmin;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.SuscripcionesResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.NotificacionesResponse;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.AvisosAdminResponse;
import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.dto.response.CanchaResponse;
import com.LosCiruelos.padel_club_api.club.dto.response.ProductoResponse;
import com.LosCiruelos.padel_club_api.club.entity.Producto;
import com.LosCiruelos.padel_club_api.club.entity.enums.TipoProducto;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.reservas.dto.response.TurnoFijoResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijo;
import com.LosCiruelos.padel_club_api.reservas.entity.TurnoFijoJugador;
import com.LosCiruelos.padel_club_api.reservas.entity.enums.EstadoReserva;

/** Mappers que no dependen de la configuración (ReservaMapper se prueba con los services). */
class MappersTest {

    @Test
    void unPagoConSuJugadorYElEstadoDeLaReserva() {
        Reserva partido = TestData.partido(10L, usuario(3L, Role.ADMIN), usuario(1L, Role.CLIENTE), "15000");
        Pago pago = TestData.pago(5L, partido, EstadoPago.APROBADO, "3750");
        pago.setProvider(PaymentProvider.BONIFICACION);
        pago.setMotivo("socio");
        pago.setJugador(partido.getJugadores().get(2));

        PagoResponse r = new PagoMapper().aResponse(pago);

        assertThat(r.getId()).isEqualTo(5L);
        assertThat(r.getReservaId()).isEqualTo(10L);
        assertThat(r.getMonto()).isEqualByComparingTo("3750");
        assertThat(r.getProvider()).isEqualTo(PaymentProvider.BONIFICACION);
        assertThat(r.getMotivo()).isEqualTo("socio");
        assertThat(r.getJugadorId()).isEqualTo(203L);
        assertThat(r.getEstadoReserva()).isEqualTo(EstadoReserva.CONFIRMADA);
    }

    @Test
    void unTurnoFijoConSusJugadoresYProximosPartidos() {
        Usuario cliente = usuario(1L, Role.CLIENTE);
        TurnoFijo tf = TurnoFijo.builder().id(8L).cancha(TestData.cancha()).diaSemana(DayOfWeek.WEDNESDAY)
                .horaInicio(LocalTime.of(20, 0)).horaFin(LocalTime.of(21, 30)).desde(MANIANA).cantidadPelotas(1)
                .cantidadPaletas(0).activo(true).createdAt(AHORA).build();
        tf.getJugadores().add(TurnoFijoJugador.builder().posicion(1).usuario(cliente).build());
        tf.getJugadores().add(TurnoFijoJugador.builder().posicion(2).nombre("Invitado").telefono("11 5555-0000").build());
        Reserva proximo = TestData.reserva(70L, cliente, EstadoReserva.CONFIRMADA, "15000");
        Reserva liberado = TestData.reserva(71L, cliente, EstadoReserva.CANCELADA, "15000");

        TurnoFijoResponse r = new TurnoFijoMapper().aResponse(tf, List.of(proximo, liberado));

        assertThat(r.getDiaSemana()).isEqualTo(DayOfWeek.WEDNESDAY);
        assertThat(r.getJugadores()).extracting(TurnoFijoResponse.Jugador::nombre)
                .containsExactly("Nombre1 Apellido1", "Invitado");
        assertThat(r.getJugadores().get(1).invitado()).isTrue();
        assertThat(r.getJugadores().get(1).telefono()).isEqualTo("11 5555-0000");
        assertThat(r.getProximosPartidos()).extracting(TurnoFijoResponse.Partido::montoPendiente)
                .containsExactly(new BigDecimal("15000"), BigDecimal.ZERO);
    }

    @Test
    void lasPreferenciasDeAvisoDelUsuario() {
        Usuario usuario = TestData.usuario(1L, Role.CLIENTE);
        usuario.setTelefono("5491122334455");
        usuario.setTelefonoVerificado(null);
        usuario.setCanalNotificaciones(CanalNotificacion.WHATSAPP);
        usuario.setRecibeRecordatorios(null);
        usuario.setRecibePromociones(true);
        NotificacionesMapper mapper = new NotificacionesMapper();

        NotificacionesResponse canal = mapper.aResponse(usuario, CanalNotificacion.EMAIL, true);
        SuscripcionesResponse suscripciones = mapper.aSuscripciones(usuario, false);
        AvisosAdminResponse admin = mapper.aAvisosAdmin(AvisosAdmin.porDefecto(1L));

        assertThat(canal).isEqualTo(new NotificacionesResponse(CanalNotificacion.WHATSAPP, CanalNotificacion.EMAIL,
                "5491122334455", false, true));
        // las cancelaciones se avisan siempre; un valor null cuenta como no
        assertThat(suscripciones).isEqualTo(new SuscripcionesResponse(true, false, true, false));
        assertThat(admin).isEqualTo(new AvisosAdminResponse(true, true, true, true, true));
    }

    @Test
    void unaCancha() {
        CanchaResponse r = new CanchaMapper().aResponse(TestData.cancha());

        assertThat(r.getId()).isEqualTo(TestData.cancha().getId());
        assertThat(r.getNumero()).isEqualTo(TestData.cancha().getNumero());
        assertThat(r.getDescripcion()).isEqualTo(TestData.cancha().getDescripcion());
    }

    @Test
    void unProductoConSuCodigoYSuStock() {
        Producto paletas = Producto.builder().id(2L).codigo(Producto.CODIGO_PALETAS).nombre("Paletas")
                .descripcion("Alquiler").tipo(TipoProducto.ALQUILER).precio(new BigDecimal("1500")).stock(20)
                .activo(true).build();

        ProductoResponse r = new ProductoMapper().aResponse(paletas);

        assertThat(r.getId()).isEqualTo(2L);
        assertThat(r.getCodigo()).isEqualTo(Producto.CODIGO_PALETAS);
        assertThat(r.getNombre()).isEqualTo("Paletas");
        assertThat(r.getDescripcion()).isEqualTo("Alquiler");
        assertThat(r.getTipo()).isEqualTo(TipoProducto.ALQUILER);
        assertThat(r.getPrecio()).isEqualByComparingTo("1500");
        assertThat(r.getStock()).isEqualTo(20);
        assertThat(r.getActivo()).isTrue();
    }
}
