package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@Table(name = "swap_requests")
public class SwapRequest {
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

    // Для индивидуальной сдачи
    @ManyToOne
    @JoinColumn(name = "requester_student_id")
    private Student requesterStudent;

    @ManyToOne
    @JoinColumn(name = "target_student_id")
    private Student targetStudent;

    // Для бригадной сдачи (запрос между бригадами)
    @ManyToOne
    @JoinColumn(name = "requester_brigade_id")
    private Brigade requesterBrigade;

    @ManyToOne
    @JoinColumn(name = "target_brigade_id")
    private Brigade targetBrigade;

    // Кому отправляем уведомление в ТГ (внутри целевой бригады / это targetStudent для индивидуальной)
    @ManyToOne
    @JoinColumn(name = "target_notify_student_id", nullable = false)
    private Student targetNotifyStudent;

    // От кого (в ТГ) должна прийти уведомление создателю запроса (в индивидуальном = requesterStudent)
    @ManyToOne
    @JoinColumn(name = "requester_notify_student_id", nullable = false)
    private Student requesterNotifyStudent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

