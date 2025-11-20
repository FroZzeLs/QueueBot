package by.frozzel.model;


import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "temp_swaps")
public class TemporarySwap {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(nullable = false)
    private boolean isGroupQueue;

    @ManyToOne
    @JoinColumn(name = "student1_id")
    private Student student1;

    @ManyToOne
    @JoinColumn(name = "student2_id")
    private Student student2;
}