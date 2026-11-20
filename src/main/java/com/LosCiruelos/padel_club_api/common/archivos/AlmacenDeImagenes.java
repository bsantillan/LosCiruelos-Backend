package com.LosCiruelos.padel_club_api.common.archivos;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;

/**
 * Las imágenes que sube el ADMIN (fotos de las canchas, la imagen de una novedad). Se valida el tipo
 * real del archivo (los primeros bytes, no la extensión) y no se guarda el original: se achica a
 * {@value #LADO_MAXIMO} px de lado como máximo, se recomprime (JPG; PNG si tiene transparencia),
 * se le sacan los metadatos (la ubicación GPS de una foto del celular) respetando la orientación de
 * la cámara, y se genera una miniatura de {@value #LADO_MINIATURA} px para los listados. Se guardan en
 * una carpeta del disco ({@code ARCHIVOS_DIR}) con un nombre UUID ({@code <uuid>.jpg} y
 * {@code <uuid>-min.jpg}) y se sirven públicas en GET /archivos/{nombre}.
 */
@Slf4j
@Component
public class AlmacenDeImagenes {

    public static final long TAMANIO_MAXIMO = 5L * 1024 * 1024;
    /** Lado mayor de la imagen guardada: alcanza para verla a pantalla completa. */
    public static final int LADO_MAXIMO = 1600;
    /** Lado mayor de la miniatura (tarjetas y listados). */
    public static final int LADO_MINIATURA = 480;
    /** Más que esto no se decodifica (una imagen chica en bytes puede ocupar cientos de MB en memoria). */
    public static final long PIXELES_MAXIMOS = 50_000_000L;
    private static final double CALIDAD_JPG = 0.82;
    private static final String SUFIJO_MINIATURA = "-min";
    private static final Pattern NOMBRE = Pattern.compile(
            "([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})(-min)?\\.(jpg|png|webp)");

    /** Los tipos aceptados, con su extensión y su content type. */
    public enum TipoDeImagen {
        JPG("jpg", "image/jpeg"),
        PNG("png", "image/png"),
        WEBP("webp", "image/webp");

        private final String extension;
        private final String contentType;

        TipoDeImagen(String extension, String contentType) {
            this.extension = extension;
            this.contentType = contentType;
        }

        public String contentType() {
            return contentType;
        }

        /** El tipo real según los primeros bytes (vacío si no es JPG, PNG ni WEBP). */
        public static Optional<TipoDeImagen> detectar(byte[] cabecera) {
            if (cabecera.length >= 3 && (cabecera[0] & 0xFF) == 0xFF && (cabecera[1] & 0xFF) == 0xD8
                    && (cabecera[2] & 0xFF) == 0xFF) {
                return Optional.of(JPG);
            }
            byte[] png = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A };
            if (cabecera.length >= 8 && Arrays.equals(Arrays.copyOf(cabecera, 8), png)) {
                return Optional.of(PNG);
            }
            if (cabecera.length >= 12 && cabecera[0] == 'R' && cabecera[1] == 'I' && cabecera[2] == 'F'
                    && cabecera[3] == 'F' && cabecera[8] == 'W' && cabecera[9] == 'E' && cabecera[10] == 'B'
                    && cabecera[11] == 'P') {
                return Optional.of(WEBP);
            }
            return Optional.empty();
        }

        static Optional<TipoDeImagen> porExtension(String nombre) {
            String ext = nombre.substring(nombre.lastIndexOf('.') + 1);
            return Arrays.stream(values()).filter(t -> t.extension.equals(ext)).findFirst();
        }
    }

    /** Un archivo guardado: su nombre (UUID con la extensión) y las URLs públicas. */
    public record Imagen(String nombre, String url, String miniaturaUrl) {
    }

    private final Path directorio;

    public AlmacenDeImagenes(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
        // en el jar de Spring Boot el lector de WEBP (TwelveMonkeys) no se registra solo
        ImageIO.scanForPlugins();
    }

    /** La URL pública de un archivo guardado. */
    public static String url(String nombre) {
        return nombre == null ? null : "/archivos/" + nombre;
    }

    /**
     * La URL de la miniatura de un archivo guardado ({@code <uuid>-min.<ext>}). Si la imagen es de
     * antes de las miniaturas, GET /archivos la resuelve con la original.
     */
    public static String urlMiniatura(String nombre) {
        return nombre == null ? null : url(nombreMiniatura(nombre));
    }

    private static String nombreMiniatura(String nombre) {
        int punto = nombre.lastIndexOf('.');
        return nombre.substring(0, punto) + SUFIJO_MINIATURA + nombre.substring(punto);
    }

    /**
     * Valida la imagen y guarda la versión achicada y la miniatura.
     *
     * @throws ReservaException (400) si está vacía, pasa de 5 MB, no es JPG, PNG ni WEBP, no se puede
     *                          leer o pasa de {@value #PIXELES_MAXIMOS} píxeles
     */
    public Imagen guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReservaException("Mandá la imagen en el campo \"archivo\".");
        }
        if (archivo.getSize() > TAMANIO_MAXIMO) {
            throw new ReservaException("La imagen no puede superar los 5 MB.");
        }
        byte[] contenido;
        try {
            contenido = archivo.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer la imagen subida", e);
        }
        TipoDeImagen.detectar(Arrays.copyOf(contenido, Math.min(16, contenido.length)))
                .orElseThrow(() -> new ReservaException("La imagen tiene que ser JPG, PNG o WEBP."));
        validarPixeles(contenido);

        BufferedImage imagen = leer(contenido);
        BufferedImage grande = achicar(imagen, LADO_MAXIMO);
        BufferedImage miniatura = achicar(grande, LADO_MINIATURA);
        boolean transparente = tieneTransparencia(grande);
        String extension = transparente ? "png" : "jpg";

        String nombre = UUID.randomUUID() + "." + extension;
        try {
            Files.createDirectories(directorio);
            escribir(grande, directorio.resolve(nombre), transparente);
            escribir(miniatura, directorio.resolve(nombreMiniatura(nombre)), transparente);
        } catch (IOException e) {
            borrar(nombre);
            throw new IllegalStateException("No se pudo guardar la imagen", e);
        }
        log.info("Imagen guardada: {} ({}x{}, subida de {} bytes)", nombre, grande.getWidth(), grande.getHeight(),
                contenido.length);
        return new Imagen(nombre, url(nombre), urlMiniatura(nombre));
    }

    /** Lee solo el encabezado: el tamaño en píxeles, antes de decodificar la imagen entera. */
    private static void validarPixeles(byte[] contenido) {
        try (ImageInputStream entrada = ImageIO.createImageInputStream(new ByteArrayInputStream(contenido))) {
            Iterator<ImageReader> lectores = ImageIO.getImageReaders(entrada);
            if (!lectores.hasNext()) {
                throw ilegible();
            }
            ImageReader lector = lectores.next();
            try {
                lector.setInput(entrada, true, true);
                long pixeles = (long) lector.getWidth(0) * lector.getHeight(0);
                if (pixeles > PIXELES_MAXIMOS) {
                    throw new ReservaException("La imagen es demasiado grande: como máximo 50 megapíxeles.");
                }
            } finally {
                lector.dispose();
            }
        } catch (IOException e) {
            throw ilegible();
        }
    }

    /** Decodifica la imagen girada según la orientación EXIF de la cámara. */
    private static BufferedImage leer(byte[] contenido) {
        try {
            return Thumbnails.of(new ByteArrayInputStream(contenido)).scale(1.0).useExifOrientation(true)
                    .asBufferedImage();
        } catch (IOException | RuntimeException e) {
            throw ilegible();
        }
    }

    private static BufferedImage achicar(BufferedImage imagen, int lado) {
        if (Math.max(imagen.getWidth(), imagen.getHeight()) <= lado) {
            return imagen;
        }
        try {
            return Thumbnails.of(imagen).size(lado, lado).keepAspectRatio(true).asBufferedImage();
        } catch (IOException e) {
            throw ilegible();
        }
    }

    /** Si algún píxel no es opaco (un logo con fondo transparente); un PNG opaco se guarda como JPG. */
    static boolean tieneTransparencia(BufferedImage imagen) {
        if (!imagen.getColorModel().hasAlpha()) {
            return false;
        }
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                if ((imagen.getRGB(x, y) >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Re-codifica la imagen: el archivo nuevo no lleva los metadatos del original. */
    private static void escribir(BufferedImage imagen, Path destino, boolean png) throws IOException {
        try (OutputStream salida = Files.newOutputStream(destino)) {
            Thumbnails.Builder<BufferedImage> builder = Thumbnails.of(imagen).scale(1.0);
            if (png) {
                builder.outputFormat("png");
            } else {
                builder.outputFormat("jpg").outputQuality(CALIDAD_JPG).imageType(BufferedImage.TYPE_INT_RGB);
            }
            builder.toOutputStream(salida);
        }
    }

    private static ReservaException ilegible() {
        return new ReservaException("No se pudo leer la imagen: probá con otra o guardala de nuevo como JPG.");
    }

    /**
     * La imagen guardada con ese nombre (vacío si el nombre no es válido o no existe). La miniatura de
     * una imagen de antes de las miniaturas se resuelve con la original.
     */
    public Optional<Path> buscar(String nombre) {
        if (nombre == null) {
            return Optional.empty();
        }
        Matcher partes = NOMBRE.matcher(nombre);
        if (!partes.matches()) {
            return Optional.empty();
        }
        Optional<Path> archivo = existente(nombre);
        if (archivo.isEmpty() && partes.group(2) != null) {
            return existente(partes.group(1) + "." + partes.group(3));
        }
        return archivo;
    }

    private Optional<Path> existente(String nombre) {
        Path archivo = directorio.resolve(nombre).normalize();
        return archivo.startsWith(directorio) && Files.isRegularFile(archivo) ? Optional.of(archivo) : Optional.empty();
    }

    public String contentType(String nombre) {
        return TipoDeImagen.porExtension(nombre).map(TipoDeImagen::contentType).orElse("application/octet-stream");
    }

    public InputStream abrir(Path archivo) throws IOException {
        return Files.newInputStream(archivo);
    }

    /**
     * Borra la imagen y su miniatura cuando se confirma la transacción en curso (si no hay
     * transacción, en el momento): si la operación se revierte, la imagen sigue estando.
     */
    public void borrarAlConfirmar(String nombre) {
        if (nombre == null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    borrar(nombre);
                }
            });
        } else {
            borrar(nombre);
        }
    }

    private void borrar(String nombre) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            return;
        }
        for (String archivo : new String[] { nombre, nombreMiniatura(nombre) }) {
            existente(archivo).ifPresent(path -> {
                try {
                    Files.deleteIfExists(path);
                    log.info("Imagen borrada: {}", archivo);
                } catch (IOException e) {
                    log.warn("No se pudo borrar la imagen {}: {}", archivo, e.getMessage());
                }
            });
        }
    }
}
