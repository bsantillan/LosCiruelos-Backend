package Grupo11.Seminario.Entities;

import java.time.LocalDate;
import java.time.LocalTime;
import Grupo11.Seminario.Entities.Enum.EstadoPago;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity(name = "Pago")
@Data
@NoArgsConstructor
public class Pago {
    
    // Se define el ID como autoincremental
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = true, name = "fecha")
    private LocalDate fecha;

    @Column(nullable = true, name = "hora")
    private LocalTime hora;

    @Column(nullable = false, name = "metodo")
    private String metodo;

    @Column(nullable = false, name = "motivo")
    private String motivo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "estado")
    private EstadoPago estado;

    @Column(nullable = false, name = "monto")
    private Float monto;

    @Column(nullable = false, name = "descuento")
    private Float descuento;

    @ManyToOne(cascade = CascadeType.ALL)
    @JoinColumn(nullable = false, name = "cuenta_id")
    private Cuenta cuenta;

}
