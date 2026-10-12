package com.seatreserve.reservation;

import com.seatreserve.common.NotRegisteredException;
import com.seatreserve.event.Event;
import com.seatreserve.registration.RegistrationService;
import com.seatreserve.session.Session;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import com.seatreserve.user.User;
import com.seatreserve.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    @Mock private SeatRepository seatRepository;
    @Mock private ReservationRepository reservationRepository;
    @Mock private UserRepository userRepository;
    @Mock private RegistrationService registrationService;
    @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private ReservationService reservationService;

    private Seat testSeat;
    private User testUser;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(reservationService, "holdTtlSeconds", 300L);

        testEvent = Event.builder().id(5L).name("DevCon").build();
        Session testSession = Session.builder().id(12L).event(testEvent).title("Keynote").build();
        testSeat = Seat.builder()
                .id(42L)
                .session(testSession)
                .section("A")
                .rowLabel("1")
                .seatNumber(1)
                .status(Seat.SeatStatus.AVAILABLE)
                .build();
        testUser = User.builder().id(99L).email("user@test.com").build();
    }

    @Test
    @DisplayName("holdSeat throws NotRegisteredException before acquiring row lock if user is not registered for event")
    void holdSeat_throwsNotRegisteredException_whenNotRegistered() {
        when(seatRepository.findEventIdBySeatId(42L)).thenReturn(Optional.of(5L));
        when(registrationService.isRegistered(5L, 99L)).thenReturn(false);

        assertThatThrownBy(() -> reservationService.holdSeat(42L, 99L))
                .isInstanceOf(NotRegisteredException.class)
                .hasMessageContaining("User 99 is not registered for event 5");

        // Core guarantee: row lock is NEVER acquired if business prerequisite fails
        verify(seatRepository, never()).findByIdForUpdate(anyLong());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    @DisplayName("holdSeat proceeds to acquire row lock and create reservation when user is registered")
    void holdSeat_succeeds_whenRegistered() {
        when(seatRepository.findEventIdBySeatId(42L)).thenReturn(Optional.of(5L));
        when(registrationService.isRegistered(5L, 99L)).thenReturn(true);
        when(seatRepository.findByIdForUpdate(42L)).thenReturn(Optional.of(testSeat));
        when(userRepository.findById(99L)).thenReturn(Optional.of(testUser));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation reservation = reservationService.holdSeat(42L, 99L);

        assertThat(reservation).isNotNull();
        assertThat(reservation.getStatus()).isEqualTo(Reservation.Status.PENDING);
        assertThat(testSeat.getStatus()).isEqualTo(Seat.SeatStatus.HELD);
        verify(seatRepository).findByIdForUpdate(42L);
        verify(reservationRepository).save(any(Reservation.class));
    }
}
