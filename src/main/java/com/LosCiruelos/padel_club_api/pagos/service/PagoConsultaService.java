package com.LosCiruelos.padel_club_api.pagos.service;

import com.LosCiruelos.padel_club_api.mapper.PagoMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.MedioDeCobroResponse;
import com.LosCiruelos.padel_club_api.pagos.payment.ProveedorCobroQr;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.CuentaDniMapper;
import com.LosCiruelos.padel_club_api.pagos.dto.response.CobroCuentaDniResponse;
import com.LosCiruelos.padel_club_api.pagos.payment.PaymentProvider;
import com.LosCiruelos.padel_club_api.pagos.repository.PagoRepository;
import com.LosCiruelos.padel_club_api.ventas.service.VentaConsultaService;

import lombok.RequiredArgsConstructor;

/** Consultas de pagos para el ADMIN: todo lo cobrado con Cuenta DNI, para controlarlo contra la cuenta. */
@Service
@RequiredArgsConstructor
public class PagoConsultaService {

    private final PagoRepository pagoRepository;
    private final VentaConsultaService ventaConsultaService;
    private final CuentaDniMapper cuentaDniMapper;
    private final ProveedorCobroQr proveedorQr;
    private final PagoMapper pagoMapper;
    private final com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService usuarioService;
    private final com.LosCiruelos.padel_club_api.club.service.JornadaService jornadaService;

    /**
     * Lo que hay que devolverle a mano a cada cliente (A_REEMBOLSAR), de la más vieja a la más nueva.
     * El EMPLEADO ve las de la jornada en curso.
     */
    @Transactional(readOnly = true)
    public List<com.LosCiruelos.padel_club_api.pagos.dto.response.DevolucionListadoResponse> devolucionesAEntregar(
            String email) {
        boolean esAdmin = com.LosCiruelos.padel_club_api.common.Roles.esAdmin(usuarioService.autenticado(email));
        com.LosCiruelos.padel_club_api.common.Jornada jornada = esAdmin ? null : jornadaService.actual();
        return pagoRepository.findDevolucionesEnEstado(
                com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion.A_REEMBOLSAR).stream()
                .filter(d -> jornada == null || jornada.incluye(d.getCreatedAt()))
                .map(pagoMapper::aDevolucionDelListado)
                .toList();
    }

    /** Lo que devolvió el EMPLEADO y el ADMIN todavía no revisó (solo ADMIN), de lo más viejo a lo más nuevo. */
    @Transactional(readOnly = true)
    public List<com.LosCiruelos.padel_club_api.pagos.dto.response.DevolucionListadoResponse> devolucionesSinRevisar(
            String email) {
        com.LosCiruelos.padel_club_api.common.Roles.exigirAdmin(usuarioService.autenticado(email),
                "Solo el ADMIN revisa las devoluciones.");
        return pagoRepository.findDevolucionesSinRevisar().stream()
                .map(pagoMapper::aDevolucionDelListado)
                .toList();
    }

    /** Lo cobrado con Cuenta DNI (reservas y ventas sueltas), para verificarlo. */
    @Transactional(readOnly = true)
    public List<CobroCuentaDniResponse> cuentaDni(LocalDate desde, LocalDate hasta, Boolean verificado) {
        return verificables(desde, hasta, verificado, List.of(MedioDePago.CUENTA_DNI));
    }

    /**
     * Pagos de reservas y ventas sueltas cobrados a mano que el ADMIN controla contra la cuenta (Cuenta
     * DNI y transferencias de Mercado Pago) entre desde y hasta (inclusive), del más viejo al más nuevo.
     *
     * @param medios CUENTA_DNI y/o TRANSFERENCIA_MP (null o vacío: los dos)
     * @param verificado null: todos
     */
    @Transactional(readOnly = true)
    public List<CobroCuentaDniResponse> verificables(LocalDate desde, LocalDate hasta, Boolean verificado,
            List<MedioDePago> medios) {
        if (desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        List<MedioDePago> pedidos = medios == null || medios.isEmpty()
                ? List.of(MedioDePago.CUENTA_DNI, MedioDePago.TRANSFERENCIA_MP) : medios;
        if (pedidos.stream().anyMatch(m -> !m.seVerifica())) {
            throw new ReservaException("Solo se verifican los cobros con Cuenta DNI o por transferencia.");
        }
        List<PaymentProvider> providers = pedidos.stream()
                .map(m -> m == MedioDePago.CUENTA_DNI ? PaymentProvider.CUENTA_DNI : PaymentProvider.TRANSFERENCIA_MP)
                .toList();
        LocalDateTime inicio = desde.atStartOfDay();
        LocalDateTime fin = hasta.plusDays(1).atStartOfDay();
        return Stream.concat(
                pagoRepository.verificables(providers, inicio, fin, verificado).stream().map(cuentaDniMapper::aResponse),
                ventaConsultaService.verificables(inicio, fin, verificado, pedidos).stream()
                        .map(cuentaDniMapper::aResponse))
                .sorted(Comparator.comparing(CobroCuentaDniResponse::fecha))
                .toList();
    }

    /**
     * Los medios con que el personal cobra una reserva, en el orden en que se muestran, con el endpoint
     * de cada uno. El QR de la caja, solo si está configurado.
     */
    public List<MedioDeCobroResponse> mediosDeCobro() {
        boolean qr = proveedorQr.estaConfigurado();
        return java.util.Arrays.stream(MedioDePago.values()).map(m -> pagoMapper.aMedioDeCobro(m, qr)).toList();
    }
}
