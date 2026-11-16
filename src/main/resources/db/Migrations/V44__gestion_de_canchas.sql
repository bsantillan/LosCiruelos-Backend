-- =====================================================================
-- V44: gestión de canchas del ADMIN. Cada cancha suma nombre, tipo (TECHADA o AIRE_LIBRE),
-- superficie, iluminación, si está activa y hasta 6 fotos ordenadas (cancha_fotos; los archivos van
-- en el disco, en ARCHIVOS_DIR). El número no se repite y la descripción pasa a ser opcional.
-- Se completan las 4 canchas actuales: 1 a 3 techadas de cemento con luz; la 4, al aire libre, de
-- césped sintético con blindex y luz.
-- =====================================================================

ALTER TABLE public.canchas
    ADD COLUMN nombre      VARCHAR(40),
    ADD COLUMN tipo        VARCHAR(20),
    ADD COLUMN superficie  VARCHAR(60),
    ADD COLUMN iluminacion BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN activa      BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE public.canchas ALTER COLUMN descripcion DROP NOT NULL;

UPDATE public.canchas SET tipo = 'TECHADA', superficie = 'Cemento', iluminacion = TRUE WHERE numero IN (1, 2, 3);
UPDATE public.canchas SET tipo = 'AIRE_LIBRE', superficie = 'Césped sintético · Blindex', iluminacion = TRUE
WHERE numero = 4;
UPDATE public.canchas SET tipo = 'TECHADA' WHERE tipo IS NULL;
ALTER TABLE public.canchas ALTER COLUMN tipo SET NOT NULL;
ALTER TABLE public.canchas ADD CONSTRAINT ck_canchas_tipo CHECK (tipo IN ('TECHADA', 'AIRE_LIBRE'));
ALTER TABLE public.canchas ADD CONSTRAINT uk_canchas_numero UNIQUE (numero);
ALTER TABLE public.canchas ADD CONSTRAINT ck_canchas_numero CHECK (numero > 0);

CREATE TABLE public.cancha_fotos (
    id        BIGSERIAL PRIMARY KEY,
    cancha_id BIGINT      NOT NULL REFERENCES public.canchas (id) ON DELETE CASCADE,
    archivo   VARCHAR(80) NOT NULL UNIQUE,
    orden     INTEGER     NOT NULL,
    CONSTRAINT ck_cancha_fotos_orden CHECK (orden BETWEEN 1 AND 6)
);
CREATE INDEX idx_cancha_fotos_cancha ON public.cancha_fotos (cancha_id);
