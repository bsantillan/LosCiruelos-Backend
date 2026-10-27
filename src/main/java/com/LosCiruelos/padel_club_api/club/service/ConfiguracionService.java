package com.LosCiruelos.padel_club_api.club.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.LosCiruelos.padel_club_api.Config.AppConfig.ConfiguracionCache;
import com.LosCiruelos.padel_club_api.club.entity.Configuracion;
import com.LosCiruelos.padel_club_api.club.entity.DiaApertura;
import com.LosCiruelos.padel_club_api.Exceptions.DiaSinAperturaException;
import com.LosCiruelos.padel_club_api.Exceptions.ReservaException;
import com.LosCiruelos.padel_club_api.club.repository.ConfiguracionRepository;
import com.LosCiruelos.padel_club_api.common.Dinero;
import com.LosCiruelos.padel_club_api.common.Fechas;

import lombok.RequiredArgsConstructor;

/**
 * Configuración del club (registro único id = 1) y las reglas que se derivan de
 * ella: horario de cada día, duración válida de un turno, precio y seña.
 */
@Service
@RequiredArgsConstructor
public class ConfiguracionService {

    public static final int MINUTOS_POR_BLOQUE = 30;

    private final ConfiguracionRepository configuracionRepository;

    @Cacheable(ConfiguracionCache.NOMBRE)
    public Configuracion obtener() {
        return configuracionRepository.obtener();
    }

    @CacheEvict(value = ConfiguracionCache.NOMBRE, allEntries = true)
    public Configuracion actualizar(Configuracion nueva) {
        nueva.setId(1L);
        return configuracionRepository.save(nueva);
    }

    /** Horario de apertura del día de la semana de {@code fecha}. */
    public DiaApertura obtenerDiaApertura(Configuracion config, LocalDate fecha) {
        String nombreDia = Fechas.nombreDiaCapitalizado(fecha.getDayOfWeek());
        return config.getDias_apertura().stream()
                .filter(d -> d.getDia().equalsIgnoreCase(nombreDia))
                .findFirst()
                .orElseThrow(() -> new DiaSinAperturaException("El club no abre los " + nombreDia + "."));
    }

    /** Duración múltiplo de 30 y dentro del mínimo y máximo configurados. */
    public void validarDuracion(Configuracion config, long duracionMinutos) {
        if (duracionMinutos <= 0 || duracionMinutos % MINUTOS_POR_BLOQUE != 0) {
            throw new ReservaException("El turno debe durar un múltiplo de 30 minutos.");
        }
        if (duracionMinutos < config.getDuracionMinimaTurno()) {
            throw new ReservaException(
                    "La duración mínima del turno es de %d minutos.".formatted(config.getDuracionMinimaTurno()));
        }
        if (duracionMinutos > config.getDuracionMaximaTurno()) {
            throw new ReservaException(
                    "La duración máxima del turno es de %d minutos.".formatted(config.getDuracionMaximaTurno()));
        }
    }

    /** montoReserva + montoXMediaHora × cantidad de bloques de 30 minutos. */
    public BigDecimal calcularMontoTurno(Configuracion config, long duracionMinutos) {
        long bloques = duracionMinutos / MINUTOS_POR_BLOQUE;
        return Dinero.redondear(config.getMontoReserva()
                .add(config.getMontoXMediaHora().multiply(BigDecimal.valueOf(bloques))));
    }

    /** Seña = total × porcentajeSenia / 100, redondeada a centavos. */
    public BigDecimal calcularSenia(Configuracion config, BigDecimal montoTotal) {
        return montoTotal.multiply(config.getPorcentajeSenia())
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
