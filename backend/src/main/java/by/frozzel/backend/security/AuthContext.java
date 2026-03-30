package by.frozzel.backend.security;

import by.frozzel.backend.model.Student;

public final class AuthContext {
    private static final ThreadLocal<Student> current = new ThreadLocal<>();

    private AuthContext() {}

    public static void setCurrentStudent(Student student) {
        current.set(student);
    }

    public static Student getCurrentStudent() {
        return current.get();
    }

    public static void clear() {
        current.remove();
    }
}

