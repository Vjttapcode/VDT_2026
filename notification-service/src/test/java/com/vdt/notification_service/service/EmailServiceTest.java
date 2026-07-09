package com.vdt.notification_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

class EmailServiceTest {

    private JavaMailSender mockSender() {
        JavaMailSender sender = mock(JavaMailSender.class);
        when(sender.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
        return sender;
    }

    private MimeMessage sent(JavaMailSender sender) {
        ArgumentCaptor<MimeMessage> cap = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(cap.capture());
        return cap.getValue();
    }

    @Test
    void redirect_batMoiMailVeDiaChiTest_giuRecipientGocTrongSubject() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "bot@gmail.com", "VDT Hệ thống văn bản", "tester@gmail.com");

        svc.sendExpiryAlert("user@congty.vn", 42L, "COMPANY", 7, "WARNING");

        MimeMessage msg = sent(sender);
        assertThat(msg.getAllRecipients()).hasSize(1);
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("tester@gmail.com");
        assertThat(msg.getSubject()).startsWith("[→ user@congty.vn]");
    }

    @Test
    void khongRedirect_giuNguyenRecipientGoc() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "bot@gmail.com", "VDT Hệ thống văn bản", "");

        svc.sendExpiryAlert("user@congty.vn", 42L, "COMPANY", 7, "WARNING");

        MimeMessage msg = sent(sender);
        assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("user@congty.vn");
        assertThat(msg.getSubject()).doesNotContain("[→");
    }

    @Test
    void from_coDisplayName() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "bot@gmail.com", "VDT Hệ thống văn bản", "");

        svc.sendApproval("user@congty.vn", "APPROVED", 1L, "Quy chế A", null);

        InternetAddress from = (InternetAddress) sent(sender).getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("bot@gmail.com");
        assertThat(from.getPersonal()).isEqualTo("VDT Hệ thống văn bản");
    }

    @Test
    void from_khongCoDisplayName_khiFromNameRong() throws Exception {
        JavaMailSender sender = mockSender();
        EmailService svc = new EmailService(sender, "no-reply@vdt.local", "", "");

        svc.sendApproval("user@congty.vn", "APPROVED", 1L, "Quy chế A", null);

        InternetAddress from = (InternetAddress) sent(sender).getFrom()[0];
        assertThat(from.getAddress()).isEqualTo("no-reply@vdt.local");
        assertThat(from.getPersonal()).isNull();
    }
}
