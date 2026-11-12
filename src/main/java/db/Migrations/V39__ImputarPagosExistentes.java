package db.Migrations;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * V39: imputa los pagos que ya existían (V38) con la regla automática de la app: primero la cancha
 * hasta completarla y después cada línea de producto en orden de creación. Se imputa lo que el club
 * se queda de cada pago (monto − lo devuelto), de los cobrados y de las bonificaciones, del más viejo
 * al más nuevo. Lo que sobre (datos inconsistentes) va a la cancha. Es idempotente: no toca los
 * pagos que ya tienen imputaciones.
 */
public class V39__ImputarPagosExistentes extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V39__ImputarPagosExistentes.class);

    @Override
    public void migrate(Context context) throws Exception {
        int imputados = imputar(context.getConnection());
        log.info("V39: {} pagos imputados", imputados);
    }

    private record Pago(long id, long reservaId, BigDecimal retenido) {
    }

    /** @return cuántos pagos se imputaron */
    public static int imputar(Connection conexion) throws SQLException {
        Map<Long, List<Pago>> porReserva = new LinkedHashMap<>();
        try (PreparedStatement ps = conexion.prepareStatement("""
                SELECT p.id, p.reserva_id,
                       CASE WHEN p.estado = 'APROBADO' THEN p.monto - COALESCE(p.monto_reembolsado, 0)
                            ELSE p.monto - COALESCE(p.monto_reembolsado, p.monto) END
                FROM pagos p
                WHERE p.estado IN ('APROBADO', 'DEVUELTO', 'A_REEMBOLSAR', 'REEMBOLSO_PENDIENTE')
                  AND NOT EXISTS (SELECT 1 FROM pago_imputaciones i WHERE i.pago_id = p.id)
                ORDER BY p.reserva_id, COALESCE(p.paid_at, p.created_at), p.id
                """); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Pago pago = new Pago(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3));
                if (pago.retenido().signum() > 0) {
                    porReserva.computeIfAbsent(pago.reservaId(), id -> new ArrayList<>()).add(pago);
                }
            }
        }

        int imputados = 0;
        for (Map.Entry<Long, List<Pago>> entrada : porReserva.entrySet()) {
            long reservaId = entrada.getKey();
            Map<Long, BigDecimal> pendientePorLinea = new LinkedHashMap<>();
            BigDecimal productos = BigDecimal.ZERO;
            try (PreparedStatement ps = conexion.prepareStatement(
                    "SELECT id, cantidad * precio_unitario FROM reserva_productos WHERE reserva_id = ? ORDER BY id")) {
                ps.setLong(1, reservaId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        pendientePorLinea.put(rs.getLong(1), rs.getBigDecimal(2));
                        productos = productos.add(rs.getBigDecimal(2));
                    }
                }
            }
            BigDecimal pendienteCancha;
            try (PreparedStatement ps = conexion.prepareStatement("SELECT monto_total FROM reservas WHERE id = ?")) {
                ps.setLong(1, reservaId);
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    pendienteCancha = rs.getBigDecimal(1).subtract(productos).max(BigDecimal.ZERO);
                }
            }

            for (Pago pago : entrada.getValue()) {
                BigDecimal restante = pago.retenido();
                BigDecimal aCancha = restante.min(pendienteCancha);
                pendienteCancha = pendienteCancha.subtract(aCancha);
                restante = restante.subtract(aCancha);
                for (Map.Entry<Long, BigDecimal> linea : pendientePorLinea.entrySet()) {
                    BigDecimal aLinea = restante.min(linea.getValue());
                    if (aLinea.signum() > 0) {
                        insertar(conexion, pago.id(), "PRODUCTO", linea.getKey(), aLinea);
                        linea.setValue(linea.getValue().subtract(aLinea));
                        restante = restante.subtract(aLinea);
                    }
                }
                aCancha = aCancha.add(restante); // lo que sobre, a la cancha
                if (aCancha.signum() > 0) {
                    insertar(conexion, pago.id(), "CANCHA", null, aCancha);
                }
                imputados++;
            }
        }
        return imputados;
    }

    private static void insertar(Connection conexion, long pagoId, String concepto, Long lineaId, BigDecimal monto)
            throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "INSERT INTO pago_imputaciones (pago_id, concepto, reserva_producto_id, monto) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, pagoId);
            ps.setString(2, concepto);
            if (lineaId != null) {
                ps.setLong(3, lineaId);
            } else {
                ps.setNull(3, java.sql.Types.BIGINT);
            }
            ps.setBigDecimal(4, monto);
            ps.executeUpdate();
        }
    }
}
