package db.Migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La migración V39 contra tablas mínimas en H2, con pagos de antes de las imputaciones. */
class V39__ImputarPagosExistentesTest {

    private Connection conexion;

    @BeforeEach
    void base() throws SQLException {
        conexion = DriverManager.getConnection("jdbc:h2:mem:v39;DB_CLOSE_DELAY=-1");
        try (Statement st = conexion.createStatement()) {
            st.execute("DROP ALL OBJECTS");
            st.execute("CREATE TABLE reservas (id bigint PRIMARY KEY, monto_total numeric(10,2) NOT NULL)");
            st.execute("CREATE TABLE reserva_productos (id bigint PRIMARY KEY, reserva_id bigint NOT NULL, "
                    + "cantidad int NOT NULL, precio_unitario numeric(10,2) NOT NULL)");
            st.execute("CREATE TABLE pagos (id bigint PRIMARY KEY, reserva_id bigint NOT NULL, monto numeric(10,2), "
                    + "estado varchar(30), monto_reembolsado numeric(10,2), paid_at timestamp, created_at timestamp)");
            st.execute("CREATE TABLE pago_imputaciones (id bigint AUTO_INCREMENT PRIMARY KEY, pago_id bigint, "
                    + "concepto varchar(20), reserva_producto_id bigint, monto numeric(10,2))");
            // reserva 1: cancha $15.000 + Gatorade 2 × $2.000 (línea 11) + agua $1.500 (línea 12)
            st.execute("INSERT INTO reservas VALUES (1, 20500), (2, 15000)");
            st.execute("INSERT INTO reserva_productos VALUES (11, 1, 2, 2000), (12, 1, 1, 1500)");
            // la seña online, una bonificación y el saldo en efectivo; uno rechazado no cuenta
            st.execute("INSERT INTO pagos VALUES "
                    + "(1, 1, 7500, 'APROBADO', NULL, '2026-10-10 10:00:00', '2026-10-10 09:59:00'),"
                    + "(2, 1, 9000, 'APROBADO', NULL, '2026-10-11 10:00:00', '2026-10-11 10:00:00'),"
                    + "(3, 1, 2000, 'RECHAZADO', NULL, NULL, '2026-10-11 11:00:00'),"
                    + "(4, 1, 4000, 'APROBADO', NULL, '2026-10-12 10:00:00', '2026-10-12 10:00:00'),"
                    // reserva 2 cancelada: de $10.000 se devolvieron $7.500 (queda la seña retenida)
                    + "(5, 2, 10000, 'DEVUELTO', 7500, '2026-10-12 10:00:00', '2026-10-12 10:00:00'),"
                    + "(6, 2, 5000, 'DEVUELTO', NULL, '2026-10-12 11:00:00', '2026-10-12 11:00:00')");
        }
    }

    @AfterEach
    void cerrar() throws SQLException {
        conexion.close();
    }

    /** "pago:concepto:linea:monto" en orden. */
    private List<String> imputaciones() throws SQLException {
        List<String> filas = new ArrayList<>();
        try (Statement st = conexion.createStatement(); ResultSet rs = st.executeQuery(
                "SELECT pago_id, concepto, reserva_producto_id, monto FROM pago_imputaciones ORDER BY id")) {
            while (rs.next()) {
                filas.add(rs.getLong(1) + ":" + rs.getString(2) + ":" + rs.getObject(3) + ":"
                        + rs.getBigDecimal(4).stripTrailingZeros().toPlainString());
            }
        }
        return filas;
    }

    @Test
    void imputaPrimeroLaCanchaYDespuesLosProductosEnOrdenDeCobro() throws SQLException {
        assertThat(V39__ImputarPagosExistentes.imputar(conexion)).isEqualTo(4);

        assertThat(imputaciones()).containsExactly(
                "1:CANCHA:null:7500",
                "2:PRODUCTO:11:1500", "2:CANCHA:null:7500",
                "4:PRODUCTO:11:2500", "4:PRODUCTO:12:1500",
                // de un pago devuelto en parte se imputa lo que el club se quedó; uno devuelto entero, nada
                "5:CANCHA:null:2500");
    }

    @Test
    void esIdempotente() throws SQLException {
        V39__ImputarPagosExistentes.imputar(conexion);

        assertThat(V39__ImputarPagosExistentes.imputar(conexion)).isZero();
        assertThat(imputaciones()).hasSize(6);
    }
}
