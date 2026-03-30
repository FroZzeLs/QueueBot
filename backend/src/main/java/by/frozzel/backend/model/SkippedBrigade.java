package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "skipped_brigades",
        uniqueConstraints = @UniqueConstraint(name = "uk_skipped_brigade", columnNames = {"brigade_id", "subject_id", "queue_kind", "subgroup_num"}))
public class SkippedBrigade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "brigade_id", nullable = false)
    private Brigade brigade;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Enumerated(EnumType.STRING)
    @Column(name = "queue_kind", nullable = false)
    private QueueKind queueKind;

    @Column(name = "subgroup_num")
    private Integer subgroupNum;

    public SkippedBrigade(Brigade brigade, Subject subject, QueueKind queueKind, Integer subgroupNum) {
        this.brigade = brigade;
        this.subject = subject;
        this.queueKind = queueKind;
        this.subgroupNum = subgroupNum;
    }
}

