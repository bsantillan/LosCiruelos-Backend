package com.LosCiruelos.padel_club_api.notificaciones.aviso;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.CanalNotificacion;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Fechas;

/**
 * Evento de aviso con los datos ya resueltos: se arma dentro de la transacción y se envía
 * después del commit, en otro hilo, sin volver a tocar las entidades.
 *
 * @param monto  lo que corresponde al aviso: lo pagado (confirmada), el total (saldo), lo que
 *               hay que devolver (reembolso manual) o el saldo pendiente (recordatorio)
 * @param detalle texto libre del aviso (quién canceló y el reembolso, el motivo, hasta cuándo pagar…)
 * @param link    link para el cliente (pagar la seña); lo completa el envío con la URL del front. Por
 *               WhatsApp va en el botón de la plantilla ({@link #parametroBoton()}); por mail, al final
 *               del texto ({@link #textoMail()})
 */
public record AvisoReserva(
        TipoAviso tipo,
        Long reservaId,
        Destinatario cliente,
        Integer cancha,
        LocalDate fecha,
        LocalTime horaInicio,
        LocalTime horaFin,
        BigDecimal montoTotal,
        BigDecimal monto,
        String detalle,
        String link) {

    /** Datos de contacto de quien recibe el aviso. */
    public record Destinatario(String nombre, String email, String telefono, CanalNotificacion canal,
            boolean telefonoVerificado) {

        public static Destinatario de(Usuario usuario) {
            String nombre = usuario.getNombre() != null ? usuario.getNombre() : "";
            return new Destinatario(nombre, usuario.getEmail(), usuario.getTelefono(),
                    usuario.getCanalNotificaciones(), Boolean.TRUE.equals(usuario.getTelefonoVerificado()));
        }
    }

    public static AvisoReserva de(TipoAviso tipo, Reserva reserva, BigDecimal monto, String detalle) {
        return para(tipo, reserva, reserva.getCliente(), monto, detalle);
    }

    /** Aviso de la reserva para otro usuario que el cliente (por ejemplo, un jugador del partido). */
    public static AvisoReserva para(TipoAviso tipo, Reserva reserva, Usuario destinatario, BigDecimal monto,
            String detalle) {
        return new AvisoReserva(tipo, reserva.getId(), Destinatario.de(destinatario),
                reserva.getCancha().getNumero(), reserva.getFechaReserva(), reserva.getHoraInicio(),
                reserva.getHoraFin(), reserva.getMontoTotal(), monto, detalle, null);
    }

    /** El mismo aviso con el link para el cliente. */
    public AvisoReserva conLink(String link) {
        return new AvisoReserva(tipo, reservaId, cliente, cancha, fecha, horaInicio, horaFin, montoTotal, monto, detalle,
                link);
    }

    /** Parámetros de la plantilla, en el orden de {{1}}, {{2}}, … de {@link TipoAviso}. */
    public List<String> parametros() {
        String nombreCompleto = cliente.nombre();
        String canchaTexto = String.valueOf(cancha);
        String fechaTexto = Fechas.textoFecha(fecha);
        String horario = Fechas.textoHora(horaInicio) + " a " + Fechas.textoHora(horaFin);
        return switch (tipo) {
            case RESERVA_CONFIRMADA -> List.of(nombreCompleto, canchaTexto, fechaTexto, horario, Dinero.texto(monto),
                    Dinero.texto(montoTotal));
            case SALDO_COBRADO -> List.of(nombreCompleto, canchaTexto, fechaTexto, horario, Dinero.texto(montoTotal));
            case RESERVA_CANCELADA, RECORDATORIO, CANCELADA_POR_CLUB -> List.of(nombreCompleto, canchaTexto,
                    fechaTexto, horario, detalle != null ? detalle : "");
            case REEMBOLSO_MANUAL -> List.of(Dinero.texto(monto), String.valueOf(reservaId), nombreCompleto,
                    detalle != null ? detalle : "");
            case REEMBOLSO_REALIZADO -> List.of(Dinero.texto(monto), String.valueOf(reservaId), nombreCompleto);
            case SENIA_PENDIENTE -> List.of(nombreCompleto, canchaTexto, fechaTexto, horario, Dinero.texto(monto),
                    detalle != null ? detalle : "");
        };
    }

    public String texto() {
        return tipo.texto(parametros());
    }

    /**
     * Lo que se agrega a la URL del botón de la plantilla de WhatsApp, o null si no tiene botón con
     * variable. El de la seña pendiente se carga en Meta como {@code <front>/reservas/{{1}}}: el
     * sufijo completa la pantalla de pago de la reserva.
     */
    public String parametroBoton() {
        return tipo == TipoAviso.SENIA_PENDIENTE ? reservaId + "/pago" : null;
    }

    /** El texto del mail: el de la plantilla y, si hay, el link (en WhatsApp es el botón). */
    public String textoMail() {
        return link != null ? texto() + "\n\nPagá la seña desde este link: " + link : texto();
    }

}
