package by.frozzel.backend.config;

import by.frozzel.backend.model.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

public class HibernateUtil {
    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        try {
            // hibernate.cfg.xml берём из ресурсов; JDBC URL можно переопределить системными свойствами.
            Configuration configuration = new Configuration().configure();

            String url = System.getProperty("jakarta.persistence.jdbc.url");
            if (url != null && !url.isBlank()) {
                configuration.setProperty("jakarta.persistence.jdbc.url", url);
            }
            String user = System.getProperty("jakarta.persistence.jdbc.user");
            if (user != null && !user.isBlank()) {
                configuration.setProperty("jakarta.persistence.jdbc.user", user);
            }
            String pass = System.getProperty("jakarta.persistence.jdbc.password");
            if (pass != null && !pass.isBlank()) {
                configuration.setProperty("jakarta.persistence.jdbc.password", pass);
            }

            configuration.addAnnotatedClass(Student.class);
            configuration.addAnnotatedClass(Subject.class);
            configuration.addAnnotatedClass(Brigade.class);
            configuration.addAnnotatedClass(BrigadeMember.class);
            configuration.addAnnotatedClass(QueueState.class);
            configuration.addAnnotatedClass(SkippedStudent.class);
            configuration.addAnnotatedClass(SkippedBrigade.class);
            configuration.addAnnotatedClass(TemporarySwap.class);
            configuration.addAnnotatedClass(SwapRequest.class);
            configuration.addAnnotatedClass(WebSession.class);

            return configuration.buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("Initial SessionFactory creation failed: " + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}

