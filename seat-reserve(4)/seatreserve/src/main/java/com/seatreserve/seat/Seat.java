package com.seatreserve.seat;

import com.seatreserve.session.Session;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "seat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @Column(nullable = false)
    private String section;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    // Kept alongside pessimistic locking (not as the concurrency mechanism
    // itself) so every seat mutation is still audit-trailed and so the
    // column exists if the locking strategy is ever revisited.
    @Version
    private Long version;

    public enum SeatStatus {
        AVAILABLE, HELD, BOOKED
    }
}
