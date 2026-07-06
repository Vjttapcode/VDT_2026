package com.vdt.notification_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {
    private final JavaMailSender mailSender;
    private final String from;

    public EmailService(JavaMailSender mailSender, @Value("${alert.from:no-reply@vdt.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    //gửi mail cảnh báo
    public void sendExpiryAlert(String to, Long docId, String level, long daysLeft, String alertType) {
        String color  = daysLeft <= 0 ? "#d32f2f" : (daysLeft <= 7 ? "#f57c00" : "#fbc02d");
        String status = daysLeft <= 0 ? "ĐÃ HẾT HẠN" : "còn " + daysLeft + " ngày";
        String subject = "[" + alertType + "] Văn bản #" + docId + " (" + level + ") - " + status;
        String html = """
            <div style="font-family:Arial,sans-serif">
              <h2 style="color:%s">Cảnh báo văn bản sắp hết hạn</h2>
              <p>Văn bản <b>#%d</b> cấp <b>%s</b>: <b style="color:%s">%s</b>.</p>
              <p>Vui lòng gia hạn hoặc xử lý kịp thời.</p>
            </div>""".formatted(color, docId, level, color, status);
        send(to, subject, html);
    }

    //gửi mail duyệt
    public void sendApproval(String to, String eventType, Long docId, String docTitle, String reason) {
        String subject = switch (eventType) {
            case "APPROVAL_REQUEST" -> "[Cần duyệt] " + docTitle;
            case "APPROVED"         -> "[Đã duyệt] " + docTitle;
            case "REJECTED"         -> "[Bị từ chối] " + docTitle;
            case "EFFECTIVE"        -> "[Có hiệu lực] " + docTitle;
            default                 -> "[Thông báo] " + docTitle;
        };
        String body = "<div style='font-family:Arial'><p>Văn bản <b>#" + docId + " - " + docTitle + "</b></p>"
            + (reason != null ? "<p>Lý do: " + reason + "</p>" : "") + "</div>";
        send(to, subject, body);
    }

    private void send(String to, String subject, String html) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setFrom(from);
            h.setTo(to);
            h.setSubject(subject);
            h.setText(html, true);
            mailSender.send(msg);
        } catch(MessagingException e) {
            throw new RuntimeException("Gửi mail thất bại tới " + to + ": " + e.getMessage(), e);
        }
    }
}
