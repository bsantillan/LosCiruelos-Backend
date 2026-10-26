package com.LosCiruelos.padel_club_api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.LosCiruelos.padel_club_api.Exceptions.ValidationException;

class TelefonosTest {

    @ParameterizedTest
    @ValueSource(strings = { "011 15-2345-6789", "11 2345-6789", "1123456789", "+54 9 11 2345 6789",
            "+5491123456789", "5491123456789", "(011) 15 2345 6789" })
    void normalizaLosCelularesDeBuenosAires(String telefono) {
        assertThat(Telefonos.normalizar(telefono)).isEqualTo("5491123456789");
    }

    @ParameterizedTest
    @ValueSource(strings = { "0351 15-612-3456", "351 6123456", "+54 9 351 612 3456" })
    void normalizaLosCelularesDelInterior(String telefono) {
        assertThat(Telefonos.normalizar(telefono)).isEqualTo("5493516123456");
    }

    @ParameterizedTest
    @ValueSource(strings = { "hola", "123", "11 2345", "+54 0000000000" })
    void rechazaLosInvalidos(String telefono) {
        assertThatThrownBy(() -> Telefonos.normalizar(telefono)).isInstanceOf(ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = { "", "  " })
    void vacioEsNull(String telefono) {
        assertThat(Telefonos.normalizar(telefono)).isNull();
        assertThat(Telefonos.normalizar(null)).isNull();
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "5492214375254, 54221154375254", // La Plata (área de 3 dígitos), probado contra Meta
            "5491161234567, 54111561234567", // Buenos Aires (área de 2)
            "5493516123456, 54351156123456", // Córdoba
            "34612345678, 34612345678" })    // otros países: igual
    void alEnviarLosCelularesArgentinosVanConEl15(String guardado, String enviado) {
        assertThat(Telefonos.paraEnvioWhatsApp(guardado)).isEqualTo(enviado);
    }

    @ParameterizedTest
    @ValueSource(strings = { "+34 612 34 56 78" })
    void aceptaNumerosDeOtrosPaises(String telefono) {
        assertThat(Telefonos.normalizar(telefono)).isEqualTo("34612345678");
    }
}
