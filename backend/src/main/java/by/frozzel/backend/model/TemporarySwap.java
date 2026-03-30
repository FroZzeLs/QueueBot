package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "temporary_swaps")
public class TemporarySwap {
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
    private Integer subgroupNum;

    @ManyToOne
    @JoinColumn(name = "student1_id")
    private Student student1;

    @ManyToOne
    @JoinColumn(name = "student2_id")
    private Student student2;

    @ManyToOne
    @JoinColumn(name = "brigade1_id")
    private Brigade brigade1;

    @ManyToOne
    @JoinColumn(name = "brigade2_id")
    private Brigade brigade2;
}

