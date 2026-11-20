package com.LosCiruelos.padel_club_api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes.Imagen;
import com.LosCiruelos.padel_club_api.common.archivos.AlmacenDeImagenes.TipoDeImagen;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

/**
 * Las imágenes subidas: el tipo real por los primeros bytes, el tamaño, el procesado (achicar,
 * recomprimir, sacar los metadatos, la miniatura), el nombre UUID y el borrado.
 */
public class AlmacenDeImagenesTest {

    /** Una foto real de 40x30 (para los tests que suben imágenes). */
    public static final byte[] PNG = imagen("png", 40, 30, false);
    public static final byte[] JPG = imagen("jpg", 40, 30, false);

    private static final byte[] CABECERA_PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13 };
    private static final byte[] CABECERA_JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16 };
    private static final byte[] CABECERA_WEBP = { 'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8',
            ' ' };

    @TempDir
    Path carpeta;

    /** Una imagen de colores del tamaño pedido; con transparencia, la mitad es transparente. */
    public static byte[] imagen(String formato, int ancho, int alto, boolean transparente) {
        BufferedImage img = new BufferedImage(ancho, alto,
                transparente ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(40, 160, 70));
        g.fillRect(0, 0, ancho, transparente ? alto / 2 : alto);
        g.setColor(Color.ORANGE);
        g.fillOval(ancho / 4, alto / 4, ancho / 2, alto / 2);
        g.dispose();
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        try {
            ImageIO.write(img, formato, salida);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return salida.toByteArray();
    }

    private static MockMultipartFile archivo(String nombre, byte[] contenido) {
        return new MockMultipartFile("archivo", nombre, "application/octet-stream", contenido);
    }

    private static BufferedImage leer(Path archivo) throws IOException {
        return ImageIO.read(archivo.toFile());
    }

    @Test
    void reconoceElTipoRealPorLosPrimerosBytes() {
        assertThat(TipoDeImagen.detectar(CABECERA_PNG)).contains(TipoDeImagen.PNG);
        assertThat(TipoDeImagen.detectar(CABECERA_JPG)).contains(TipoDeImagen.JPG);
        assertThat(TipoDeImagen.detectar(CABECERA_WEBP)).contains(TipoDeImagen.WEBP);
        assertThat(TipoDeImagen.detectar("GIF89a".getBytes())).isEmpty();
    }

    @Test
    void guardaLaImagenYSuMiniaturaConNombreUuidYLasBorraJuntas() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        // un PNG opaco se guarda como JPG, aunque el archivo se llame de otra forma
        Imagen imagen = almacen.guardar(archivo("foto.webp", PNG));

        assertThat(imagen.nombre()).matches("[0-9a-f-]{36}\\.jpg");
        String uuid = imagen.nombre().replace(".jpg", "");
        assertThat(imagen.url()).isEqualTo("/archivos/" + imagen.nombre());
        assertThat(imagen.miniaturaUrl()).isEqualTo("/archivos/" + uuid + "-min.jpg");
        assertThat(almacen.buscar(imagen.nombre())).isPresent();
        assertThat(almacen.buscar(uuid + "-min.jpg")).contains(carpeta.resolve(uuid + "-min.jpg"));
        assertThat(almacen.contentType(imagen.nombre())).isEqualTo("image/jpeg");

        almacen.borrarAlConfirmar(imagen.nombre()); // sin transacción: en el momento
        assertThat(almacen.buscar(imagen.nombre())).isEmpty();
        assertThat(almacen.buscar(uuid + "-min.jpg")).isEmpty();
        assertThat(Files.list(carpeta)).isEmpty();
    }

    @Test
    void achicaLasGrandesA1600YLaMiniaturaA480SinCambiarLaProporcion() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        Imagen imagen = almacen.guardar(archivo("foto.jpg", imagen("jpg", 3200, 2400, false)));

        BufferedImage grande = leer(carpeta.resolve(imagen.nombre()));
        assertThat(grande.getWidth()).isEqualTo(1600);
        assertThat(grande.getHeight()).isEqualTo(1200);
        BufferedImage miniatura = leer(carpeta.resolve(imagen.nombre().replace(".jpg", "-min.jpg")));
        assertThat(miniatura.getWidth()).isEqualTo(480);
        assertThat(miniatura.getHeight()).isEqualTo(360);
    }

    @Test
    void noAgrandaLasChicas() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        Imagen imagen = almacen.guardar(archivo("foto.jpg", JPG));

        BufferedImage guardada = leer(carpeta.resolve(imagen.nombre()));
        assertThat(guardada.getWidth()).isEqualTo(40);
        assertThat(guardada.getHeight()).isEqualTo(30);
    }

    @Test
    void unaImagenConTransparenciaSeGuardaComoPng() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        Imagen imagen = almacen.guardar(archivo("logo.png", imagen("png", 200, 100, true)));

        assertThat(imagen.nombre()).endsWith(".png");
        assertThat(imagen.miniaturaUrl()).endsWith("-min.png");
        BufferedImage guardada = leer(carpeta.resolve(imagen.nombre()));
        assertThat(guardada.getColorModel().hasAlpha()).isTrue();
        assertThat(guardada.getRGB(0, 99) >>> 24).isZero(); // la parte de abajo sigue transparente
    }

    @Test
    void aceptaWebp() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());
        byte[] webp;
        try (InputStream entrada = getClass().getResourceAsStream("/imagenes/foto.webp")) {
            webp = entrada.readAllBytes();
        }

        Imagen imagen = almacen.guardar(archivo("foto.webp", webp));

        assertThat(imagen.nombre()).endsWith(".jpg");
        assertThat(leer(carpeta.resolve(imagen.nombre())).getWidth()).isEqualTo(64);
    }

    @Test
    void sacaLosMetadatosYRespetaLaOrientacionDeLaCamara() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());
        // una foto apaisada de 40x30 que la cámara marcó "girar 90°" (orientación EXIF 6)
        byte[] conExif = conOrientacionExif(JPG, 6);
        assertThat(new String(conExif, StandardCharsets.ISO_8859_1)).contains("Exif");

        Imagen imagen = almacen.guardar(archivo("foto.jpg", conExif));

        byte[] guardada = Files.readAllBytes(carpeta.resolve(imagen.nombre()));
        assertThat(new String(guardada, StandardCharsets.ISO_8859_1)).doesNotContain("Exif");
        BufferedImage derecha = ImageIO.read(new ByteArrayInputStream(guardada));
        assertThat(derecha.getWidth()).isEqualTo(30);
        assertThat(derecha.getHeight()).isEqualTo(40);
    }

    @Test
    void rechazaOtrosTiposLasPesadasLasIlegiblesYLosNombresRaros() {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        assertThatThrownBy(() -> almacen.guardar(archivo("foto.png", "<script>".getBytes())))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La imagen tiene que ser JPG, PNG o WEBP.");
        byte[] pesada = new byte[(int) AlmacenDeImagenes.TAMANIO_MAXIMO + 1];
        System.arraycopy(CABECERA_PNG, 0, pesada, 0, CABECERA_PNG.length);
        assertThatThrownBy(() -> almacen.guardar(archivo("foto.png", pesada)))
                .hasMessage("La imagen no puede superar los 5 MB.");
        assertThatThrownBy(() -> almacen.guardar(new MockMultipartFile("archivo", new byte[0])))
                .hasMessage("Mandá la imagen en el campo \"archivo\".");
        assertThatThrownBy(() -> almacen.guardar(archivo("foto.png", CABECERA_PNG)))
                .isInstanceOf(ReservaException.class)
                .hasMessage("No se pudo leer la imagen: probá con otra o guardala de nuevo como JPG.");
        assertThat(almacen.buscar("../application.properties")).isEmpty();
        assertThat(almacen.buscar("no-existe.png")).isEmpty();
    }

    @Test
    void rechazaMasDe50MegapixelesSinDecodificarLaImagen() {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());

        // solo el encabezado de un PNG de 10000x10000 (100 MP): pesa unos bytes
        assertThatThrownBy(() -> almacen.guardar(archivo("enorme.png", encabezadoPng(10_000, 10_000))))
                .isInstanceOf(ReservaException.class)
                .hasMessage("La imagen es demasiado grande: como máximo 50 megapíxeles.");
    }

    @Test
    void laMiniaturaDeUnaImagenDeAntesSeResuelveConLaOriginal() throws Exception {
        AlmacenDeImagenes almacen = new AlmacenDeImagenes(carpeta.toString());
        String vieja = "6cd82e47-c225-4a42-a3b7-8302d16e9835.png";
        Files.write(carpeta.resolve(vieja), PNG);

        assertThat(almacen.buscar("6cd82e47-c225-4a42-a3b7-8302d16e9835-min.png"))
                .contains(carpeta.resolve(vieja));
    }

    /** Inserta después del SOI un segmento APP1 Exif con la orientación (y nada más). */
    private static byte[] conOrientacionExif(byte[] jpg, int orientacion) {
        ByteBuffer tiff = ByteBuffer.allocate(26);
        tiff.put(new byte[] { 'I', 'I', 42, 0, 8, 0, 0, 0 }); // little endian, IFD en el byte 8
        tiff.put(new byte[] { 1, 0 }); // 1 entrada
        tiff.put(new byte[] { 0x12, 0x01, 3, 0, 1, 0, 0, 0 }); // 0x0112 Orientation, SHORT, 1 valor
        tiff.put(new byte[] { (byte) orientacion, 0, 0, 0 });
        tiff.put(new byte[] { 0, 0, 0, 0 }); // no hay otro IFD
        byte[] exif = new byte[6 + tiff.capacity()];
        System.arraycopy("Exif\0\0".getBytes(StandardCharsets.ISO_8859_1), 0, exif, 0, 6);
        System.arraycopy(tiff.array(), 0, exif, 6, tiff.capacity());
        int largo = exif.length + 2;

        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        salida.write(jpg, 0, 2); // SOI
        salida.write(0xFF);
        salida.write(0xE1); // APP1
        salida.write(largo >> 8);
        salida.write(largo & 0xFF);
        salida.write(exif, 0, exif.length);
        salida.write(jpg, 2, jpg.length - 2);
        return salida.toByteArray();
    }

    /** Firma e IHDR de un PNG del tamaño pedido, sin los píxeles. */
    private static byte[] encabezadoPng(int ancho, int alto) {
        ByteBuffer ihdr = ByteBuffer.allocate(17);
        ihdr.put("IHDR".getBytes(StandardCharsets.ISO_8859_1)).putInt(ancho).putInt(alto)
                .put(new byte[] { 8, 2, 0, 0, 0 }); // 8 bits, RGB
        CRC32 crc = new CRC32();
        crc.update(ihdr.array());
        ByteBuffer png = ByteBuffer.allocate(8 + 4 + 17 + 4);
        png.put(CABECERA_PNG, 0, 8).putInt(13).put(ihdr.array()).putInt((int) crc.getValue());
        return png.array();
    }
}
