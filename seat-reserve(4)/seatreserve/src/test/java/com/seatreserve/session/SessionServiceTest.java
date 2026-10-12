package com.seatreserve.session;

import com.seatreserve.event.Event;
import com.seatreserve.event.EventRepository;
import com.seatreserve.seat.Seat;
import com.seatreserve.seat.SeatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static com.seatreserve.session.SessionDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private EventRepository eventRepository;
    @Mock private SeatRepository seatRepository;

    @InjectMocks
    private SessionService sessionService;

    private Session testSession;
    private Event testEvent;

    @BeforeEach
    void setUp() {
        com.seatreserve.user.User organizer = com.seatreserve.user.User.builder().id(999L).build();
        testEvent = Event.builder().id(1L).organizer(organizer).name("DevCon 2026").build();
        testSession = Session.builder()
                .id(10L)
                .event(testEvent)
                .title("Kubernetes Workshop")
                .capacity(30)
                .startTime(Instant.now())
                .endTime(Instant.now().plusSeconds(7200))
                .build();
    }

    @Test
    @DisplayName("createSession throws UnauthorizedOrganizerException when caller is not the organizer")
    void createSession_throwsAccessDenied_whenNotOrganizer() {
        when(eventRepository.findById(1L)).thenReturn(Optional.of(testEvent));

        CreateSessionRequest request = new CreateSessionRequest(
                "Workshop", "Desc", Instant.now(), Instant.now().plusSeconds(3600), 50
        );

        assertThatThrownBy(() -> sessionService.createSession(1L, request, 123L))
                .isInstanceOf(com.seatreserve.common.UnauthorizedOrganizerException.class)
                .hasMessageContaining("User 123 is not the organizer of event 1");
    }

    @Test
    @DisplayName("generateSeats throws UnauthorizedOrganizerException when caller is not the organizer")
    void generateSeats_throwsAccessDenied_whenNotOrganizer() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(testSession));

        GenerateSeatsRequest request = new GenerateSeatsRequest(List.of(new SectionSpec("A", 3, 10)));

        assertThatThrownBy(() -> sessionService.generateSeats(10L, request, 123L))
                .isInstanceOf(com.seatreserve.common.UnauthorizedOrganizerException.class)
                .hasMessageContaining("User 123 is not the organizer of event 1");
    }

    @Test
    @DisplayName("generateSeats generates seats accurately when section layout equals declared capacity")
    void generateSeats_succeeds_whenCapacityMatches() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(testSession));
        when(seatRepository.existsBySessionId(10L)).thenReturn(false);
        when(seatRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // 3 rows x 10 seats = 30 total seats (matches capacity: 30)
        GenerateSeatsRequest request = new GenerateSeatsRequest(List.of(
                new SectionSpec("Lab-A", 3, 10)
        ));

        GenerateSeatsResponse response = sessionService.generateSeats(10L, request);

        assertThat(response.sessionId()).isEqualTo(10L);
        assertThat(response.totalSeatsGenerated()).isEqualTo(30);
        assertThat(response.seats()).hasSize(30);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Seat>> captor = ArgumentCaptor.forClass(List.class);
        verify(seatRepository).saveAll(captor.capture());
        List<Seat> saved = captor.getValue();
        assertThat(saved).hasSize(30);
        assertThat(saved.getFirst().getSection()).isEqualTo("Lab-A");
        assertThat(saved.getFirst().getRowLabel()).isEqualTo("1");
        assertThat(saved.getFirst().getSeatNumber()).isEqualTo(1);
        assertThat(saved.getFirst().getStatus()).isEqualTo(Seat.SeatStatus.AVAILABLE);
    }

    @Test
    @DisplayName("generateSeats throws IllegalArgumentException when section layout mismatches declared capacity")
    void generateSeats_rejects_whenCapacityMismatches() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(testSession));
        when(seatRepository.existsBySessionId(10L)).thenReturn(false);

        // 2 rows x 10 seats = 20 total seats (capacity is 30)
        GenerateSeatsRequest request = new GenerateSeatsRequest(List.of(
                new SectionSpec("Lab-A", 2, 10)
        ));

        assertThatThrownBy(() -> sessionService.generateSeats(10L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Generated seat count (20) does not match session capacity (30)");

        verify(seatRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("generateSeats throws IllegalStateException when seats are already generated for session")
    void generateSeats_rejects_whenSeatsAlreadyExist() {
        when(sessionRepository.findById(10L)).thenReturn(Optional.of(testSession));
        when(seatRepository.existsBySessionId(10L)).thenReturn(true);

        GenerateSeatsRequest request = new GenerateSeatsRequest(List.of(
                new SectionSpec("Lab-A", 3, 10)
        ));

        assertThatThrownBy(() -> sessionService.generateSeats(10L, request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Seats have already been generated for session 10");

        verify(seatRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("listSessionsForEvent executes grouped queries without N+1 and correctly populates counts and seatsGenerated flag")
    void listSessionsForEvent_usesGroupedQueries_andMapsAccurately() {
        Long eventId = 1L;
        Session session1 = Session.builder().id(101L).title("Keynote").capacity(100).build();
        Session session2 = Session.builder().id(102L).title("Workshop").capacity(30).build();
        Session session3 = Session.builder().id(103L).title("Unseated Talk").capacity(50).build();

        when(eventRepository.existsById(eventId)).thenReturn(true);
        when(sessionRepository.findByEventId(eventId)).thenReturn(List.of(session1, session2, session3));

        // Mock grouped available counts (session 101 has 95 available, 102 has 20 available)
        when(seatRepository.countByStatusGroupedBySession(eventId, Seat.SeatStatus.AVAILABLE))
                .thenReturn(List.of(
                        new Object[]{101L, 95L},
                        new Object[]{102L, 20L}
                ));

        // Mock grouped total counts (session 101 has 100 seats, 102 has 30 seats, 103 has 0 generated)
        when(seatRepository.countTotalGroupedBySession(eventId))
                .thenReturn(List.of(
                        new Object[]{101L, 100L},
                        new Object[]{102L, 30L}
                ));

        List<SessionResponse> responses = sessionService.listSessionsForEvent(eventId);

        assertThat(responses).hasSize(3);

        SessionResponse r1 = responses.get(0);
        assertThat(r1.id()).isEqualTo(101L);
        assertThat(r1.totalSeats()).isEqualTo(100L);
        assertThat(r1.availableSeats()).isEqualTo(95L);
        assertThat(r1.seatsGenerated()).isTrue();

        SessionResponse r2 = responses.get(1);
        assertThat(r2.id()).isEqualTo(102L);
        assertThat(r2.totalSeats()).isEqualTo(30L);
        assertThat(r2.availableSeats()).isEqualTo(20L);
        assertThat(r2.seatsGenerated()).isTrue();

        // Session 3 has no seats generated yet
        SessionResponse r3 = responses.get(2);
        assertThat(r3.id()).isEqualTo(103L);
        assertThat(r3.totalSeats()).isEqualTo(0L);
        assertThat(r3.availableSeats()).isEqualTo(0L);
        assertThat(r3.seatsGenerated()).isFalse();

        // Verify only 2 grouped queries were executed across all 3 sessions (no N+1!)
        verify(seatRepository, times(1)).countByStatusGroupedBySession(eq(eventId), eq(Seat.SeatStatus.AVAILABLE));
        verify(seatRepository, times(1)).countTotalGroupedBySession(eq(eventId));
        verify(seatRepository, never()).countBySessionIdAndStatus(anyLong(), any());
        verify(seatRepository, never()).countBySessionId(anyLong());
    }
}
