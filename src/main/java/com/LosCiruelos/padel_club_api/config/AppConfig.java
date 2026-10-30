package com.LosCiruelos.padel_club_api.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.LosCiruelos.padel_club_api.common.Fechas;
import com.LosCiruelos.padel_club_api.common.Reloj;

import tools.jackson.databind.JacksonModule;

@Configuration
@EnableCaching
@EnableAsync
public class AppConfig {

    /**
     * Reloj de la aplicación, en hora de Argentina ({@link Fechas#ZONA}) sin depender de la
     * zona del servidor. Los servicios lo usan en lugar de LocalDateTime.now() para poder testear
     * con horas fijas. Avanza de a milisegundos: en Windows el reloj tiene precisión de 100 ns.
     */
    @Bean
    public Reloj reloj() {
        return Reloj.delSistema();
    }

    /**
     * Hilos para enviar los avisos (WhatsApp / mail) sin demorar las respuestas. Pocos hilos y
     * cola acotada: si se llena, el aviso se envía en el hilo que lo publicó.
     */
    @Bean(name = "avisosExecutor")
    public Executor avisosExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("avisos-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /** Formato de fechas y horas del JSON (Spring Boot registra los módulos que son beans). */
    @Bean
    public JacksonModule fechasModule() {
        return Fechas.moduloJackson();
    }

    /** Caché en memoria para la configuración del club. */
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager(ConfiguracionCache.NOMBRE);
    }

    public static final class ConfiguracionCache {
        public static final String NOMBRE = "configuracion";

        private ConfiguracionCache() {
        }
    }
}
