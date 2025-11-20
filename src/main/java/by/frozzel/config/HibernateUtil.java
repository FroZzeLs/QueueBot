package by.frozzel.config;

import by.frozzel.model.*;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;

import java.util.Properties;

public class HibernateUtil {
    private static final SessionFactory sessionFactory = buildSessionFactory();

    private static SessionFactory buildSessionFactory() {
        try {
            // 1. Загружаем настройки из hibernate.cfg.xml
            Configuration configuration = new Configuration().configure();

            // 2. ЯВНО проверяем, передал ли Docker новые настройки через -D...
            // Если передал — перезаписываем то, что было в файле.

            String url = System.getProperty("jakarta.persistence.jdbc.url");
            if (url != null && !url.isEmpty()) {
                configuration.setProperty("jakarta.persistence.jdbc.url", url);
                System.out.println("Hibernate Config: URL overridden to " + url);
            }

            String user = System.getProperty("jakarta.persistence.jdbc.user");
            if (user != null && !user.isEmpty()) {
                configuration.setProperty("jakarta.persistence.jdbc.user", user);
            }

            String pass = System.getProperty("jakarta.persistence.jdbc.password");
            if (pass != null && !pass.isEmpty()) {
                configuration.setProperty("jakarta.persistence.jdbc.password", pass);
            }

            // 3. Добавляем классы (на случай, если они не подтянулись из xml)
            configuration.addAnnotatedClass(Student.class);
            configuration.addAnnotatedClass(Subject.class);
            configuration.addAnnotatedClass(QueueState.class);
            configuration.addAnnotatedClass(SwapRequest.class);
            configuration.addAnnotatedClass(TemporarySwap.class);
            configuration.addAnnotatedClass(SkippedStudent.class);

            return configuration.buildSessionFactory();
        } catch (Throwable ex) {
            System.err.println("Initial SessionFactory creation failed." + ex);
            throw new ExceptionInInitializerError(ex);
        }
    }

    public static SessionFactory getSessionFactory() {
        return sessionFactory;
    }
}