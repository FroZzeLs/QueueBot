package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "queue_states",
        uniqueConstraints = @UniqueConstraint(name = "uk_queue_state", columnNames = {"subject_id", "queue_kind", "subgroup_num"}))
public class QueueState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_kind", nullable = false)
    private QueueKind queueKind;

    @Column(name = "subgroup_num")
    private Integer subgroupNum; // null for COMMON

    @ManyToOne
    @JoinColumn(name = "last_passed_student_id")
    private Student lastPassedStudent;

    @ManyToOne
    @JoinColumn(name = "last_passed_brigade_id")
    private Brigade lastPassedBrigade;
}

