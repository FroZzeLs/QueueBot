package by.frozzel.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "skipped_students")
public class SkippedStudent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "is_group_queue", nullable = false)
    private boolean isGroupQueue;

    public SkippedStudent(Student student, Subject subject, boolean isGroupQueue) {
        this.student = student;
        this.subject = subject;
        this.isGroupQueue = isGroupQueue;
    }
}