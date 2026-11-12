package com.LosCiruelos.padel_club_api.pagos.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.BatchSize;

import com.LosCiruelos.padel_club_api.common.exception.EstadoInvalidoException;
import com.LosCiruelos.padel_club_api.pagos.entity.enums.EstadoDevolucion;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Lo que se le devuelve al cliente de un cobro (todo o una parte, por línea). Es hija del pago. Las
 * crean la devolución del ADMIN, la cancelación con reembolso y los cobros de Mercado Pago que no se
 * pueden aplicar. Cuenta en la caja cuando se entrega o Mercado Pago la confirma ({@code entregadoAt}).
 */
@Entity
@Table(name = "pago_devoluciones")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Devolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "pago_id", nullable = false)
    private Pago pago;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal monto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EstadoDevolucion estado;

    @Column(length = 200)
    private String motivo;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // quién la registró (null: la generó el sistema, por ejemplo al cancelar el cliente)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registrada_por_id")
    private Usuario registradaPor;

    // cuándo se le entregó la plata al cliente o Mercado Pago confirmó el reembolso (caja)
    @Column(name = "entregado_at")
    private LocalDateTime entregadoAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entregado_por_id")
    private Usuario entregadoPor;

    // distingue cada devolución ante Mercado Pago (claves de idempotencia)
    @Builder.Default
    @Column(nullable = false, unique = true, updatable = false)
    private UUID clave = UUID.randomUUID();

    @Builder.Default
    @BatchSize(size = 50)
    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DevolucionLinea> lineas = new ArrayList<>();

    public boolean estaPorEntregar() {
        return estado == EstadoDevolucion.A_REEMBOLSAR;
    }

    /** El personal le dio la plata al cliente: pasa de A_REEMBOLSAR a DEVUELTO. */
    public void entregar(Usuario quien, LocalDateTime ahora) {
        if (!estaPorEntregar()) {
            throw new EstadoInvalidoException("La devolución no está pendiente de entrega (está " + estado + ").");
        }
        marcarDevuelta(quien, ahora);
    }

    /** Devuelta: entregada a mano por {@code quien} o confirmada por Mercado Pago (quien = null). */
    public void marcarDevuelta(Usuario quien, LocalDateTime ahora) {
        estado = EstadoDevolucion.DEVUELTO;
        entregadoAt = ahora;
        entregadoPor = quien;
    }
}
