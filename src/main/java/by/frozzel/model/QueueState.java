package by.frozzel.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "queue_states")
public class QueueState {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "is_group_queue", nullable = false)
    private boolean isGroupQueue; // true = вся группа, false = подгрупповая

    @Column(name = "subgroup_num")
    private Integer subgroupNum; // 0 если общая, 1 или 2 если подгруппа

    @ManyToOne
    @JoinColumn(name = "last_passed_student_id")
    private Student lastPassedStudent;
}