package br.com.clicklance.service;

import br.com.clicklance.exception.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/** SMTP configurável; desenvolvimento pode apontar para um capturador SMTP local. */
@Service
public class RecoveryMailService {
    private final ObjectProvider<JavaMailSender> senders;
    private final String from;
    private final String host;
    public RecoveryMailService(ObjectProvider<JavaMailSender> senders,
                               @Value("${clicklance.recovery.mail-from:}") String from,
                               @Value("${spring.mail.host:}") String host) {
        this.senders = senders; this.from = from; this.host = host;
    }

    public void requireConfigured() {
        if (senders.getIfAvailable() == null || from.isBlank() || host.isBlank()) throw new ApiException(503, "DEPENDENCY_UNAVAILABLE");
    }

    public void send(String recipient, String token) {
        requireConfigured();
        var message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(recipient);
        message.setSubject("ClickLance - recuperacao de conta");
        message.setText("Use este token para redefinir sua senha nos proximos 15 minutos: " + token
                + "\nSe voce nao solicitou a recuperacao, ignore esta mensagem.");
        try { senders.getObject().send(message); }
        catch (MailException ignored) { throw new ApiException(503, "DEPENDENCY_UNAVAILABLE"); }
    }
}
