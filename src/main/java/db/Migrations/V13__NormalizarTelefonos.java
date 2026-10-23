package db.Migrations;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.LosCiruelos.padel_club_api.Services.WhatsApp.Telefonos;

/**
 * V13: normaliza los teléfonos guardados antes de V8 (por ejemplo "1122334455") al formato de
 * WhatsApp ("5491122334455"), con la misma regla que la app ({@link Telefonos#normalizar}).
 * <ul>
 * <li>No toca {@code telefono_verificado}: el número es el mismo, escrito de otra forma.</li>
 * <li>Si no es un teléfono válido, queda en null y sin verificar.</li>
 * <li>Única excepción a la verificación: si al normalizarlo coincide con el celular verificado de
 * otra cuenta activa, el de esta cuenta queda sin verificar, para respetar el índice único de V11
 * (se conserva el que ya estaba normalizado o, entre dos viejos, el de la cuenta más antigua).</li>
 * </ul>
 * Es idempotente: con los teléfonos ya normalizados no cambia nada. No loguea los números.
 */
public class V13__NormalizarTelefonos extends BaseJavaMigration {

    private static final Logger log = LoggerFactory.getLogger(V13__NormalizarTelefonos.class);

    /** Cuántos teléfonos se normalizaron, cuántos quedaron en null y cuántos perdieron la verificación. */
    public record Resultado(int normalizados, int invalidos, int sinVerificarPorDuplicado) {
    }

    @Override
    public void migrate(Context context) throws Exception {
        Resultado r = normalizar(context.getConnection());
        log.info("V13: {} teléfonos normalizados, {} inválidos quedaron en null, {} perdieron la verificación "
                + "por coincidir con el celular verificado de otra cuenta activa",
                r.normalizados(), r.invalidos(), r.sinVerificarPorDuplicado());
    }

    private record Fila(long id, String telefono, boolean verificado, boolean activo) {
    }

    public static Resultado normalizar(Connection conexion) throws SQLException {
        List<Fila> filas = new ArrayList<>();
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT id, telefono, telefono_verificado, enabled FROM usuarios WHERE telefono IS NOT NULL ORDER BY id");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                filas.add(new Fila(rs.getLong(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4)));
            }
        }

        int normalizados = 0;
        int invalidos = 0;
        int sinVerificar = 0;
        for (Fila fila : filas) {
            String normalizado = Telefonos.normalizarSinError(fila.telefono());
            if (Objects.equals(normalizado, fila.telefono())) {
                continue; // ya está bien
            }
            if (normalizado == null) {
                actualizar(conexion, fila.id(), null, false);
                invalidos++;
                continue;
            }
            boolean verificado = fila.verificado();
            if (verificado && fila.activo() && otraCuentaActivaLoTieneVerificado(conexion, normalizado, fila.id())) {
                verificado = false;
                sinVerificar++;
            }
            actualizar(conexion, fila.id(), normalizado, verificado);
            normalizados++;
        }
        return new Resultado(normalizados, invalidos, sinVerificar);
    }

    private static boolean otraCuentaActivaLoTieneVerificado(Connection conexion, String telefono, long id)
            throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "SELECT 1 FROM usuarios WHERE telefono = ? AND telefono_verificado AND enabled AND id <> ?")) {
            ps.setString(1, telefono);
            ps.setLong(2, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void actualizar(Connection conexion, long id, String telefono, boolean verificado)
            throws SQLException {
        try (PreparedStatement ps = conexion.prepareStatement(
                "UPDATE usuarios SET telefono = ?, telefono_verificado = ? WHERE id = ?")) {
            ps.setString(1, telefono);
            ps.setBoolean(2, verificado);
            ps.setLong(3, id);
            ps.executeUpdate();
        }
    }
}
