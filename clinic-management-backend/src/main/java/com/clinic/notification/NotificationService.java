package com.clinic.notification;

import static com.clinic.notification.NotificationDtos.*;

import com.clinic.security.FirebasePrincipal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class NotificationService {
    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(FirebasePrincipal principal) {
        UUID userId = userId(principal);
        return jdbc.query("""
                SELECT id, clinic_id, category, title, body, created_at, read_at
                FROM notifications WHERE user_id = ? ORDER BY created_at DESC LIMIT 100
                """, (rs, row) -> new NotificationDto(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                rs.getString("category"), rs.getString("title"), rs.getString("body"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("read_at", OffsetDateTime.class)), userId);
    }

    @Transactional
    public NotificationDto markRead(FirebasePrincipal principal, UUID notificationId) {
        UUID userId = userId(principal);
        jdbc.update("UPDATE notifications SET read_at = COALESCE(read_at, now()) WHERE id = ? AND user_id = ?", notificationId, userId);
        return jdbc.query("""
                SELECT id, clinic_id, category, title, body, created_at, read_at
                FROM notifications WHERE id = ? AND user_id = ?
                """, (rs, row) -> new NotificationDto(rs.getObject("id", UUID.class), rs.getObject("clinic_id", UUID.class),
                rs.getString("category"), rs.getString("title"), rs.getString("body"),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("read_at", OffsetDateTime.class)),
                notificationId, userId).stream().findFirst().orElseThrow(() -> notFound("Notification not found"));
    }

    @Transactional
    public void markAllRead(FirebasePrincipal principal) {
        jdbc.update("UPDATE notifications SET read_at = now() WHERE user_id = ? AND read_at IS NULL", userId(principal));
    }

    @Transactional
    public PreferenceDto preferences(FirebasePrincipal principal) {
        UUID userId = userId(principal);
        jdbc.update("INSERT INTO notification_preferences (user_id) VALUES (?) ON CONFLICT (user_id) DO NOTHING", userId);
        return jdbc.queryForObject("""
                SELECT email_enabled, sms_enabled, whatsapp_enabled FROM notification_preferences WHERE user_id = ?
                """, (rs, row) -> new PreferenceDto(rs.getBoolean("email_enabled"), rs.getBoolean("sms_enabled"),
                rs.getBoolean("whatsapp_enabled")), userId);
    }

    @Transactional
    public PreferenceDto updatePreferences(FirebasePrincipal principal, PreferenceRequest request) {
        UUID userId = userId(principal);
        jdbc.update("""
                INSERT INTO notification_preferences (user_id, email_enabled, sms_enabled, whatsapp_enabled, updated_at)
                VALUES (?, ?, ?, ?, now())
                ON CONFLICT (user_id) DO UPDATE SET email_enabled = EXCLUDED.email_enabled,
                    sms_enabled = EXCLUDED.sms_enabled, whatsapp_enabled = EXCLUDED.whatsapp_enabled, updated_at = now()
                """, userId, request.emailEnabled(), request.smsEnabled(), request.whatsappEnabled());
        return new PreferenceDto(request.emailEnabled(), request.smsEnabled(), request.whatsappEnabled());
    }

    private UUID userId(FirebasePrincipal principal) {
        if (principal == null || principal.uid() == null || principal.uid().isBlank()) throw new AccessDeniedException("Sign in is required");
        try { return jdbc.queryForObject("SELECT id FROM users WHERE firebase_uid = ? AND status = 'ACTIVE'", UUID.class, principal.uid()); }
        catch (EmptyResultDataAccessException exception) { throw new AccessDeniedException("Active account not found"); }
    }

    private ResponseStatusException notFound(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
}
