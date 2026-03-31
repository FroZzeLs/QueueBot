package by.frozzel.backend.web;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.Student;
import by.frozzel.backend.model.WebSession;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tg-auth")
public class TgAuthController {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public static class TelegramLoginRequest {
        public Long chatId;
        public String authToken;
    }

    @PostMapping("/login")
    public ResponseEntity<?> telegramLogin(@RequestBody TelegramLoginRequest req) {
        if (req.authToken == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_TOKEN"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            // Look up student by auth token
            Student st = s.createQuery("FROM Student WHERE telegramAuthToken = :token", Student.class)
                    .setParameter("token", req.authToken)
                    .setMaxResults(1)
                    .uniqueResult();

            if (st == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "STUDENT_NOT_FOUND"));
            }

            // Verify token matches
            if (!req.authToken.equals(st.getTelegramAuthToken())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "INVALID_TOKEN"));
            }

            // Check expiration
            if (st.getTelegramAuthExpires() != null && st.getTelegramAuthExpires().isBefore(Instant.now())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "TOKEN_EXPIRED"));
            }

            // Create session
            String sessionId = UUID.randomUUID().toString();
            WebSession session = new WebSession();
            session.setSessionId(sessionId);
            session.setStudent(st);
            session.setExpiresAt(Instant.now().plus(2, ChronoUnit.HOURS));

            // Invalidate token after successful login (one-time use)
            st.setTelegramAuthToken(null);
            st.setTelegramAuthExpires(null);

            Transaction tx = s.beginTransaction();
            s.persist(session);
            s.merge(st);
            tx.commit();

            // Set session cookie
            Map<String, Object> response = new HashMap<>();
            response.put("ok", true);
            response.put("sessionId", sessionId);
            response.put("student", Map.of(
                    "id", st.getId(),
                    "fio", st.getFio(),
                    "subgroup", st.getSubgroup(),
                    "telegramTag", st.getTelegramTag(),
                    "role", calcRole(st)
            ));

            return ResponseEntity.ok(response);
        }
    }

    @PostMapping("/invalidate-token")
    public ResponseEntity<?> invalidateToken(@RequestBody RequestAuthTokenRequest req) {
        if (req.chatId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_CHAT_ID"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE chatId = :chatId", Student.class)
                    .setParameter("chatId", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();

            if (st == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "STUDENT_NOT_FOUND"));
            }

            // Invalidate token
            st.setTelegramAuthToken(null);
            st.setTelegramAuthExpires(null);

            Transaction tx = s.beginTransaction();
            s.merge(st);
            tx.commit();

            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class RequestAuthTokenRequest {
        public Long chatId;
    }

    @PostMapping("/request-token")
    public ResponseEntity<?> requestAuthToken(@RequestBody RequestAuthTokenRequest req) {
        if (req.chatId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "MISSING_CHAT_ID"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE chatId = :chatId", Student.class)
                    .setParameter("chatId", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();

            if (st == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "STUDENT_NOT_FOUND"));
            }

            // Invalidate any existing token first (protect against spam/replay)
            st.setTelegramAuthToken(null);
            st.setTelegramAuthExpires(null);

            // Generate new token
            String token = UUID.randomUUID().toString().substring(0, 16);
            Instant expires = Instant.now().plus(15, ChronoUnit.MINUTES);

            Transaction tx = s.beginTransaction();
            st.setTelegramAuthToken(token);
            st.setTelegramAuthExpires(expires);
            s.merge(st);
            tx.commit();

            return ResponseEntity.ok(Map.of(
                    "ok", true,
                    "token", token,
                    "expiresAt", expires.toString()
            ));
        }
    }

    private static String calcRole(Student st) {
        if (st.isSuperAdmin()) return "SUPER_ADMIN";
        if (st.isAdmin()) return "ADMIN";
        return "USER";
    }

    public static class ResetPasswordRequest {
        public Long chatId;
        public String newPassword;
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest req) {
        if (req.chatId == null || req.newPassword == null || req.newPassword.length() < 6) {
            return ResponseEntity.badRequest().body(Map.of("error", "INVALID_PARAMETERS"));
        }

        try (Session s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE chatId = :chatId", Student.class)
                    .setParameter("chatId", req.chatId)
                    .setMaxResults(1)
                    .uniqueResult();

            if (st == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "STUDENT_NOT_FOUND"));
            }

            // Update password
            String hash = encoder.encode(req.newPassword);
            st.setPasswordHash(hash);

            Transaction tx = s.beginTransaction();
            s.merge(st);
            tx.commit();

            return ResponseEntity.ok(Map.of("ok", true, "message", "Password reset successfully"));
        }
    }
}
