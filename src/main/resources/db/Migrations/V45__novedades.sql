-- =====================================================================
-- V45: novedades del club que publica el ADMIN (torneos, clases, avisos), para la página de inicio.
-- Nacen como borrador; al publicarlas se completa publicada_at. Con visible_hasta dejan de mostrarse
-- solas después de esa fecha. La imagen (opcional) es un archivo en ARCHIVOS_DIR.
-- =====================================================================

CREATE TABLE public.novedades (
    id            BIGSERIAL PRIMARY KEY,
    titulo        VARCHAR(80)  NOT NULL,
    descripcion   VARCHAR(300) NOT NULL,
    etiqueta      VARCHAR(30),
    imagen        VARCHAR(80),
    publicada     BOOLEAN      NOT NULL DEFAULT FALSE,
    publicada_at  TIMESTAMP,
    visible_hasta DATE,
    creado_por_id BIGINT       NOT NULL REFERENCES public.usuarios (id),
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    CONSTRAINT ck_novedades_publicada CHECK (NOT publicada OR publicada_at IS NOT NULL)
);
-- la lista pública: publicadas, de la más nueva a la más vieja
CREATE INDEX idx_novedades_publicadas ON public.novedades (publicada_at DESC) WHERE publicada;
CREATE INDEX idx_novedades_created_at ON public.novedades (created_at DESC);
