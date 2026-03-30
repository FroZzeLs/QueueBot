package by.frozzel.backend.security;

import by.frozzel.backend.config.HibernateUtil;
import by.frozzel.backend.model.Student;
import by.frozzel.backend.model.WebSession;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class WebAuthFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "QB_SESSION";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String sessionId = readCookie(request, COOKIE_NAME);
            if (sessionId != null && !sessionId.isBlank()) {
                try (Session s = HibernateUtil.getSessionFactory().openSession()) {
                    WebSession webSession = s.get(WebSession.class, sessionId);
                    if (webSession != null) {
                        if (webSession.getExpiresAt() != null && webSession.getExpiresAt().isAfter(Instant.now())) {
                            Student st = webSession.getStudent();
                            if (st != null) AuthContext.setCurrentStudent(st);
                        } else {
                            // Expired: delete
                            Transaction tx = s.beginTransaction();
                            WebSession toDel = s.get(WebSession.class, sessionId);
                            if (toDel != null) s.remove(toDel);
                            tx.commit();
                        }
                    }
                } catch (Exception ignored) {
                    // don't break request if auth is broken; controllers can handle "unauthorized"
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            AuthContext.clear();
        }
    }

    private String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) return null;
        for (Cookie c : cookies) {
            if (name.equals(c.getName())) return c.getValue();
        }
        return null;
    }
}

