package com.LosCiruelos.padel_club_api.notificaciones.email;

import java.util.Map;

public interface EmailService {
    void sendVerificationEmail(String to, Map<String, Object> data);
    void sendResetPasswordEmail(String to, Map<String, Object> data);
    void sendBienvenidaEmail(String to, Map<String, Object> data);
    /** Aviso de reservas (al ADMIN o al cliente) para quien eligió recibirlos por mail. */
    void sendAviso(String to, String asunto, String texto);
}
