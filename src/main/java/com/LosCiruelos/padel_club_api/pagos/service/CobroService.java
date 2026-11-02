package com.LosCiruelos.padel_club_api.pagos.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.PagoResponse;
import com.LosCiruelos.padel_club_api.pagos.entity.Pago;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoPago;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.reservas.dto.request.CrearReservaAdminRequest;
import com.LosCiruelos.padel_club_api.reservas.dto.response.ReservaResponse;
import com.LosCiruelos.padel_club_api.reservas.entity.Reserva;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaConsultaService;
import com.LosCiruelos.padel_club_api.reservas.service.ReservaService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cobros del personal en el club (efectivo, tarjeta o QR en el mostrador) y bonificaciones del
 * ADMIN. Toda operación bloquea primero la reserva.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CobroService {

    private final ReservaService reservaService;
    private final ReservaConsultaService reservaConsultaService;
    private final PagoRepository pagoRepository;
    private final UsuarioService usuarioService;
    private final EstadosDePago estados;
    private final Reloj reloj;
    private final PagoMapper pagoMapper;

    // ── Pagos en el club ─────────────────────────────────────────────────

    /** En un partido se cobra el monto general, igual que en una reserva normal (sin indicar el jugador). */
    @Transactional
    public PagoResponse registrarPagoManual(Long reservaId, BigDecimal monto, PaymentProvider provider,
            String email) {
        return registrar(reservaId, monto, provider, null, email);
    }

    /**
     * Pago con Cuenta DNI: no hay API, así que el personal lo registra con el número de comprobante
     * (el que muestra la app al cobrar), que no se puede cargar dos veces.
     */
    @Transactional
    public PagoResponse registrarCuentaDni(Long reservaId, BigDecimal monto, String comprobante, String email) {
        String limpio = comprobante.trim();
        if (pagoRepository.existsByComprobante(limpio)) {
            throw new EstadoInvalidoException("Ese comprobante de Cuenta DNI ya está registrado.");
        }
        return registrar(reservaId, monto, PaymentProvider.CUENTA_DNI, limpio, email);
    }

    private PagoResponse registrar(Long reservaId, BigDecimal monto, PaymentProvider provider, String comprobante,
            String email) {
        if (monto == null || monto.signum() <= 0) {
            throw new ReservaException("El monto debe ser mayor a cero.");
        }

        Usuario personal = usuarioService.autenticado(email);
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, personal);
        estados.validarQueAdmitePagos(reserva);
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException(
                    "Hay un pago online en proceso para esta reserva; esperá a que se resuelva.");
        }
        BigDecimal montoPendiente = reserva.getMontoPendiente();
        if (monto.compareTo(montoPendiente) > 0) {
            throw new ReservaException("El monto ($%s) supera el pendiente ($%s).".formatted(monto, montoPendiente));
        }

        // un pago online iniciado por un monto que ya no corresponde se descarta
        reserva.expirarPagosPendientes().forEach(pagoRepository::save);

        LocalDateTime ahora = reloj.ahora();
        Pago pago = Pago.builder()
                .monto(monto)
                .estado(EstadoPago.APROBADO)
                .provider(provider)
                .createdAt(ahora)
                .paidAt(ahora)
                .registradoPorId(personal.getId())
                .comprobante(comprobante)
                .build();
        reserva.agregarPago(pago);
        pago = pagoRepository.save(pago);

        estados.recalcularReserva(reserva);
        log.info("Pago manual {} #{} — reserva #{} — monto ${} — registrado por {}",
                provider, pago.getId(), reserva.getId(), monto, email);
        return pagoMapper.aResponse(pago);
    }

    // ── Bonificación del ADMIN ───────────────────────────────────────────

    /**
     * El ADMIN no le cobra al cliente todo o una parte del turno (socio, torneo, cortesía). Se
     * registra como un pago BONIFICACION: cubre el monto (la reserva queda confirmada o paga) pero
     * no es plata, así que no cuenta como cobrado ni se devuelve si después se cancela.
     *
     * @param monto null: todo lo que falta pagar
     */
    @Transactional
    public PagoResponse bonificar(Long reservaId, BigDecimal monto, String motivo, String email) {
        Usuario admin = usuarioService.autenticado(email);
        validarQueEsAdmin(admin);
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, admin);
        estados.validarQueAdmitePagos(reserva);
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException(
                    "Hay un pago online en proceso para esta reserva; esperá a que se resuelva.");
        }
        BigDecimal pendiente = reserva.getMontoPendiente();
        if (pendiente.signum() <= 0) {
            throw new EstadoInvalidoException("La reserva ya está paga.");
        }
        BigDecimal aBonificar = monto != null ? monto : pendiente;
        if (aBonificar.signum() <= 0) {
            throw new ReservaException("El monto debe ser mayor a cero.");
        }
        if (aBonificar.compareTo(pendiente) > 0) {
            throw new ReservaException("El monto ($%s) supera el pendiente ($%s).".formatted(aBonificar, pendiente));
        }

        // un pago online iniciado por un monto que ya no corresponde se descarta
        reserva.expirarPagosPendientes().forEach(pagoRepository::save);

        LocalDateTime ahora = reloj.ahora();
        Pago bonificacion = Pago.builder()
                .monto(aBonificar)
                .estado(EstadoPago.APROBADO)
                .provider(PaymentProvider.BONIFICACION)
                .createdAt(ahora)
                .paidAt(ahora)
                .registradoPorId(admin.getId())
                .motivo(motivo != null && !motivo.isBlank() ? motivo.trim() : null)
                .build();
        reserva.agregarPago(bonificacion);
        bonificacion = pagoRepository.save(bonificacion);

        // la confirmó el ADMIN mismo: no se manda el aviso de "nueva reserva / saldo cobrado"
        estados.recalcularReserva(reserva, false);
        log.info("Bonificación #{} — reserva #{} — ${} — {} — por {}", bonificacion.getId(), reserva.getId(),
                aBonificar, bonificacion.getMotivo() != null ? bonificacion.getMotivo() : "sin motivo", email);
        return pagoMapper.aResponse(bonificacion);
    }

    /** POST /admin/reservas con bonificación (solo ADMIN): se crea y se bonifica en la misma transacción. */
    @Transactional
    public ReservaResponse crearReservaBonificada(CrearReservaAdminRequest req, String email) {
        validarQueEsAdmin(usuarioService.autenticado(email));
        ReservaResponse creada = reservaService.crearParaCliente(req, email);
        bonificar(creada.getId(), req.getBonificacion().getMonto(), req.getBonificacion().getMotivo(), email);
        return reservaConsultaService.obtener(creada.getId(), email);
    }

    private static void validarQueEsAdmin(Usuario usuario) {
        Roles.exigirAdmin(usuario, "Solo el ADMIN puede bonificar una reserva.");
    }
}
