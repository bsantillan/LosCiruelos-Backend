package db.Migrations;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** La migración V13 contra una tabla usuarios mínima en H2. */
class V13__NormalizarTelefonosTest {

    private Connection conexion;

    @BeforeEach
    void base() throws SQLException {
        conexion = DriverManager.getConnection("jdbc:h2:mem:v13;DB_CLOSE_DELAY=-1");
        try (Statement st = conexion.createStatement()) {
            st.execute("DROP TABLE IF EXISTS usuarios");
            st.execute("CREATE TABLE usuarios (id bigint PRIMARY KEY, telefono varchar(20), "
                    + "telefono_verificado boolean NOT NULL, enabled boolean NOT NULL)");
        }
    }

    @AfterEach
    void cerrar() throws SQLException {
        conexion.close();
    }

    private void usuario(long id, String telefono, boolean verificado, boolean activo) throws SQLException {
        try (var ps = conexion.prepareStatement("INSERT INTO usuarios VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, id);
            ps.setString(2, telefono);
            ps.setBoolean(3, verificado);
            ps.setBoolean(4, activo);
            ps.executeUpdate();
        }
    }

    /** id → "telefono|verificado" */
    private Map<Long, String> usuarios() throws SQLException {
        Map<Long, String> filas = new LinkedHashMap<>();
        try (Statement st = conexion.createStatement();
                ResultSet rs = st.executeQuery("SELECT id, telefono, telefono_verificado FROM usuarios ORDER BY id")) {
            while (rs.next()) {
                filas.put(rs.getLong(1), rs.getString(2) + "|" + rs.getBoolean(3));
            }
        }
        return filas;
    }

    @Test
    void normalizaSinTocarLaVerificacionYDejaEnNullLosInvalidos() throws SQLException {
        usuario(1, "1122334455", true, true);       // formato viejo, verificado
        usuario(2, "011 15-6123-4567", false, true); // formato viejo, sin verificar
        usuario(3, "5493516123456", true, true);    // ya normalizado
        usuario(4, "12345678", true, true);         // inválido
        usuario(5, null, false, true);              // sin teléfono

        V13__NormalizarTelefonos.Resultado r = V13__NormalizarTelefonos.normalizar(conexion);

        assertThat(r.normalizados()).isEqualTo(2);
        assertThat(r.invalidos()).isEqualTo(1);
        assertThat(r.sinVerificarPorDuplicado()).isZero();
        assertThat(usuarios()).containsExactly(
                Map.entry(1L, "5491122334455|true"),
                Map.entry(2L, "5491161234567|false"),
                Map.entry(3L, "5493516123456|true"),
                Map.entry(4L, "null|false"),
                Map.entry(5L, "null|false"));
    }

    @Test
    void esIdempotente() throws SQLException {
        usuario(1, "1122334455", true, true);
        V13__NormalizarTelefonos.normalizar(conexion);

        V13__NormalizarTelefonos.Resultado otraVez = V13__NormalizarTelefonos.normalizar(conexion);

        assertThat(otraVez).isEqualTo(new V13__NormalizarTelefonos.Resultado(0, 0, 0));
        assertThat(usuarios()).containsExactly(Map.entry(1L, "5491122334455|true"));
    }

    @Test
    void siCoincideConElCelularVerificadoDeOtraCuentaActivaQuedaSinVerificar() throws SQLException {
        usuario(1, "5491122334455", true, true); // ya normalizado y verificado
        usuario(2, "11 2233-4455", true, true);  // el mismo número en formato viejo
        usuario(3, "1122334455", true, false);   // cuenta desactivada: no choca con el índice

        V13__NormalizarTelefonos.Resultado r = V13__NormalizarTelefonos.normalizar(conexion);

        assertThat(r.sinVerificarPorDuplicado()).isEqualTo(1);
        assertThat(usuarios()).containsExactly(
                Map.entry(1L, "5491122334455|true"),
                Map.entry(2L, "5491122334455|false"),
                Map.entry(3L, "5491122334455|true"));
    }
}
