package by.frozzel.backend.web;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.Student;
import by.frozzel.backend.model.WebSession;
import by.frozzel.backend.security.AuthContext;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @GetMapping("/bootstrap-status")
    public Map<String, Object> bootstrapStatus() {
        try (var s = HibernateUtil.getSessionFactory().openSession()) {
            Student any = s.createQuery("FROM Student WHERE isSuperAdmin = true", Student.class)
                    .setMaxResults(1)
                    .uniqueResult();
            Map<String, Object> res = new HashMap<>();
            res.put("hasSuperAdmin", any != null);
            return res;
        }
    }

    public static class VerifySuperKeyRequest {
        public String superKey;
    }

    @PostMapping("/bootstrap/verify-super-key")
    public ResponseEntity<?> verifySuperKey(@RequestBody @Valid VerifySuperKeyRequest req) {
        String expected = System.getenv("SUPER_ADMIN_KEY");
        if (expected == null || expected.isBlank()) expected = "";
        if (req.superKey != null && req.superKey.equals(expected)) {
            return ResponseEntity.ok(Map.of("ok", true));
        }
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "error", "INVALID_SUPER_KEY"));
    }

    public static class RegisterSuperAdminRequest {
        public String superKey;
        public String telegramTag;
        public String password;
        public boolean rememberDevice;
    }

    @PostMapping("/bootstrap/register-super-admin")
    public ResponseEntity<?> registerSuperAdmin(@RequestBody @Valid RegisterSuperAdminRequest req, HttpServletResponse response) {
        String expected = System.getenv("SUPER_ADMIN_KEY");
        if (expected == null) expected = "";
        if (req.superKey == null || req.superKey.isBlank() || !req.superKey.equals(expected)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("ok", false, "error", "INVALID_SUPER_KEY"));
        }
        String tag = normalizeTag(req.telegramTag);
        if (tag == null || tag.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_TAG"));
        }
        try (var s = HibernateUtil.getSessionFactory().openSession()) {
            var tx = s.beginTransaction();

            Student st = s.createQuery("FROM Student WHERE telegramTag = :tag", Student.class)
                    .setParameter("tag", tag)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                st = new Student("Суперадмин " + tag, 1, tag);
                s.persist(st);
            }
            st.setAdmin(true);
            st.setSuperAdmin(true);
            if (req.password != null && !req.password.isBlank()) {
                st.setPasswordHash(encoder.encode(req.password));
            }
            s.merge(st);

            WebSession session = new WebSession();
            session.setSessionId(UUID.randomUUID().toString());
            session.setStudent(st);
            session.setExpiresAt(Instant.now().plusSeconds(req.rememberDevice ? Duration.ofDays(30).getSeconds() : Duration.ofHours(2).getSeconds()));
            s.persist(session);

            tx.commit();

            setSessionCookie(response, session.getSessionId(), req.rememberDevice);
            return ResponseEntity.ok(Map.of("ok", true));
        }
    }

    public static class LoginRequest {
        public String telegramTag;
        public String password;
        public boolean rememberDevice;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid LoginRequest req, HttpServletResponse response) {
        String tag = normalizeTag(req.telegramTag);
        if (tag == null || tag.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_TAG"));

        try (var s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE telegramTag = :tag", Student.class)
                    .setParameter("tag", tag)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("state", "NOT_REGISTERED"));
            }
            if (st.getPasswordHash() == null || st.getPasswordHash().isBlank()) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED)
                        .body(Map.of("state", "SET_PASSWORD_REQUIRED"));
            }
            if (req.password == null || req.password.isBlank()) {
                return ResponseEntity.status(HttpStatus.PRECONDITION_REQUIRED).body(Map.of("state", "PASSWORD_REQUIRED"));
            }
            if (!encoder.matches(req.password, st.getPasswordHash())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("state", "INVALID_PASSWORD"));
            }

            WebSession session = new WebSession();
            session.setSessionId(UUID.randomUUID().toString());
            session.setStudent(st);
            session.setExpiresAt(Instant.now().plusSeconds(req.rememberDevice ? Duration.ofDays(30).getSeconds() : Duration.ofHours(2).getSeconds()));
            var tx = s.beginTransaction();
            s.persist(session);
            tx.commit();

            setSessionCookie(response, session.getSessionId(), req.rememberDevice);
            return ResponseEntity.ok(Map.of("state", "OK", "role", calcRole(st)));
        }
    }

    public static class SetPasswordRequest {
        public String telegramTag;
        public String newPassword;
        public boolean rememberDevice;
    }

    @PostMapping("/set-password")
    public ResponseEntity<?> setPassword(@RequestBody @Valid SetPasswordRequest req, HttpServletResponse response) {
        String tag = normalizeTag(req.telegramTag);
        if (tag == null || tag.isBlank()) return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_TAG"));

        try (var s = HibernateUtil.getSessionFactory().openSession()) {
            Student st = s.createQuery("FROM Student WHERE telegramTag = :tag", Student.class)
                    .setParameter("tag", tag)
                    .setMaxResults(1)
                    .uniqueResult();
            if (st == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("state", "NOT_REGISTERED"));
            }
            if (req.newPassword == null || req.newPassword.isBlank()) {
                return ResponseEntity.badRequest().body(Map.of("error", "EMPTY_PASSWORD"));
            }
            var tx = s.beginTransaction();
            st.setPasswordHash(encoder.encode(req.newPassword));
            s.merge(st);

            WebSession session = new WebSession();
            session.setSessionId(UUID.randomUUID().toString());
            session.setStudent(st);
            session.setExpiresAt(Instant.now().plusSeconds(req.rememberDevice ? Duration.ofDays(30).getSeconds() : Duration.ofHours(2).getSeconds()));
            s.persist(session);
            tx.commit();

            setSessionCookie(response, session.getSessionId(), req.rememberDevice);
            return ResponseEntity.ok(Map.of("state", "OK", "role", calcRole(st)));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me() {
        Student st = AuthContext.getCurrentStudent();
        if (st == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "UNAUTHORIZED"));
        return ResponseEntity.ok(Map.of(
                "telegramTag", st.getTelegramTag(),
                "fio", st.getFio(),
                "subgroup", st.getSubgroup(),
                "role", calcRole(st),
                "isPasswordSet", st.getPasswordHash() != null && !st.getPasswordHash().isBlank()
        ));
    }

    private static void setSessionCookie(HttpServletResponse response, String sessionId, boolean rememberDevice) {
        int maxAgeSeconds = rememberDevice ? (int) Duration.ofDays(30).getSeconds() : (int) Duration.ofHours(2).getSeconds();
        CookieBuilder.apply(response, "QB_SESSION", sessionId, maxAgeSeconds);
    }

    private static String normalizeTag(String raw) {
        if (raw == null) return null;
        String t = raw.trim();
        if (t.startsWith("@")) t = t.substring(1);
        return t;
    }

    private static String calcRole(Student st) {
        if (st.isSuperAdmin()) return "SUPER_ADMIN";
        if (st.isAdmin()) return "ADMIN";
        return "USER";
    }
}

