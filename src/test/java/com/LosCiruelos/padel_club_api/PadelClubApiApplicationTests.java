package com.LosCiruelos.padel_club_api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Levanta la aplicación completa: necesita las variables de entorno y un PostgreSQL
 * accesible, por eso solo corre cuando DB_URL está definida.
 */
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
class PadelClubApiApplicationTests {

	@Test
	void contextLoads() {
	}

}
