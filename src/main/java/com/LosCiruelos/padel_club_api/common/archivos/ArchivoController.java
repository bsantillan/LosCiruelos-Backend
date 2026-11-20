package com.LosCiruelos.padel_club_api.common.archivos;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

/**
 * Las imágenes subidas, públicas (sin sesión). Cada nombre es un UUID que no se reutiliza, así que se
 * cachean por un año.
 */
@RestController
@RequestMapping("/archivos")
@RequiredArgsConstructor
public class ArchivoController {

    private final AlmacenDeImagenes almacen;

    @GetMapping("/{nombre}")
    public ResponseEntity<InputStreamResource> archivo(@PathVariable String nombre) throws IOException {
        Path archivo = almacen.buscar(nombre).orElse(null);
        if (archivo == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .contentType(MediaType.parseMediaType(almacen.contentType(nombre)))
                .contentLength(Files.size(archivo))
                .body(new InputStreamResource(almacen.abrir(archivo)));
    }
}
