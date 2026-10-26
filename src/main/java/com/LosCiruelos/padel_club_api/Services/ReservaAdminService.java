package com.LosCiruelos.padel_club_api.Services;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.DTOs.Admin.FiltroReservasAdmin;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ListadoReservasResponse;
import com.LosCiruelos.padel_club_api.DTOs.Responses.ReservaResponse;
import com.LosCiruelos.padel_club_api.Entities.Enum.EstadoReserva;
import com.LosCiruelos.padel_club_api.Entities.Enum.TipoReserva;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.Repository.BusquedaReservasAdmin;
import com.LosCiruelos.padel_club_api.common.Busquedas;
import com.LosCiruelos.padel_club_api.common.Paginacion;
import com.LosCiruelos.padel_club_api.common.Reloj;

import lombok.RequiredArgsConstructor;

/**
 * Listado de reservas para el panel del personal (ADMIN y EMPLEADO): pasadas y futuras, de
 * cualquier cliente, con filtros combinables y los totales del filtro.
 */
@Service
@RequiredArgsConstructor
public class ReservaAdminService {

    public static final int TAMANIO_POR_DEFECTO = 20;
    public static final int TAMANIO_MAXIMO = 100;

    private final BusquedaReservasAdmin busqueda;
    private final ReservaService reservaService;
    private final Reloj reloj;

    /**
     * @param tipo       NORMAL o PARTIDO (null: las dos)
     * @param pendientes solo las que deben plata: CONFIRMADA, SEÑADA y PARCIALMENTE_PAGADA (hayan jugado o no).
     *                   Se combina con {@code estados}: quedan las que cumplen los dos
     * @param orden      "asc" o "desc" por fecha y hora del turno; null: "asc" si desde es hoy o
     *                   posterior (próximas) y "desc" en el resto (historial)
     */
    @Transactional(readOnly = true)
    public ListadoReservasResponse listar(LocalDate desde, LocalDate hasta, List<EstadoReserva> estados,
            Long clienteId, String cliente, Long canchaId, TipoReserva tipo, boolean pendientes, int pagina,
            Integer tamanio, String orden) {
        int size = tamanio != null ? tamanio : TAMANIO_POR_DEFECTO;
        Paginacion.validar(pagina, size, TAMANIO_MAXIMO);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }

        FiltroReservasAdmin filtro = new FiltroReservasAdmin(desde, hasta, estadosEfectivos(estados, pendientes),
                clienteId, Busquedas.normalizar(cliente), canchaId, tipo);
        Sort.Direction direccion = direccion(orden, desde);
        PageRequest pageable = PageRequest.of(pagina, size,
                Sort.by(direccion, "fechaReserva", "horaInicio").and(Sort.by(direccion, "id")));

        Page<ReservaResponse> resultado = busqueda.buscar(filtro, pageable).map(reservaService::aResponse);
        return ListadoReservasResponse.de(resultado, busqueda.totales(filtro));
    }

    /** null: todos los estados. Con "pendientes", solo las que deben plata (de las pedidas). */
    static Set<EstadoReserva> estadosEfectivos(List<EstadoReserva> estados, boolean pendientes) {
        Set<EstadoReserva> pedidos = estados == null || estados.isEmpty() ? null : EnumSet.copyOf(estados);
        if (!pendientes) {
            return pedidos;
        }
        Set<EstadoReserva> queDeben = EnumSet.copyOf(BusquedaReservasAdmin.DEBEN);
        if (pedidos != null) {
            queDeben.retainAll(pedidos);
        }
        return queDeben;
    }

    private Sort.Direction direccion(String orden, LocalDate desde) {
        if (orden == null || orden.isBlank()) {
            boolean proximas = desde != null && !desde.isBefore(reloj.hoy());
            return proximas ? Sort.Direction.ASC : Sort.Direction.DESC;
        }
        return switch (orden.trim().toLowerCase()) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ReservaException("El orden tiene que ser 'asc' o 'desc'.");
        };
    }
}
