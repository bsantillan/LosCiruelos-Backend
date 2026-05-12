package Grupo11.Seminario.Entities;

import java.time.LocalTime;
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
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "configuracion_general")
@Data
@NoArgsConstructor
public class ConfiguracionGeneral {
    
    // Se define el ID = 1 para que haya una sola instanc
    @Id
    private Integer id = 1;

    @Column(nullable = false, name = "monto_reserva")
    private Float monto_reserva;

    @Column(nullable = false, name = "monto_asociacion")
    private Float monto_asociacion;

    @Column(nullable = false, name = "porcentaje_senia")
    private Float porcentaje_seña;

    @Column(nullable = false, name = "descuento_socio")
    private Float descuento_socio;

    @Column(nullable = false, name = "monto_paletas")
    private Float monto_paletas;

    @Column(nullable = false, name = "monto_pelotas")
    private Float monto_pelotas;

    @Column(nullable = false, name = "duracion_minima_turno")
    private Integer duracion_minima_turno;

    @Column(nullable = false, name = "duracion_maxima_turno")
    private Integer duracion_maxima_turno;

    @Column(nullable = false, name = "horario_inicio_pico")
    private LocalTime horario_inicio_pico;

    @Column(nullable = false, name = "horario_fin_pico")
    private LocalTime horario_fin_pico;

    @Column(nullable = false, name = "monto_x_media_hora")
    private Float monto_x_media_hora;

    @Column(nullable = false, name = "stock_paleta")
    private Integer stock_paleta;

    @Column(nullable = false, name = "stock_pelota")
    private Integer stock_pelota;

    @OneToMany(fetch = FetchType.EAGER, cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(nullable = false, name = "configuracion_general_id")
    private List<DiaApertura> dias_apertura = new ArrayList<>();

}
