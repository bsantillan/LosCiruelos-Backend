package com.LosCiruelos.padel_club_api.notificaciones.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.LosCiruelos.padel_club_api.common.Reloj;
import com.LosCiruelos.padel_club_api.common.exception.ReservaException;
import com.LosCiruelos.padel_club_api.mapper.NotificacionesMapper;
import com.LosCiruelos.padel_club_api.notificaciones.aviso.TipoAviso;
import com.LosCiruelos.padel_club_api.notificaciones.dto.response.MensajesWhatsAppResponse;
import com.LosCiruelos.padel_club_api.notificaciones.entity.MensajeWhatsApp;
import com.LosCiruelos.padel_club_api.notificaciones.repository.MensajeWhatsAppRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cuenta los WhatsApp que manda el club (cada uno lo cobra Meta): se registra cada uno que sale bien y
 * el ADMIN ve cuántos se mandaron en un período.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MensajesWhatsAppService {

    private final MensajeWhatsAppRepository repository;
    private final NotificacionesMapper notificacionesMapper;
    private final Reloj reloj;

    /**
     * Un código del celular que salió bien. En su propia transacción: se llama después del commit de
     * la operación, y un error al registrarlo no afecta al envío.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarCodigo(boolean paraCliente) {
        guardar(MensajeWhatsApp.codigo(paraCliente, reloj.ahora()));
    }

    /** Un aviso que salió bien por WhatsApp (en su propia transacción, como {@link #registrarCodigo}). */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrarAviso(TipoAviso aviso, boolean paraCliente) {
        guardar(MensajeWhatsApp.aviso(aviso, paraCliente, reloj.ahora()));
    }

    private void guardar(MensajeWhatsApp mensaje) {
        try {
            repository.save(mensaje);
        } catch (RuntimeException e) {
            log.warn("No se pudo registrar el WhatsApp mandado ({} {}): {}", mensaje.getMotivo(), mensaje.getAviso(),
                    e.getMessage());
        }
    }

    /**
     * Los mandados entre desde y hasta (inclusive). Sin fechas: del 1 del mes en curso a hoy.
     *
     * @throws ReservaException (400) si desde es posterior a hasta
     */
    @Transactional(readOnly = true)
    public MensajesWhatsAppResponse resumen(LocalDate desde, LocalDate hasta) {
        LocalDate hoy = reloj.hoy();
        LocalDate fin = hasta != null ? hasta : hoy;
        LocalDate inicio = desde != null ? desde : (hasta != null ? fin : hoy).withDayOfMonth(1);
        if (inicio.isAfter(fin)) {
            throw new ReservaException("La fecha desde no puede ser posterior a hasta.");
        }
        return notificacionesMapper.aMensajesWhatsApp(inicio, fin,
                repository.contar(inicio.atStartOfDay(), fin.plusDays(1).atStartOfDay()));
    }
}
