package com.LosCiruelos.padel_club_api.Entities;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "Configuracion")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Configuracion {

    @Id
    private Long id;

    @Column(nullable = false, name = "monto_reserva", precision = 10, scale = 2)
    private BigDecimal montoReserva;

    @Column(nullable = false, name = "porcentaje_senia", precision = 5, scale = 2)
    private BigDecimal porcentajeSenia;

    @Column(nullable = false, name = "duracion_minima_turno")
    private Integer duracionMinimaTurno;

    @Column(nullable = false, name = "duracion_maxima_turno")
    private Integer duracionMaximaTurno;

    @Column(nullable = false, name = "monto_x_media_hora", precision = 10, scale = 2)
    private BigDecimal montoXMediaHora;

    // Política de cancelación del cliente: con al menos esta anticipación (minutos) se devuelve
    // todo lo pagado / la seña; con menos, nada
    @Builder.Default
    @Column(nullable = false, name = "minutos_reembolso_total")
    private Integer minutosReembolsoTotal = 1440;

    @Builder.Default
    @Column(nullable = false, name = "minutos_reembolso_senia")
    private Integer minutosReembolsoSenia = 720;

    // Recordatorio al cliente antes del turno (por WhatsApp o mail, según su perfil)
    @Builder.Default
    @Column(nullable = false, name = "recordatorio_activo")
    private Boolean recordatorioActivo = true;

    @Builder.Default
    @Column(nullable = false, name = "minutos_recordatorio")
    private Integer minutosRecordatorio = 120;

    @Builder.Default
    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(nullable = false, name = "configuracion_general_id")
    private List<DiaApertura> dias_apertura = new ArrayList<>();
}