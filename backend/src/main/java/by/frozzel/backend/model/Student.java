package by.frozzel.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "students")
public class Student {
    @jakarta.persistence.Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fio;

    // 1 or 2
    @Column(name = "subgroup_num", nullable = false)
    private int subgroup;

    @Column(name = "telegram_tag", unique = true)
    private String telegramTag;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin = false;

    @Column(name = "is_super_admin", nullable = false)
    private boolean isSuperAdmin = false;

    public Student(String fio, int subgroup, String telegramTag) {
        this.fio = fio;
        this.subgroup = subgroup;
        this.telegramTag = telegramTag;
    }
}

