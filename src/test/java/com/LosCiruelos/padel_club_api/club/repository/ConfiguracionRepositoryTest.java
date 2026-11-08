package com.LosCiruelos.padel_club_api.club.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import com.LosCiruelos.padel_club_api.TestData;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;

/** La configuración se guarda con sus días de apertura y sus días cerrados (H2). */
@DataJpaTest(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect" })
class ConfiguracionRepositoryTest {

    private static final LocalDate NAVIDAD = LocalDate.of(2026, 12, 25);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ConfiguracionRepository repo;

    @Test
    void losDiasCerradosSeGuardanYSeBorranConLaConfiguracion() {
        Configuracion config = TestData.configuracion();
        config.cerrar(NAVIDAD, "Navidad", 1L, TestData.AHORA);
        config.cerrar(NAVIDAD.plusDays(7), "Año nuevo", 1L, TestData.AHORA);
        em.persist(config);
        em.flush();
        em.clear();

        Configuracion leida = repo.obtener();
        assertThat(leida.getDias_apertura()).hasSize(7);
        assertThat(leida.cierresDesde(TestData.HOY)).extracting(c -> c.getMotivo()).containsExactly("Navidad",
                "Año nuevo");

        leida.reabrir(NAVIDAD);
        em.flush();
        em.clear();
        assertThat(repo.obtener().cierresDesde(TestData.HOY)).extracting(c -> c.getFecha())
                .containsExactly(NAVIDAD.plusDays(7));
    }
}
