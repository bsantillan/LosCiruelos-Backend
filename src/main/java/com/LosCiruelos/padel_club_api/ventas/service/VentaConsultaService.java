package com.LosCiruelos.padel_club_api.ventas.service;

import com.LosCiruelos.padel_club_api.common.Jornada;
import com.LosCiruelos.padel_club_api.club.service.JornadaService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
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
import com.LosCiruelos.padel_club_api.ventas.dto.response.MedioDeVentaResponse;
import com.LosCiruelos.padel_club_api.ventas.dto.response.ListadoVentasResponse;
import com.LosCiruelos.padel_club_api.ventas.dto.response.VentaResponse;
import com.LosCiruelos.padel_club_api.ventas.repository.FiltroVentas;
import com.LosCiruelos.padel_club_api.ventas.repository.VentaSpecification;
import com.LosCiruelos.padel_club_api.common.Busquedas;
import com.LosCiruelos.padel_club_api.common.Paginacion;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import java.util.Set;
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
    private final CobroQrDeVentas cobroQr;
    private final JornadaService jornadaService;

    /** Los medios con que se puede cobrar una venta, en el orden en que se muestran, y si están disponibles. */
    public List<MedioDeVentaResponse> medios() {
        boolean qr = cobroQr.estaConfigurado();
        return Arrays.stream(MedioDePago.values()).map(m -> ventaMapper.aMedio(m, qr)).toList();
    }

    /** Tamaño de página del listado. */
    static final int TAMANIO_POR_DEFECTO = 20;
    static final int TAMANIO_MAXIMO = 100;

    /**
     * Listado de ventas del personal, paginado, con filtros opcionales que se combinan y los totales
     * de todo el filtro. Sin fechas, las de hoy. El ADMIN ve cualquier período; el EMPLEADO, solo hoy.
     *
     * @param cliente texto a buscar en nombre, apellido o email del cliente (al menos 3 letras)
     * @param orden   "desc" (por defecto: de la más nueva a la más vieja) o "asc"
     */
    @Transactional(readOnly = true)
    public ListadoVentasResponse listar(LocalDate desde, LocalDate hasta, Long productoId, String cliente,
            Long clienteId, List<MedioDePago> medios, List<EstadoVenta> estados, int pagina, Integer tamanio,
            String orden, String email) {
        boolean esAdmin = Roles.esAdmin(usuarioService.autenticado(email));
        // el "hoy" del EMPLEADO es la jornada en curso (pasada la medianoche, sigue la del día anterior)
        Jornada jornada = esAdmin ? null : jornadaService.actual();
        LocalDate hoy = esAdmin ? reloj.hoy() : jornada.fecha();
        if (desde == null && hasta == null) {
            desde = hoy;
            hasta = hoy;
        }
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        if (!esAdmin && !(hoy.equals(desde) && hoy.equals(hasta))) {
            throw new PermisoDenegadoException("Solo podés ver las ventas de hoy.");
        }
        // desde y hasta son jornadas (de la apertura de un día a la del siguiente), para todos
        LocalDateTime inicio = jornada != null ? jornada.inicio() : desde != null ? jornadaService.del(desde).inicio() : null;
        LocalDateTime fin = jornada != null ? jornada.fin() : hasta != null ? jornadaService.del(hasta).fin() : null;
        String texto = Busquedas.normalizar(cliente);
        if (texto != null && texto.replace(" ", "").length() < 3) {
            throw new ReservaException("Escribí al menos 3 letras para buscar un cliente.");
        }
        FiltroVentas filtro = new FiltroVentas(inicio, fin, productoId, texto, clienteId,
                medios != null ? Set.copyOf(medios) : null, estados != null ? Set.copyOf(estados) : null);
        Sort.Direction direccion = direccion(orden);
        PageRequest pageable = Paginacion.pagina(pagina, tamanio != null ? tamanio : TAMANIO_POR_DEFECTO,
                TAMANIO_MAXIMO, Sort.by(direccion, "createdAt").and(Sort.by(direccion, "id")));
        return ventaMapper.aListado(ventaRepository.findAll(VentaSpecification.delListado(filtro), pageable),
                ventaRepository.totalesDelListado(filtro));
    }

    private static Sort.Direction direccion(String orden) {
        if (orden == null || orden.isBlank()) {
            return Sort.Direction.DESC;
        }
        return switch (orden.trim().toLowerCase()) {
            case "asc" -> Sort.Direction.ASC;
            case "desc" -> Sort.Direction.DESC;
            default -> throw new ReservaException("El orden tiene que ser 'asc' o 'desc'.");
        };
    }

    /** Una venta, con su estado. El EMPLEADO solo ve las de hoy (las otras, 404). */
    @Transactional(readOnly = true)
    public VentaResponse obtener(Long id, String email) {
        Usuario personal = usuarioService.autenticado(email);
        return ventaRepository.findConDetalleById(id)
                .filter(v -> Roles.esAdmin(personal) || v.esDeLaJornada(jornadaService.actual()))
                .map(ventaMapper::aResponse)
                .orElseThrow(() -> new VentaNoEncontradaException(id));
    }

    /** Cuántas ventas cobradas y lo cobrado por medio en [inicio, fin) (la caja del resumen); las anuladas no cuentan. */
    public TotalesDeVentas totales(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.totales(inicio, fin, EstadoVenta.COBRADA, MedioDePago.EFECTIVO,
                MedioDePago.MERCADO_PAGO, MedioDePago.TRANSFERENCIA_MP, MedioDePago.CUENTA_DNI);
    }

    /** Lo devuelto de las ventas en [inicio, fin), por fecha de la devolución (la caja del resumen). */
    public BigDecimal devoluciones(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.devoluciones(inicio, fin);
    }

    /**
     * Ventas cobradas a mano que el ADMIN controla contra la cuenta (Cuenta DNI o transferencia) en
     * [inicio, fin); verificada null: todas.
     */
    public List<Venta> verificables(LocalDateTime inicio, LocalDateTime fin, Boolean verificada,
            java.util.Collection<MedioDePago> medios) {
        return ventaRepository.porMedios(medios, inicio, fin, verificada);
    }

    /** Lo vendido de cada producto en [inicio, fin), sin las ventas anuladas ni lo devuelto. */
    public List<VentaPorProducto> porProducto(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.porProducto(inicio, fin, EstadoVenta.COBRADA);
    }

    /** Las ventas cobradas o anuladas de [inicio, fin), para el cierre de caja (las anuladas se restan). */
    @Transactional(readOnly = true)
    public List<Venta> paraLaCaja(LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.findParaLaCaja(inicio, fin, java.util.EnumSet.of(EstadoVenta.COBRADA, EstadoVenta.ANULADA));
    }

    /** Las devoluciones de ventas de [inicio, fin), para el cierre de caja. */
    @Transactional(readOnly = true)
    public List<com.LosCiruelos.padel_club_api.ventas.entity.VentaDevolucion> devolucionesParaLaCaja(
            LocalDateTime inicio, LocalDateTime fin) {
        return ventaRepository.findDevolucionesParaLaCaja(inicio, fin);
    }
}
