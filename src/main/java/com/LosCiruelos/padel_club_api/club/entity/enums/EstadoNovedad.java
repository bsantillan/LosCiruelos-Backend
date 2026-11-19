package com.LosCiruelos.padel_club_api.club.entity.enums;

/** En qué está una novedad (se calcula: no se guarda). */
public enum EstadoNovedad {
    BORRADOR,   // sin publicar
    PUBLICADA,  // publicada y vigente: se muestra en la página de inicio
    VENCIDA     // publicada, pero ya pasó su visibleHasta
}
