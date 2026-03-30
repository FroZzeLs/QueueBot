package by.frozzel.backend;

import by.frozzel.backend.config.HibernateUtil;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class BackendMain {
    public static void main(String[] args) {
        // Force SessionFactory init early (fail-fast in container logs).
        HibernateUtil.getSessionFactory();
        SpringApplication.run(BackendMain.class, args);
    }
}

