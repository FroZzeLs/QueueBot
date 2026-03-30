package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Data
@NoArgsConstructor
@Table(name = "web_sessions")
public class WebSession {
    @Id
    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @ManyToOne
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;
}

