package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "skipped_students",
        uniqueConstraints = @UniqueConstraint(name = "uk_skipped_student", columnNames = {"student_id", "subject_id", "queue_kind", "subgroup_num"}))
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

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_kind", nullable = false)
    private QueueKind queueKind;

    @Column(name = "subgroup_num")
    private Integer subgroupNum;

    public SkippedStudent(Student student, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        this.student = student;
        this.subject = subject;
        this.queueKind = queueKind;
        this.subgroupNum = subgroupNum;
    }
}

