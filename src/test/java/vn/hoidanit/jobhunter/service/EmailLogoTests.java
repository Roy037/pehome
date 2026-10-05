package vn.hoidanit.jobhunter.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

/** The message that really leaves the server: where the logo comes from, and that a text part is always there. */
class EmailLogoTests {

    private static String sent(String backendUrl) throws Exception {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        EmailService service = new EmailService(sender, engine);
        ReflectionTestUtils.setField(service, "from", "");
        ReflectionTestUtils.setField(service, "frontendUrl", "http://localhost:3000");
        ReflectionTestUtils.setField(service, "backendUrl", backendUrl);
        service.sendNotice("a@example.com", "Xin chào", EmailService.Notice.of("Chào", "An", "Nội dung.", "Mở", "/job", "Lý do."));
        ArgumentCaptor<MimeMessage> message = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(message.capture());
        message.getValue().saveChanges();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        message.getValue().writeTo(out);
        return out.toString("UTF-8");
    }

    @Test
    void localDevelopmentTravelsWithTheImage() throws Exception {
        String mime = sent("http://localhost:8080");
        assertTrue(mime.contains("Content-ID: <logo>") && mime.contains("image/png"), "the logo is attached inline");
        assertTrue(mime.contains("src=3D\"cid:logo\"") || mime.contains("src=\"cid:logo\""));
        assertTrue(mime.contains("text/plain") && mime.contains("text/html"), "both a text and an html part");
    }

    @Test
    void anHttpsServerLinksTheLogoInstead() throws Exception {
        String mime = sent("https://api.itjobs.vn/");
        assertFalse(mime.contains("Content-ID: <logo>"), "nothing is attached");
        assertTrue(mime.contains("https://api.itjobs.vn/mail/itjobs-logo.png"));
        assertTrue(mime.contains("text/plain"));
    }
}
