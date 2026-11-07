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
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.ventas.entity.Venta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.EstadoVenta;
import com.LosCiruelos.padel_club_api.ventas.entity.enums.MedioDePago;
import com.LosCiruelos.padel_club_api.ventas.exception.VentaNoEncontradaException;
import com.LosCiruelos.padel_club_api.ventas.repository.TotalesDeVentas;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaPorProducto;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaRepository;

import lombok.RequiredArgsConstructor;

/** Consultas de las ventas sueltas: el listado y el detalle del personal y los totales para el resumen. */
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

    /** Una venta, con su estado. El EMPLEADO solo ve las de hoy (las otras, 404). */
    @Transactional(readOnly = true)
    public VentaResponse obtener(Long id, String email) {
        Usuario personal = usuarioService.autenticado(email);
        return ventaRepository.findConDetalleById(id)
                .filter(v -> Roles.esAdmin(personal) || v.esDelDia(reloj.hoy()))
                .map(ventaMapper::aResponse)
                .orElseThrow(() -> new VentaNoEncontradaException(id));
    }

    /** Cuántas ventas cobradas y lo cobrado por medio en [inicio, fin) (la caja del resumen); las anuladas no cuentan. */
    public TotalesDeVentas totales(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.totales(inicio, fin, EstadoVenta.COBRADA, MedioDePago.EFECTIVO,
                MedioDePago.MERCADO_PAGO, MedioDePago.CUENTA_DNI);
    }

    /** Ventas con Cuenta DNI en [inicio, fin), para que el ADMIN las controle contra la cuenta. */
    public List<Venta> cuentaDni(LocalDateTime inicio, LocalDateTime fin, Boolean verificada) {
        return ventaRepository.porMedio(MedioDePago.CUENTA_DNI, inicio, fin, verificada);
    }

    /** Lo vendido de cada producto en [inicio, fin), sin las ventas anuladas. */
    public List<VentaPorProducto> porProducto(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.porProducto(inicio, fin, EstadoVenta.COBRADA);
    }
}
