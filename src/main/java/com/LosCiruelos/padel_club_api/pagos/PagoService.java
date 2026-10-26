package com.LosCiruelos.padel_club_api.pagos;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.Services.ConfiguracionService;
import com.LosCiruelos.padel_club_api.Services.ProductoService;
import com.LosCiruelos.padel_club_api.Services.UsuarioService;
import com.LosCiruelos.padel_club_api.pagos.dto.PaymentResult;
import com.LosCiruelos.padel_club_api.reservas.ReservaService;
import com.LosCiruelos.padel_club_api.reservas.dto.CrearReservaRequest;
import com.LosCiruelos.padel_club_api.pagos.dto.PagoResponse;
import com.LosCiruelos.padel_club_api.reservas.dto.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.reservas.Reserva;
import com.LosCiruelos.padel_club_api.reservas.ReservaJugador;
import com.LosCiruelos.padel_club_api.Entities.Usuario;
import com.LosCiruelos.padel_club_api.reservas.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.Role;
import com.LosCiruelos.padel_club_api.Exceptions.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.Exceptions.PagoNoEncontradoException;
import com.LosCiruelos.padel_club_api.Exceptions.PagoProveedorException;
import com.LosCiruelos.padel_club_api.Exceptions.ReembolsoFallidoException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Services.Avisos.AvisoReserva;
import com.LosCiruelos.padel_club_api.Services.Avisos.TipoAviso;
import com.LosCiruelos.padel_club_api.pagos.mercadopago.PaymentService;
import com.LosCiruelos.padel_club_api.pagos.mercadopago.PaymentServiceFactory;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Pagos de reservas: online con Mercado Pago (Brick / Wallet) y manuales en el club.
 *
 * Igual que en ReservaService, toda operación bloquea primero la reserva y trabaja
 * con {@code reserva.getPagos()}, de modo que el cobro, el webhook, la expiración y
 * los pagos manuales nunca se pisan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    private final PaymentServiceFactory paymentServiceFactory;
    private final ReservaService reservaService;
    private final PagoRepository pagoRepository;
    private final ConfiguracionService configuracionService;
    private final UsuarioService usuarioService;
    private final ProductoService productoService;
    private final Reloj reloj;
    private final ApplicationEventPublisher eventos;

    // ── 1. Iniciar un pago online ────────────────────────────────────────

    /**
     * Crea un Pago PENDIENTE y su preferencia en Mercado Pago.
     * pagarTotal = true cobra todo lo que falta; false cobra lo que falta para completar la seña.
     * Si ya hay un pago iniciado por el mismo monto lo devuelve (el usuario recargó la página).
     */
    @Transactional
    public PagoResponse iniciarPago(Long reservaId, Boolean pagarTotal, String email) {
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, usuario(email));
        if (reserva.esPartido()) {
            throw new EstadoInvalidoException("Los partidos se pagan en el club.");
        }
        validarQueAdmitePagos(reserva);
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException("Hay un pago en proceso para esta reserva; esperá a que se resuelva.");
        }

        BigDecimal monto = calcularMontoAPagar(reserva, Boolean.TRUE.equals(pagarTotal));

        Optional<Pago> iniciado = reserva.getPagos().stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE)
                .findFirst();
        if (iniciado.isPresent() && iniciado.get().getMonto().compareTo(monto) == 0
                && iniciado.get().getPaymentToken() != null) {
            return PagoResponse.from(iniciado.get());
        }
        // un pago iniciado por otro monto se descarta
        iniciado.ifPresent(p -> cambiarEstado(p, EstadoPago.EXPIRADO));

        Pago pago = Pago.builder()
                .monto(monto)
                .estado(EstadoPago.PENDIENTE)
                .provider(PaymentProvider.MERCADO_PAGO)
                .createdAt(reloj.ahora())
                .build();
        reserva.agregarPago(pago);
        pago = pagoRepository.save(pago);

        // si MP falla se lanza una excepción y la transacción no guarda nada
        pago.setPaymentToken(paymentServiceFactory.getService().crearPreferencia(pago));
        pago = pagoRepository.save(pago);

        log.info("Pago #{} iniciado para la reserva #{} por ${}", pago.getId(), reserva.getId(), monto);
        return PagoResponse.from(pago);
    }

    BigDecimal calcularMontoAPagar(Reserva reserva, boolean pagarTotal) {
        Configuracion config = configuracionService.obtener();
        BigDecimal pagado = reserva.getMontoCubierto();
        BigDecimal pendiente = reserva.getMontoPendiente();

        if (pendiente.signum() <= 0) {
            throw new EstadoInvalidoException("La reserva ya está paga.");
        }
        if (pagarTotal) {
            return pendiente;
        }
        BigDecimal faltaSenia = configuracionService.calcularSenia(config, reserva.getMontoTotal()).subtract(pagado);
        if (faltaSenia.signum() <= 0) {
            throw new EstadoInvalidoException("La seña ya está paga; para pagar el saldo usá pagarTotal=true.");
        }
        return faltaSenia.min(pendiente);
    }

    // ── 2. Cobrar con el Brick ───────────────────────────────────────────

    @Transactional
    public PagoResponse procesarPago(Long reservaId, Map<String, Object> formData, String email) {
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, usuario(email));

        // con el lock tomado, un doble click espera y acá ya no encuentra el pago PENDIENTE
        Pago pago = reserva.getPagos().stream()
                .filter(p -> p.getEstado() == EstadoPago.PENDIENTE && p.getProvider() == PaymentProvider.MERCADO_PAGO)
                .findFirst()
                .orElseThrow(() -> new EstadoInvalidoException(
                        "No hay un pago iniciado para esta reserva. Iniciá el pago nuevamente."));
        validarQueAdmitePagos(reserva);

        PaymentResult resultado = paymentServiceFactory.getService().cobrar(pago, formData);
        aplicarResultado(reserva, pago, resultado);

        PagoResponse response = PagoResponse.from(pago);
        response.setDetalle(resultado.getDetalle());
        return response;
    }

    // ── 3. Webhook de Mercado Pago ───────────────────────────────────────

    /**
     * Nunca se confía en el contenido de la notificación: se consulta el pago a MP
     * y se aplica el resultado. Notificaciones repetidas o fuera de orden no cambian
     * un estado final.
     */
    @Transactional
    public void procesarNotificacion(String paymentId) {
        PaymentResult resultado = paymentServiceFactory.getService().consultarPago(paymentId);

        Optional<Long> reservaId = pagoRepository.findReservaIdByExternalPaymentId(paymentId)
                .or(() -> resultado.getPagoId() != null
                        ? pagoRepository.findReservaIdByPagoId(resultado.getPagoId())
                        : Optional.empty());
        if (reservaId.isEmpty()) {
            log.warn("Notificación del pago {} de MP sin Pago asociado (external_reference={})",
                    paymentId, resultado.getExternalReference());
            return;
        }

        Reserva reserva = reservaService.bloquear(reservaId.get());
        Optional<Pago> pago = reserva.getPagos().stream()
                .filter(p -> paymentId.equals(p.getExternalPaymentId()))
                .findFirst()
                .or(() -> reserva.getPagos().stream()
                        .filter(p -> p.getId().equals(resultado.getPagoId()))
                        .findFirst());
        if (pago.isEmpty()) {
            log.warn("Notificación del pago {} de MP: no se encontró el Pago en la reserva #{}", paymentId,
                    reserva.getId());
            return;
        }

        aplicarResultado(reserva, pago.get(), resultado);
    }

    // ── 4. Pagos en el club (efectivo / tarjeta o QR en el mostrador) ────

    /** @param jugadorId en un partido, qué jugador paga (obligatorio); en una reserva normal, null */
    @Transactional
    public PagoResponse registrarPagoManual(Long reservaId, BigDecimal monto, PaymentProvider provider,
            Long jugadorId, String email) {
        if (monto == null || monto.signum() <= 0) {
            throw new ReservaException("El monto debe ser mayor a cero.");
        }

        Usuario personal = usuario(email);
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, personal);
        validarQueAdmitePagos(reserva);
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException(
                    "Hay un pago online en proceso para esta reserva; esperá a que se resuelva.");
        }
        ReservaJugador jugador = resolverJugador(reserva, jugadorId);

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
                .jugador(jugador)
                .build();
        reserva.agregarPago(pago);
        pago = pagoRepository.save(pago);

        recalcularEstadoReserva(reserva);
        log.info("Pago manual {} #{} — reserva #{} — monto ${} — registrado por {}",
                provider, pago.getId(), reserva.getId(), monto, email);
        return PagoResponse.from(pago);
    }

    // ── 4 bis. Bonificación del ADMIN ────────────────────────────────────

    /**
     * El ADMIN no le cobra al cliente todo o una parte del turno (socio, torneo, cortesía). Se
     * registra como un pago BONIFICACION: cubre el monto (la reserva queda confirmada o paga) pero
     * no es plata, así que no cuenta como cobrado ni se devuelve si después se cancela.
     *
     * @param monto     null: todo lo que falta pagar (en un partido, lo que le falta de su parte al jugador)
     * @param jugadorId en un partido, a qué jugador se le bonifica (obligatorio); en una reserva normal, null
     */
    @Transactional
    public PagoResponse bonificar(Long reservaId, BigDecimal monto, String motivo, Long jugadorId, String email) {
        Usuario admin = usuario(email);
        validarQueEsAdmin(admin);
        Reserva reserva = reservaService.bloquearConAcceso(reservaId, admin);
        validarQueAdmitePagos(reserva);
        if (reserva.tienePagoEnProceso()) {
            throw new EstadoInvalidoException(
                    "Hay un pago online en proceso para esta reserva; esperá a que se resuelva.");
        }
        ReservaJugador jugador = resolverJugador(reserva, jugadorId);
        BigDecimal pendiente = reserva.getMontoPendiente();
        if (pendiente.signum() <= 0) {
            throw new EstadoInvalidoException("La reserva ya está paga.");
        }
        BigDecimal aBonificar = monto != null ? monto : jugador != null ? debeDe(reserva, jugador) : pendiente;
        if (monto == null && jugador != null && aBonificar.signum() <= 0) {
            throw new EstadoInvalidoException(jugador.nombreParaMostrar() + " ya cubrió su parte.");
        }
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
                .jugador(jugador)
                .build();
        reserva.agregarPago(bonificacion);
        bonificacion = pagoRepository.save(bonificacion);

        // la confirmó el ADMIN mismo: no se manda el aviso de "nueva reserva / saldo cobrado"
        recalcularEstadoReserva(reserva, false);
        log.info("Bonificación #{} — reserva #{} — ${} — {} — por {}", bonificacion.getId(), reserva.getId(),
                aBonificar, bonificacion.getMotivo() != null ? bonificacion.getMotivo() : "sin motivo", email);
        return PagoResponse.from(bonificacion);
    }

    /** POST /reservas con bonificación (solo ADMIN): se crea y se bonifica en la misma transacción. */
    @Transactional
    public ReservaResponse crearReservaBonificada(CrearReservaRequest req, String email) {
        validarQueEsAdmin(usuario(email));
        ReservaResponse creada = reservaService.crearReserva(req, email);
        bonificar(creada.getId(), req.getBonificacion().getMonto(), req.getBonificacion().getMotivo(), null, email);
        return reservaService.obtener(creada.getId(), email);
    }

    /**
     * En un partido hay que indicar qué jugador paga, y tiene que ser uno de sus jugadores; en una
     * reserva normal no se indica.
     */
    private static ReservaJugador resolverJugador(Reserva reserva, Long jugadorId) {
        if (!reserva.esPartido()) {
            if (jugadorId != null) {
                throw new ReservaException("La reserva no es un partido: no se indica el jugador.");
            }
            return null;
        }
        if (jugadorId == null) {
            throw new ReservaException("Indicá qué jugador paga (jugadorId).");
        }
        return reserva.getJugadores().stream()
                .filter(j -> j.getId().equals(jugadorId))
                .findFirst()
                .orElseThrow(() -> new ReservaException("El jugador " + jugadorId + " no es de este partido."));
    }

    /** Lo que le falta al jugador de su parte (sin pasarse de lo que falta en la reserva). */
    private static BigDecimal debeDe(Reserva reserva, ReservaJugador jugador) {
        return ReservaResponse.from(reserva).getJugadores().stream()
                .filter(j -> j.getId().equals(jugador.getId()))
                .map(j -> j.getDebe())
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private static void validarQueEsAdmin(Usuario usuario) {
        Roles.exigirAdmin(usuario, "Solo el ADMIN puede bonificar una reserva.");
    }

    // ── 5. Reembolso por cancelación ─────────────────────────────────────

    /**
     * Reparte {@code monto} entre los pagos aprobados de la reserva (ya bloqueada):
     * primero los cobrados online por Mercado Pago, que se devuelven por API y quedan DEVUELTO;
     * después los cobrados en el club, que quedan A_REEMBOLSAR para que el personal los devuelva.
     * Cada pago guarda en montoReembolsado cuánto se devolvió de él.
     *
     * Si MP no acepta un reembolso (ej. el pago recién cobrado todavía se está acreditando),
     * el pago queda REEMBOLSO_PENDIENTE y lo reintenta {@link #reintentarReembolso}: la
     * cancelación sigue adelante igual.
     */
    public void reembolsar(Reserva reserva, BigDecimal monto) {
        BigDecimal restante = monto;
        List<Pago> aprobados = reserva.getPagos().stream()
                .filter(p -> p.getEstado() == EstadoPago.APROBADO && !p.esBonificacion()) // no es plata
                .sorted(Comparator.comparing((Pago p) -> p.getExternalPaymentId() == null)
                        .thenComparing(Pago::getCreatedAt, Comparator.reverseOrder()))
                .toList();

        for (Pago pago : aprobados) {
            if (restante.signum() <= 0) {
                break;
            }
            BigDecimal aDevolver = restante.min(pago.getMontoRetenido());
            if (aDevolver.signum() <= 0) {
                continue;
            }
            pago.setMontoReembolsado(aDevolver);
            if (pago.getExternalPaymentId() != null) {
                try {
                    paymentServiceFactory.getService().reembolsar(pago, aDevolver);
                    cambiarEstado(pago, EstadoPago.DEVUELTO);
                } catch (ReembolsoFallidoException e) {
                    pago.setReembolsoSolicitadoAt(reloj.ahora());
                    if (e.isDefinitivo()) {
                        // el aviso de la cancelación ya dice lo que hay que devolver a mano
                        pasarAReembolsoManual(pago, reserva, null);
                    } else {
                        cambiarEstado(pago, EstadoPago.REEMBOLSO_PENDIENTE);
                        log.warn("Pago #{}: MP no aceptó el reembolso de ${}; queda REEMBOLSO_PENDIENTE "
                                + "para reintentarlo (reserva #{})", pago.getId(), aDevolver, reserva.getId());
                    }
                }
            } else {
                cambiarEstado(pago, EstadoPago.A_REEMBOLSAR);
                log.warn("Pago #{} cobrado en el club: hay que devolver ${} a mano (reserva #{})", pago.getId(),
                        aDevolver, reserva.getId());
            }
            restante = restante.subtract(aDevolver);
        }
    }

    /** Ids de los pagos con un reembolso de MP por reintentar. */
    public List<Long> idsReembolsosPendientes() {
        return pagoRepository.findIdsByEstado(EstadoPago.REEMBOLSO_PENDIENTE);
    }

    /**
     * Reintenta el reembolso de un pago REEMBOLSO_PENDIENTE, con la reserva bloqueada y la misma
     * clave de idempotencia. Antes consulta el pago en MP, por si el reembolso ya se hizo (un
     * intento anterior que respondió con error, o una devolución desde el panel de MP).
     * <ul>
     * <li>se pudo reembolsar, o MP ya lo tiene devuelto → DEVUELTO;</li>
     * <li>falla que no se arregla reintentando, o pasaron {@link #PLAZO_REINTENTO_REEMBOLSO}
     * → A_REEMBOLSAR (a devolver a mano) y log.error;</li>
     * <li>si no, sigue REEMBOLSO_PENDIENTE para el próximo intento.</li>
     * </ul>
     *
     * @return el estado en que quedó el pago
     */
    @Transactional
    public EstadoPago reintentarReembolso(Long pagoId) {
        Long reservaId = pagoRepository.findReservaIdByPagoId(pagoId).orElse(null);
        if (reservaId == null) {
            return null;
        }
        Reserva reserva = reservaService.bloquear(reservaId);
        Pago pago = reserva.getPagos().stream()
                .filter(p -> p.getId().equals(pagoId))
                .findFirst()
                .orElse(null);
        if (pago == null || pago.getEstado() != EstadoPago.REEMBOLSO_PENDIENTE) {
            return pago != null ? pago.getEstado() : null; // otro proceso ya lo resolvió (ej. el webhook)
        }

        BigDecimal aDevolver = pago.getMontoReembolsado() != null ? pago.getMontoReembolsado() : pago.getMonto();
        PaymentService proveedor = paymentServiceFactory.getService();
        try {
            BigDecimal yaDevuelto = proveedor.consultarPago(pago.getExternalPaymentId()).getMontoDevuelto();
            if (yaDevuelto != null && yaDevuelto.signum() > 0) {
                if (yaDevuelto.compareTo(aDevolver) == 0) {
                    cambiarEstado(pago, EstadoPago.DEVUELTO);
                    log.info("Pago #{}: MP ya tenía devueltos ${}; queda DEVUELTO", pago.getId(), yaDevuelto);
                    avisarReembolsoRealizado(pago, reserva, aDevolver);
                } else {
                    // alguien devolvió otro monto desde el panel: que lo revise una persona
                    log.error("Pago #{}: MP tiene devueltos ${} pero había que devolver ${}", pago.getId(),
                            yaDevuelto, aDevolver);
                    pasarAReembolsoManual(pago, reserva, "Mercado Pago tiene devueltos " + Dinero.texto(yaDevuelto)
                            + " y había que devolver " + Dinero.texto(aDevolver) + ". Revisalo en el panel de MP.");
                }
                return pago.getEstado();
            }

            proveedor.reembolsar(pago, aDevolver);
            cambiarEstado(pago, EstadoPago.DEVUELTO);
            avisarReembolsoRealizado(pago, reserva, aDevolver);
            log.info("Pago #{}: reembolso de ${} hecho en el reintento (reserva #{})", pago.getId(), aDevolver,
                    reserva.getId());
        } catch (ReembolsoFallidoException | PagoNoEncontradoException e) {
            boolean definitivo = e instanceof PagoNoEncontradoException
                    || ((ReembolsoFallidoException) e).isDefinitivo();
            if (definitivo || plazoDeReintentoVencido(pago)) {
                pasarAReembolsoManual(pago, reserva, "Mercado Pago no aceptó el reembolso (pago MP "
                        + pago.getExternalPaymentId() + ").");
            }
        } catch (PagoProveedorException e) {
            // MP no respondió a la consulta: se reintenta en el próximo ciclo
            if (plazoDeReintentoVencido(pago)) {
                pasarAReembolsoManual(pago, reserva, "Mercado Pago no respondió durante 24 h (pago MP "
                        + pago.getExternalPaymentId() + ").");
            }
        }
        return pago.getEstado();
    }

    /** Pasado este plazo sin poder reembolsar por API, el pago se devuelve a mano. */
    static final Duration PLAZO_REINTENTO_REEMBOLSO = Duration.ofHours(24);

    private boolean plazoDeReintentoVencido(Pago pago) {
        LocalDateTime desde = pago.getReembolsoSolicitadoAt() != null ? pago.getReembolsoSolicitadoAt()
                : pago.getCreatedAt();
        return !reloj.ahora().isBefore(desde.plus(PLAZO_REINTENTO_REEMBOLSO));
    }

    /** @param motivoAviso si no es null, se avisa a los ADMIN con ese motivo */
    private void pasarAReembolsoManual(Pago pago, Reserva reserva, String motivoAviso) {
        cambiarEstado(pago, EstadoPago.A_REEMBOLSAR);
        log.error("Pago #{} (MP {}): no se pudo reembolsar por Mercado Pago. Queda A_REEMBOLSAR: hay que "
                + "devolver ${} a mano (reserva #{})", pago.getId(), pago.getExternalPaymentId(),
                pago.getMontoReembolsado(), reserva.getId());
        if (motivoAviso != null) {
            avisarReembolsoManual(pago, reserva, motivoAviso);
        }
    }

    /** Un reembolso que había quedado pendiente al cancelar finalmente se hizo. */
    private void avisarReembolsoRealizado(Pago pago, Reserva reserva, BigDecimal monto) {
        eventos.publishEvent(AvisoReserva.de(TipoAviso.REEMBOLSO_REALIZADO, reserva, monto, null));
    }

    private void avisarReembolsoManual(Pago pago, Reserva reserva, String motivo) {
        BigDecimal monto = pago.getMontoReembolsado() != null ? pago.getMontoReembolsado() : pago.getMonto();
        eventos.publishEvent(AvisoReserva.de(TipoAviso.REEMBOLSO_MANUAL, reserva, monto, motivo));
    }

    // ── Reglas de estado ─────────────────────────────────────────────────

    /** Aplica el resultado del proveedor al pago y recalcula la reserva. Es idempotente. */
    void aplicarResultado(Reserva reserva, Pago pago, PaymentResult resultado) {
        EstadoPago actual = pago.getEstado();
        String paymentId = resultado.getPaymentId();
        boolean esOtroPagoDeMp = paymentId != null && pago.getExternalPaymentId() != null
                && !paymentId.equals(pago.getExternalPaymentId());

        switch (resultado.getEstado()) {
            case APROBADO -> {
                if (actual == EstadoPago.APROBADO || actual == EstadoPago.DEVUELTO
                        || actual == EstadoPago.A_REEMBOLSAR || actual == EstadoPago.REEMBOLSO_PENDIENTE) {
                    return; // notificación repetida
                }
                pago.setExternalPaymentId(paymentId);
                boolean montoCorrecto = resultado.getMonto() == null
                        || resultado.getMonto().compareTo(pago.getMonto()) == 0;
                if (!montoCorrecto) {
                    log.error("Pago #{}: MP cobró ${} pero se esperaba ${}. Queda A_REEMBOLSAR.",
                            pago.getId(), resultado.getMonto(), pago.getMonto());
                    pago.setMontoReembolsado(pago.getMonto());
                    cambiarEstado(pago, EstadoPago.A_REEMBOLSAR);
                    avisarReembolsoManual(pago, reserva, "Mercado Pago cobró " + Dinero.texto(resultado.getMonto())
                            + " y se esperaba " + Dinero.texto(pago.getMonto()) + ".");
                } else if (reserva.estaInactiva()) {
                    log.warn("Pago #{} aprobado para la reserva #{} en estado {}. Queda A_REEMBOLSAR: "
                            + "hay que devolver el dinero.", pago.getId(), reserva.getId(), reserva.getEstado());
                    pago.setMontoReembolsado(pago.getMonto());
                    cambiarEstado(pago, EstadoPago.A_REEMBOLSAR);
                    avisarReembolsoManual(pago, reserva, "Se cobró un pago online de una reserva "
                            + reserva.getEstado() + ".");
                } else {
                    pago.setPaidAt(reloj.ahora());
                    cambiarEstado(pago, EstadoPago.APROBADO);
                }
            }
            case RECHAZADO -> {
                // un rechazo de otro intento no pisa un pago que está en curso con otro id
                if (!esOtroPagoDeMp && (actual == EstadoPago.PENDIENTE || actual == EstadoPago.EN_PROCESO)) {
                    pago.setExternalPaymentId(paymentId);
                    cambiarEstado(pago, EstadoPago.RECHAZADO);
                }
            }
            case EN_PROCESO -> {
                if (!esOtroPagoDeMp && actual == EstadoPago.PENDIENTE) {
                    pago.setExternalPaymentId(paymentId);
                    cambiarEstado(pago, EstadoPago.EN_PROCESO);
                }
            }
            case DEVUELTO -> {
                if (!esOtroPagoDeMp && (actual == EstadoPago.APROBADO || actual == EstadoPago.EN_PROCESO
                        || actual == EstadoPago.A_REEMBOLSAR || actual == EstadoPago.REEMBOLSO_PENDIENTE)) {
                    // devolución hecha fuera del sistema (panel de MP, contracargo): es por el total
                    if (pago.getMontoReembolsado() == null) {
                        pago.setMontoReembolsado(pago.getMonto());
                    }
                    cambiarEstado(pago, EstadoPago.DEVUELTO);
                    if (actual == EstadoPago.REEMBOLSO_PENDIENTE) {
                        avisarReembolsoRealizado(pago, reserva, pago.getMontoReembolsado());
                    }
                    log.warn("Pago #{} devuelto — reserva #{}", pago.getId(), reserva.getId());
                }
            }
        }
        recalcularEstadoReserva(reserva);
    }

    /**
     * El estado de la reserva siempre se deriva de sus pagos aprobados:
     * nada pagado → PENDIENTE · menos que la seña → PARCIALMENTE_PAGADA ·
     * seña o más → SEÑADA · total → PAGADA (o COMPLETADA si el turno ya terminó).
     * Una reserva confirmada que queda sin dinero (reembolso) pasa a CANCELADA. Un partido sin nada
     * pagado queda CONFIRMADA (no vence ni se cancela por eso).
     * CANCELADA, EXPIRADA y COMPLETADA no cambian.
     */
    public void recalcularEstadoReserva(Reserva reserva) {
        recalcularEstadoReserva(reserva, true);
    }

    /** @param avisar false cuando el cambio lo hizo el ADMIN mismo (bonificación) */
    public void recalcularEstadoReserva(Reserva reserva, boolean avisar) {
        EstadoReserva anterior = reserva.getEstado();
        if (reserva.estaInactiva() || anterior == EstadoReserva.COMPLETADA) {
            return;
        }

        BigDecimal pagado = reserva.getMontoCubierto(); // pagado + bonificado
        BigDecimal senia = configuracionService.calcularSenia(configuracionService.obtener(), reserva.getMontoTotal());

        EstadoReserva nuevo;
        if (pagado.signum() == 0 && reserva.esPartido()) {
            nuevo = EstadoReserva.CONFIRMADA;
        } else if (pagado.signum() == 0) {
            nuevo = anterior == EstadoReserva.PENDIENTE ? EstadoReserva.PENDIENTE : EstadoReserva.CANCELADA;
        } else if (pagado.compareTo(reserva.getMontoTotal()) >= 0) {
            // totalmente paga: si el turno ya terminó se completa en el acto (ej. el saldo se
            // cobró en el club después de jugar); si no, la completa el job al terminar
            nuevo = reserva.turnoTermino(reloj.ahora()) ? EstadoReserva.COMPLETADA : EstadoReserva.PAGADA;
        } else if (pagado.compareTo(senia) >= 0) {
            nuevo = EstadoReserva.SEÑADA;
        } else {
            nuevo = EstadoReserva.PARCIALMENTE_PAGADA;
        }

        if (nuevo != anterior) {
            reserva.setEstado(nuevo);
            if (nuevo == EstadoReserva.CANCELADA) {
                reserva.expirarPagosPendientes().forEach(pagoRepository::save);
                productoService.liberar(reserva);
                log.warn("Reserva #{} cancelada: se devolvió todo lo pagado", reserva.getId());
            }
            reservaService.save(reserva);
            log.info("Reserva #{}: {} → {}", reserva.getId(), anterior, nuevo);
            // los partidos los arma y los sigue el ADMIN: no se le avisa de cada pago
            if (avisar && !reserva.esPartido()) {
                avisarCambioDeEstado(reserva, anterior, nuevo, pagado);
            }
        }
    }

    /**
     * Avisos a los ADMIN: reserva confirmada (primera vez que llega a la seña o al total),
     * saldo cobrado (una señada queda paga) o cancelada por devolución de todo lo pagado.
     */
    private void avisarCambioDeEstado(Reserva reserva, EstadoReserva anterior, EstadoReserva nuevo,
            BigDecimal pagado) {
        boolean eraConfirmada = anterior == EstadoReserva.SEÑADA || anterior == EstadoReserva.PAGADA;
        boolean quedaConfirmada = nuevo == EstadoReserva.SEÑADA || nuevo == EstadoReserva.PAGADA
                || nuevo == EstadoReserva.COMPLETADA;
        boolean quedaPaga = nuevo == EstadoReserva.PAGADA || nuevo == EstadoReserva.COMPLETADA;

        if (quedaConfirmada && !eraConfirmada) {
            eventos.publishEvent(AvisoReserva.de(TipoAviso.RESERVA_CONFIRMADA, reserva, pagado, null));
        } else if (quedaPaga && anterior == EstadoReserva.SEÑADA) {
            eventos.publishEvent(AvisoReserva.de(TipoAviso.SALDO_COBRADO, reserva, pagado, null));
        } else if (nuevo == EstadoReserva.CANCELADA) {
            eventos.publishEvent(AvisoReserva.de(TipoAviso.RESERVA_CANCELADA, reserva, BigDecimal.ZERO,
                    "Se devolvió todo lo pagado desde Mercado Pago."));
        }
    }

    private void validarQueAdmitePagos(Reserva reserva) {
        LocalDateTime ahora = reloj.ahora();
        if (reserva.estaVencida(ahora)) {
            throw new EstadoInvalidoException("El tiempo para pagar esta reserva expiró.");
        }
        if (!reserva.admitePagos(ahora)) {
            throw new EstadoInvalidoException("La reserva no admite pagos en estado " + reserva.getEstado() + ".");
        }
    }

    private void cambiarEstado(Pago pago, EstadoPago estado) {
        LocalDateTime ahora = reloj.ahora();
        // un cobro de MP que queda devuelto o por devolver sin pasar por APROBADO también entró a caja
        if (pago.getPaidAt() == null && estado != EstadoPago.EN_PROCESO && Pago.ESTADOS_CON_DINERO.contains(estado)) {
            pago.setPaidAt(ahora);
        }
        if (estado == EstadoPago.DEVUELTO || estado == EstadoPago.A_REEMBOLSAR) {
            pago.setReembolsadoAt(ahora);
        }
        pago.setEstado(estado);
        pagoRepository.save(pago);
    }

    private Usuario usuario(String email) {
        return usuarioService.autenticado(email);
    }
}
