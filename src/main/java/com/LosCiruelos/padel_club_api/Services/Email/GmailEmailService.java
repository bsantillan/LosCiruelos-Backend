package com.LosCiruelos.padel_club_api.Services.Email;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service("gmail")
@RequiredArgsConstructor
public class GmailEmailService implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${email.from}")
    private String fromEmail;

    @Override
    public void sendVerificationEmail(String to, Map<String, Object> data) {
        String nombre = (String) data.get("nombre");
        String codigo = (String) data.get("codigo");

        String html = """
            <div style="font-family: Arial, sans-serif; max-width: 500px; margin: auto; padding: 24px;">
                <h2>Hola, %s</h2>
                <p>Tu código de verificación es:</p>
                <h1 style="letter-spacing: 8px; color: #4CAF50;">%s</h1>
                <p>Expira en 24 horas.</p>
            </div>
            """.formatted(nombre, codigo);

        enviarEmail(to, "Verificá tu cuenta - Los Ciruelos Padel", html);
    }

    @Override
    public void sendResetPasswordEmail(String to, Map<String, Object> data) {
        String nombre = (String) data.get("nombre");
        String codigo = (String) data.get("codigo");

        String html = """
            <div style="font-family: Arial, sans-serif; max-width: 500px; margin: auto; padding: 24px;">
                <h2>Hola, %s</h2>
                <p>Tu código para restablecer la contraseña es:</p>
                <h1 style="letter-spacing: 8px; color: #f44336;">%s</h1>
                <p>Expira en 15 minutos.</p>
                <p>Si no lo pediste, ignorá este mail.</p>
            </div>
            """.formatted(nombre, codigo);

        enviarEmail(to, "Restablecer contraseña - Los Ciruelos Padel", html);
    }

    private void enviarEmail(String to, String subject, String html) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true); // true = es HTML
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new RuntimeException("Error enviando email", e);
        }
    }
}
