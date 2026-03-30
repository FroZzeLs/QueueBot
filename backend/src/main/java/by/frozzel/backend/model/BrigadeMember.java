package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "brigade_members",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_brigade_member_student", columnNames = {"brigade_id", "student_id"})
        })
public class BrigadeMember {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "brigade_id", nullable = false)
    private Brigade brigade;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
}

