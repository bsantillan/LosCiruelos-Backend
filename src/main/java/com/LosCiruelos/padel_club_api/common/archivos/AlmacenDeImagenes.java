package com.LosCiruelos.padel_club_api.common.archivos;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import com.LosCiruelos.padel_club_api.common.exception.ReservaException;

import lombok.extern.slf4j.Slf4j;

/**
 * Las imágenes que sube el ADMIN (fotos de las canchas, la imagen de una novedad): se guardan en una
 * carpeta del disco ({@code ARCHIVOS_DIR}) con un nombre UUID y se sirven públicas en
 * GET /archivos/{nombre}. Se valida el tipo real del archivo (los primeros bytes), no la extensión.
 */
@Slf4j
@Component
public class AlmacenDeImagenes {

    public static final long TAMANIO_MAXIMO = 5L * 1024 * 1024;
    private static final Pattern NOMBRE = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)");

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

    /** Un archivo guardado: su nombre (UUID con la extensión) y la URL pública. */
    public record Imagen(String nombre, String url) {
    }

    private final Path directorio;

    public AlmacenDeImagenes(@Value("${app.archivos.directorio:./archivos}") String directorio) {
        this.directorio = Path.of(directorio).toAbsolutePath().normalize();
    }

    /** La URL pública de un archivo guardado. */
    public static String url(String nombre) {
        return nombre == null ? null : "/archivos/" + nombre;
    }

    /**
     * Valida y guarda la imagen.
     *
     * @throws ReservaException (400) si está vacía, pasa de 5 MB o no es JPG, PNG ni WEBP
     */
    public Imagen guardar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ReservaException("Mandá la imagen en el campo \"archivo\".");
        }
        if (archivo.getSize() > TAMANIO_MAXIMO) {
            throw new ReservaException("La imagen no puede superar los 5 MB.");
        }
        try {
            byte[] contenido = archivo.getBytes();
            TipoDeImagen tipo = TipoDeImagen.detectar(Arrays.copyOf(contenido, Math.min(16, contenido.length)))
                    .orElseThrow(() -> new ReservaException("La imagen tiene que ser JPG, PNG o WEBP."));
            String nombre = UUID.randomUUID() + "." + tipo.extension;
            Files.createDirectories(directorio);
            Files.write(directorio.resolve(nombre), contenido);
            log.info("Imagen guardada: {} ({} bytes)", nombre, contenido.length);
            return new Imagen(nombre, url(nombre));
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen", e);
        }
    }

    /** La imagen guardada con ese nombre (vacío si el nombre no es válido o no existe). */
    public Optional<Path> buscar(String nombre) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            return Optional.empty();
        }
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
     * Borra el archivo cuando se confirma la transacción en curso (si no hay transacción, en el momento):
     * si la operación se revierte, la imagen sigue estando.
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
        buscar(nombre).ifPresent(archivo -> {
            try {
                Files.deleteIfExists(archivo);
                log.info("Imagen borrada: {}", nombre);
            } catch (IOException e) {
                log.warn("No se pudo borrar la imagen {}: {}", nombre, e.getMessage());
            }
        });
    }
}
