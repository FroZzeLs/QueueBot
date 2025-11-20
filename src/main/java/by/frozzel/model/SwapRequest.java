package by.frozzel.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@NoArgsConstructor
@Table(name = "swap_requests")
public class SwapRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Student requester;

    @ManyToOne
    private Student target;

    @ManyToOne
    private Subject subject;

    private boolean isGroupQueue;
}