package com.LosCiruelos.padel_club_api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes.TipoDeImagen;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/** Las imágenes subidas: el tipo real por los primeros bytes, el tamaño, el nombre UUID y el borrado. */
public class AlmacenDeImagenesTest {

    public static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
    public static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };
    public static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };

    @TempDir
    Path carpeta;

    @Test
    void reconoceElTipoRealPorLosPrimerosBytes() {
        assertThat(TipoDeImagen.detectar(PNG)).contains(TipoDeImagen.PNG);
        assertThat(TipoDeImagen.detectar(JPG)).contains(TipoDeImagen.JPG);
        assertThat(TipoDeImagen.detectar(WEBP)).contains(TipoDeImagen.WEBP);
        assertThat(TipoDeImagen.detectar("GIF89a".getBytes())).isEmpty();
    }

    @Test
    void guardaConNombreUuidYLaSirveConSuTipo() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        AlmacenDeImagenes.Imagen imagen = almacen.guardar(new MockMultipartFile("archivo", "foto.jpg", "image/jpeg", PNG));

        // la extensión sale del contenido (es un PNG aunque se llame .jpg)
        assertThat(imagen.nombre()).matches("[0-9a-f-]{36}\\.png");
        assertThat(imagen.url()).isEqualTo("/archivos/" + imagen.nombre());
        assertThat(almacen.buscar(imagen.nombre())).isPresent();
        assertThat(Files.readAllBytes(carpeta.resolve(imagen.nombre()))).isEqualTo(PNG);
        assertThat(almacen.contentType(imagen.nombre())).isEqualTo("image/png");

        almacen.borrarAlConfirmar(imagen.nombre()); // sin transacción: en el momento
        assertThat(almacen.buscar(imagen.nombre())).isEmpty();
    }

    @Test
    void rechazaOtrosTiposLasGrandesYLosNombresRaros() {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        assertThatThrownBy(() -> almacen.guardar(new MockMultipartFile("archivo", "foto.png", "image/png",
                "<script>".getBytes())))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La imagen tiene que ser JPG, PNG o WEBP.");
        byte[] grande = new byte[(int) AlmacenDeImagenes.TAMANIO_MAXIMO + 1];
        System.arraycopy(PNG, 0, grande, 0, PNG.length);
        assertThatThrownBy(() -> almacen.guardar(new MockMultipartFile("archivo", "foto.png", "image/png", grande)))
                .hasMessage("La imagen no puede superar los 5 MB.");
        assertThatThrownBy(() -> almacen.guardar(new MockMultipartFile("archivo", new byte[0])))
                .hasMessage("Mandá la imagen en el campo \"archivo\".");
        assertThat(almacen.buscar("../application.properties")).isEmpty();
        assertThat(almacen.buscar("no-existe.png")).isEmpty();
    }
}
