package com.LosCiruelos.padel_club_api.Services;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.PaymentResult;
import com.LosCiruelos.padel_club_api.DTOs.Responses.PagoResponse;
import com.LosCiruelos.padel_club_api.Entities.Configuracion;
import com.LosCiruelos.padel_club_api.Entities.Pago;
import com.LosCiruelos.padel_club_api.Entities.Reserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPago;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoPagoExterno;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.PaymentProvider;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.PagoRepository;
import com.LosCiruelos.padel_club_api.Services.Pagos.PaymentServiceFactory;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class PagoService {

    private final PaymentServiceFactory paymentServiceFactory;
    private final ReservaService reservaService;
    private final PagoRepository pagoRepository;
    private final ConfiguracionService configuracionService;

    @Transactional
    public PagoResponse iniciarPago(Long reservaId, Boolean pagarTotal) {
        Reserva reserva = reservaService.findByIdOrThrow(reservaId,
                new ReservaException("Reserva no encontrada: " + reservaId));

        if (reserva.getEstado() != EstadoReserva.PENDIENTE) {
            throw new ReservaException("Solo se puede pagar una reserva PENDIENTE.");
        }

        boolean tienePagoPendiente = pagoRepository.findByReservaId(reservaId)
                .stream().anyMatch(p -> p.getEstado() == EstadoPago.PENDIENTE);
        if (tienePagoPendiente) {
            throw new ReservaException("Ya existe un pago pendiente para esta reserva.");
        }

        Configuracion config = configuracionService.obtener();
        BigDecimal monto = pagarTotal
                ? reserva.getMontoTotal()
                : reserva.getMontoTotal()
                        .multiply(BigDecimal.valueOf(config.getPorcentajeSeña() / 100))
                        .setScale(2, RoundingMode.HALF_UP);

        String resultado = paymentServiceFactory.getService().iniciarPago(reserva, monto);

        String[] partes = resultado.split("\\|");
        String initPoint = partes[0];
        String preferenceId = partes[1];

        Pago pago = Pago.builder()
                .reserva(reserva)
                .monto(monto)
                .estado(EstadoPago.PENDIENTE)
                .provider(PaymentProvider.MERCADO_PAGO)
                .preferenceId(preferenceId)
                .createdAt(LocalDateTime.now())
                .build();

        pago = pagoRepository.save(pago);
        return PagoResponse.from(pago, initPoint);
    }

    @Transactional
    public void procesarWebhook(String paymentId, String topic) {
        if (!"payment".equals(topic))
            return;
        PaymentResult resultado = paymentServiceFactory.getService().consultarPago(paymentId);

        Reserva reserva = reservaService.findByIdOrThrow(Long.parseLong(resultado.getExternalReference()),
                new ReservaException("Reserva no encontrada"));

        Pago pago = pagoRepository.findByExternalPaymentId(paymentId)
                .orElseThrow(() -> new ReservaException("Pago no encontrado"));

        switch (resultado.getEstado()) {
            case EstadoPagoExterno.APROBADO -> {
                pago.setEstado(EstadoPago.APROBADO);
                pago.setExternalPaymentId(paymentId);
                pago.setPaidAt(LocalDateTime.now());
                pagoRepository.save(pago);

                reserva = reservaService.findById(reserva.getId());
                reserva.setEstado(this.calcularEstadoReserva(reserva));
                reservaService.save(reserva);
            }
            case EstadoPagoExterno.RECHAZADO -> {
                pago.setEstado(EstadoPago.RECHAZADO);
                pago.setExternalPaymentId(paymentId);
                reserva.setEstado(EstadoReserva.PENDIENTE);

                pagoRepository.save(pago);
                reservaService.save(reserva);
            }
            case EstadoPagoExterno.EN_PROCESO -> {
                pago.setEstado(EstadoPago.EN_PROCESO);
                pago.setExternalPaymentId(paymentId);

                pagoRepository.save(pago);
            }
            case EstadoPagoExterno.DEVUELTO -> {
                pago.setEstado(EstadoPago.DEVUELTO);
                pago.setExternalPaymentId(paymentId);

                pagoRepository.save(pago);

                log.warn("Pago devuelto — reserva #{} requiere atención del admin", reserva.getId());
            }
            default -> log.warn("Status desconocido: {}", resultado.getEstado());
        }
    }

    @Transactional
    public PagoResponse registrarPagoManual(Long reservaId, BigDecimal monto, PaymentProvider provider) {
        Reserva reserva = reservaService.findByIdOrThrow(reservaId,
                new ReservaException("Reserva no encontrada: " + reservaId));

        if (reserva.getEstado() != EstadoReserva.PENDIENTE &&
                reserva.getEstado() != EstadoReserva.SEÑADA) {
            throw new ReservaException("Solo se puede registrar un pago en reservas PENDIENTE o SEÑADA.");
        }

        BigDecimal montoPendiente = reserva.getMontoPendiente();
        if (monto.compareTo(montoPendiente) > 0) {
            throw new ReservaException(
                    "El monto ($%s) supera el pendiente ($%s).".formatted(monto, montoPendiente));
        }

        Pago pago = Pago.builder()
                .reserva(reserva)
                .monto(monto)
                .estado(EstadoPago.APROBADO)
                .provider(provider)
                .createdAt(LocalDateTime.now())
                .paidAt(LocalDateTime.now())
                .build();

        pago = pagoRepository.save(pago);

        reserva = reservaService.findById(reservaId);

        reserva.setEstado(calcularEstadoReserva(reserva));

        reservaService.save(reserva);

        log.info("Pago manual {} #{} — reserva #{} — monto: ${}",
                provider, pago.getId(), reserva.getId(), monto);

        return PagoResponse.from(pago);
    }

    private EstadoReserva calcularEstadoReserva(Reserva reserva) {
        BigDecimal pendiente = reserva.getMontoPendiente();
        if (pendiente.compareTo(BigDecimal.ZERO) == 0) {
            return EstadoReserva.PAGADA;
        }
        Configuracion config = configuracionService.obtener();
        BigDecimal montoSeña = reserva.getMontoTotal()
                .multiply(BigDecimal.valueOf(config.getPorcentajeSeña() / 100))
                .setScale(2, RoundingMode.HALF_UP);
        if (reserva.getMontoPagado().compareTo(montoSeña) == 0) {
            return EstadoReserva.SEÑADA;
        }
        return EstadoReserva.PARCIALMENTE_PAGADA;
    }
}