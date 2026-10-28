package com.LosCiruelos.padel_club_api.usuarios.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.usuarios.entity.AdminProfile;
import com.LosCiruelos.padel_club_api.usuarios.entity.Usuario;
import com.LosCiruelos.padel_club_api.usuarios.entity.enums.Role;

/** El perfil de cada rol cuelga de la cuenta: se guarda por cascada y se lee con ella (H2). */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class UsuarioRepositoryPerfilesTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private UsuarioRepository usuarioRepository;

    private Usuario nuevo(Role rol, String email) {
        Usuario usuario = TestData.usuario(null, rol);
        usuario.setEmail(email);
        return usuario;
    }

    @Test
    void elPerfilDelAdminSeGuardaConSuCuentaYSeLeeEnLosAdminsActivos() {
        Usuario conAvisos = nuevo(Role.ADMIN, "admin1@test.com");
        AdminProfile perfil = AdminProfile.porDefecto(conAvisos);
        perfil.setReservaConfirmada(false);
        conAvisos.setAdminProfile(perfil);
        em.persist(conAvisos); // el perfil se guarda por cascada, con la misma clave que la cuenta
        em.persist(nuevo(Role.ADMIN, "admin2@test.com"));
        Usuario inactivo = nuevo(Role.ADMIN, "admin3@test.com");
        inactivo.setEnabled(false);
        em.persist(inactivo);
        em.persist(nuevo(Role.CLIENTE, "cliente@test.com"));
        em.flush();
        em.clear();

        List<Usuario> admins = usuarioRepository.findByRolAndEnabledTrue(Role.ADMIN);

        assertThat(admins).extracting(Usuario::getEmail)
                .containsExactlyInAnyOrder("admin1@test.com", "admin2@test.com");
        Usuario leido = admins.stream().filter(a -> a.getEmail().equals("admin1@test.com")).findFirst().orElseThrow();
        assertThat(leido.getAdminProfile().getUsuarioId()).isEqualTo(leido.getId());
        assertThat(leido.getAdminProfile().getReservaConfirmada()).isFalse();
        Usuario sinPerfil = admins.stream().filter(a -> a.getEmail().equals("admin2@test.com")).findFirst()
                .orElseThrow();
        assertThat(sinPerfil.getAdminProfile()).isNull();
    }

    @Test
    void sacarlePerfilALaCuentaBorraSuFila() {
        Usuario admin = nuevo(Role.ADMIN, "admin@test.com");
        admin.setAdminProfile(AdminProfile.porDefecto(admin));
        em.persist(admin);
        em.flush();

        admin.setAdminProfile(null);
        em.flush();
        em.clear();

        assertThat(em.find(AdminProfile.class, admin.getId())).isNull();
        assertThat(em.find(Usuario.class, admin.getId())).isNotNull();
    }
}
