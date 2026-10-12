package com.seatreserve.registration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AttendeeRegistrationRepository extends JpaRepository<AttendeeRegistration, Long> {
    Optional<AttendeeRegistration> findByEventIdAndUserId(Long eventId, Long userId);
}
