package com.LosCiruelos.padel_club_api.notificaciones.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

class EmailTest {

    @Test
    void gmailArmaYEnviaLosTresEmails() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        GmailEmailService gmail = new GmailEmailService(sender);
        ReflectionTestUtils.setField(gmail, "fromEmail", "club@test.com");

        gmail.sendVerificationEmail("a@b.com", Map.of("nombre", "Ana", "codigo", "123456"));
        gmail.sendResetPasswordEmail("a@b.com", Map.of("nombre", "Ana", "codigo", "123456"));
        gmail.sendBienvenidaEmail("a@b.com", Map.of("nombre", "Ana", "codigo", "123456", "password", "Tmp1!aaaaaaa"));

        verify(sender, times(3)).send(any(MimeMessage.class));
    }

    @Test
    void laFabricaDevuelveElProveedorConfigurado() {
        EmailService gmail = mock(EmailService.class);
        EmailServiceFactory factory = new EmailServiceFactory(Map.of("gmail", gmail));
        ReflectionTestUtils.setField(factory, "provider", "gmail");
        assertThat(factory.getService()).isSameAs(gmail);

        ReflectionTestUtils.setField(factory, "provider", "sendgrid");
        assertThatThrownBy(factory::getService).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void sinTransaccionElEmailSeEnviaEnElMomentoYLosErroresNoSePropagan() {
        AtomicInteger enviados = new AtomicInteger();
        EnvioPostCommit.ejecutar("prueba", enviados::incrementAndGet);
        EnvioPostCommit.ejecutar("falla", () -> {
            throw new MailSendException("smtp caído");
        });
        assertThat(enviados.get()).isEqualTo(1);
    }

    @Test
    void conTransaccionElEmailEsperaAlCommit() {
        AtomicInteger enviados = new AtomicInteger();
        TransactionSynchronizationManager.initSynchronization();
        try {
            EnvioPostCommit.ejecutar("prueba", enviados::incrementAndGet);
            assertThat(enviados.get()).isZero();

            List<TransactionSynchronization> sincronizaciones = TransactionSynchronizationManager.getSynchronizations();
            sincronizaciones.forEach(TransactionSynchronization::afterCommit);
            assertThat(enviados.get()).isEqualTo(1);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void errorDeSmtpSeTraduceAExcepcionDeEnvio() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(inv -> new MimeMessage((Session) null));
        doThrow(new MailSendException("smtp caído")).when(sender).send(any(MimeMessage.class));
        GmailEmailService gmail = new GmailEmailService(sender);
        ReflectionTestUtils.setField(gmail, "fromEmail", "club@test.com");

        assertThatThrownBy(() -> gmail.sendVerificationEmail("a@b.com", Map.of("nombre", "Ana", "codigo", "1")))
                .isInstanceOf(MailSendException.class);
    }
}
