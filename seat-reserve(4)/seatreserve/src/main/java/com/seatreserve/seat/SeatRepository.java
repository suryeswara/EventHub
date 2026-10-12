package com.seatreserve.seat;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {

    /**
     * THE core concurrency-critical query in this project.
     *
     * PESSIMISTIC_WRITE issues a `SELECT ... FOR UPDATE` under the hood.
     * If two transactions call this for the same seatId concurrently, the
     * second transaction blocks at the database level until the first
     * commits or rolls back — it does NOT proceed with stale data.
     *
     * This is intentionally chosen over optimistic locking (@Version alone)
     * because for a hold operation we want the second request to WAIT and
     * then see the correct up-to-date status (and fail cleanly with 409),
     * rather than racing ahead and failing only at commit time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Seat s where s.id = :seatId")
    Optional<Seat> findByIdForUpdate(@Param("seatId") Long seatId);

    @Query("select s.session.event.id from Seat s where s.id = :seatId")
    Optional<Long> findEventIdBySeatId(@Param("seatId") Long seatId);

    List<Seat> findBySessionIdAndStatus(Long sessionId, Seat.SeatStatus status);

    long countBySessionIdAndStatus(Long sessionId, Seat.SeatStatus status);

    long countBySessionId(Long sessionId);

    boolean existsBySessionId(Long sessionId);

    @Query("SELECT se.session.id, COUNT(se) FROM Seat se WHERE se.session.event.id = :eventId AND se.status = :status GROUP BY se.session.id")
    List<Object[]> countByStatusGroupedBySession(@Param("eventId") Long eventId, @Param("status") Seat.SeatStatus status);

    @Query("SELECT se.session.id, COUNT(se) FROM Seat se WHERE se.session.event.id = :eventId GROUP BY se.session.id")
    List<Object[]> countTotalGroupedBySession(@Param("eventId") Long eventId);

    List<Seat> findBySessionId(Long sessionId);

    List<Seat> findBySessionIdOrderBySectionAscRowLabelAscSeatNumberAsc(Long sessionId);

    List<Seat> findBySession_Event_Id(Long eventId);

    /**
     * Stopgap bridge for backward compatibility with the existing SeatController
     * (/api/v1/events/{eventId}/seats).
     *
     * WARNING: This flattens seats across all sessions under the event into a single list.
     * Since seat coordinates (section, row, seatNumber) are scoped uniquely per session
     * rather than per event, this list may contain duplicate visual positions from
     * different sessions without session context.
     *
     * Scheduled for replacement in Phase 2 with session-scoped endpoints
     * (/api/v1/sessions/{sessionId}/seats) or a session-aware DTO.
     */
    @Deprecated
    default List<Seat> findByEventId(Long eventId) {
        return findBySession_Event_Id(eventId);
    }
}
