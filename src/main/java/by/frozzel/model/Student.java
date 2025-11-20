package by.frozzel.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "students")
public class Student {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String fio;

    @Column(name = "subgroup_num", nullable = false)
    private int subgroup; // 1 or 2

    @Column(name = "telegram_tag")
    private String telegramTag;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "is_admin", nullable = false)
    private boolean isAdmin = false;

    // НОВОЕ ПОЛЕ
    @Column(name = "is_super_admin", nullable = false)
    private boolean isSuperAdmin = false;

    public Student(String fio, int subgroup, String telegramTag) {
        this.fio = fio;
        this.subgroup = subgroup;
        this.telegramTag = telegramTag;
    }
}