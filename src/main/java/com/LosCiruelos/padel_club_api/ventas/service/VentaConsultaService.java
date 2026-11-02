package com.LosCiruelos.padel_club_api.ventas.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.Roles;
import com.LosCiruelos.padel_club_api.common.exception.PermisoDenegadoException;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.VentaMapper;
import com.LosCiruelos.padel_club_api.usuarios.service.UsuarioService;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.repository.TotalesDeVentas;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

import lombok.RequiredArgsConstructor;

/** Consultas de las ventas sueltas: el listado del personal y los totales para el resumen. */
@Service
@RequiredArgsConstructor
public class VentaConsultaService {

    private final VentaRepository ventaRepository;
    private final UsuarioService usuarioService;
    private final VentaMapper ventaMapper;
    private final Reloj reloj;

    /** Ventas entre desde y hasta (sin fechas, las de hoy). El ADMIN ve cualquier período; el EMPLEADO, solo hoy. */
    @Transactional(readOnly = true)
    public List<VentaResponse> listar(LocalDate desde, LocalDate hasta, String email) {
        LocalDate hoy = reloj.hoy();
        if ((desde == null) != (hasta == null)) {
            throw new ReservaException("Indicá las dos fechas (desde y hasta) o ninguna para ver las de hoy.");
        }
        if (desde == null) {
            desde = hoy;
            hasta = hoy;
        }
        if (desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        if (!Roles.esAdmin(usuarioService.autenticado(email)) && !(desde.equals(hoy) && hasta.equals(hoy))) {
            throw new PermisoDenegadoException("Solo podés ver las ventas de hoy.");
        }
        return ventaRepository.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                desde.atStartOfDay(), hasta.plusDays(1).atStartOfDay()).stream()
                .map(ventaMapper::aResponse)
                .toList();
    }

    /** Cuántas ventas y lo cobrado por medio en [inicio, fin) (la caja del resumen). */
    public TotalesDeVentas totales(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.totales(inicio, fin, MedioDePago.EFECTIVO, MedioDePago.MERCADO_PAGO,
                MedioDePago.CUENTA_DNI);
    }

    /** Lo vendido de cada producto en [inicio, fin). */
    public List<VentaPorProducto> porProducto(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.porProducto(inicio, fin);
    }
}
