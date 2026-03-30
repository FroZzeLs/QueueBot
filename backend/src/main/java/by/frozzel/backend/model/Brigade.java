package by.frozzel.backend.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

@Entity
@Data
@NoArgsConstructor
@Table(name = "brigades")
public class Brigade {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    // Стабильный порядок внутри очереди (если displayName совпали).
    @Column(name = "sort_key", nullable = false)
    private String sortKey;

    public Brigade(Subject subject, String displayName, String sortKey) {
        this.subject = subject;
        this.displayName = displayName;
        this.sortKey = sortKey;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Brigade other)) return false;
        if (id == null || other.id == null) return false;
        return Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }
}

