package br.com.clicklance;

import br.com.clicklance.exception.ApiException;
import br.com.clicklance.service.RecoveryMailService;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.*;

class RecoveryMailServiceTest {
    @Test void configuredAdapterDeliversOnlyToControlledLocalSmtpCapture() throws Exception {
        try (var server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             var pool = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setSoTimeout(5000);
            var received = pool.submit(() -> {
                try (var socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    var input = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    var output = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                    var message = new StringBuilder();
                    output.println("220 localhost test-only");
                    for (String line; (line = input.readLine()) != null;) {
                        if (line.startsWith("DATA")) {
                            output.println("354 send content");
                            while ((line = input.readLine()) != null && !line.equals(".")) message.append(line).append('\n');
                            output.println("250 captured");
                        } else if (line.startsWith("QUIT")) {
                            output.println("221 done");
                            break;
                        } else output.println("250 localhost");
                    }
                    return message.toString();
                }
            });
            var sender = new JavaMailSenderImpl();
            sender.setHost(server.getInetAddress().getHostAddress());
            sender.setPort(server.getLocalPort());
            sender.getJavaMailProperties().setProperty("mail.smtp.connectiontimeout", "3000");
            sender.getJavaMailProperties().setProperty("mail.smtp.timeout", "3000");
            var beans = new DefaultListableBeanFactory();
            beans.registerSingleton("testSmtp", sender);
            var mail = new RecoveryMailService(beans.getBeanProvider(JavaMailSender.class), "noreply@example.invalid", "127.0.0.1");
            String token = "fixture-only-" + "x".repeat(30);
            mail.send("player@example.invalid", token);
            assertThat(received.get(5, TimeUnit.SECONDS)).contains("To: player@example.invalid", token, "15 minutos");
        }
    }

    @Test void missingSmtpAndTransportFailureBecomeSafeDependencyErrors() {
        var beans = new DefaultListableBeanFactory();
        var missing = new RecoveryMailService(beans.getBeanProvider(JavaMailSender.class), "noreply@example.invalid", "127.0.0.1");
        assertThatThrownBy(missing::requireConfigured).isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).status()).isEqualTo(503));
        var sender = org.mockito.Mockito.mock(JavaMailSender.class);
        org.mockito.Mockito.doThrow(new org.springframework.mail.MailSendException("sensitive diagnostic"))
                .when(sender).send(org.mockito.ArgumentMatchers.any(org.springframework.mail.SimpleMailMessage.class));
        beans.registerSingleton("broken", sender);
        var blankHost = new RecoveryMailService(beans.getBeanProvider(JavaMailSender.class), "noreply@example.invalid", "");
        assertThatThrownBy(blankHost::requireConfigured).isInstanceOf(ApiException.class);
        var configured = new RecoveryMailService(beans.getBeanProvider(JavaMailSender.class), "noreply@example.invalid", "127.0.0.1");
        assertThatThrownBy(() -> configured.send("player@example.invalid", "test-token"))
                .isInstanceOf(ApiException.class).satisfies(error -> {
                    assertThat(((ApiException) error).status()).isEqualTo(503);
                    assertThat(error.getMessage()).doesNotContain("sensitive", "player@example.invalid", "test-token");
                });
    }
}
