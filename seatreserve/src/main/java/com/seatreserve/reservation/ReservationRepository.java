package com.seatreserve.reservation;

import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

    // Used by the scheduled sweep job (Day 8-9) to find holds that
    // have outlived their TTL and need to be released.
    List<Reservation> findByStatusAndExpiresAtBefore(Reservation.Status status, Instant cutoff);

    List<Reservation> findByUserIdOrderByHeldAtDesc(Long userId);

    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.seat s " +
           "JOIN FETCH s.session sess " +
           "JOIN FETCH sess.event e " +
           "JOIN FETCH r.user u " +
           "WHERE r.id = :id")
    Optional<Reservation> findByIdWithDetails(@Param("id") Long id);

    @Query("SELECT r FROM Reservation r " +
           "JOIN FETCH r.seat s " +
           "JOIN FETCH s.session sess " +
           "JOIN FETCH sess.event e " +
           "JOIN FETCH r.user u " +
           "WHERE r.user.id = :userId " +
           "ORDER BY r.heldAt DESC")
    List<Reservation> findByUserIdWithDetails(@Param("userId") Long userId);
}
