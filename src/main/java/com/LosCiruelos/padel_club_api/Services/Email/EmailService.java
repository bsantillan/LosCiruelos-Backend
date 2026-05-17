package com.LosCiruelos.padel_club_api.Services.Email;

import java.util.Map;

public interface EmailService {
    void sendVerificationEmail(String to, Map<String, Object> data);
    void sendResetPasswordEmail(String to, Map<String, Object> data);
    void sendBienvenidaEmail(String to, Map<String, Object> data);
}
