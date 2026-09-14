package team.hotelchain.operations;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffOperationsController {
    private final StaffOperationsService operations;
    private final DailyOperationsService dailyOperations;
    private final StaffReservationQueryService reservationQuery;
    private final StaffRoomReassignmentService roomReassignment;
    private final RoomOperationsService roomOperations;

    public StaffOperationsController(StaffOperationsService operations, DailyOperationsService dailyOperations,
            StaffReservationQueryService reservationQuery,
            StaffRoomReassignmentService roomReassignment,
            RoomOperationsService roomOperations) {
        this.operations = operations;
        this.dailyOperations = dailyOperations;
        this.reservationQuery = reservationQuery;
        this.roomReassignment = roomReassignment;
        this.roomOperations = roomOperations;
    }

    @GetMapping("/hotels/{hotelId}/operations")
    public DailyOperationsView dailyOperations(
            @PathVariable UUID hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestHeader("X-Staff-Session") String token) {
        return dailyOperations.get(token, hotelId, date);
    }

    @GetMapping("/hotels/{hotelId}/room-operations")
    public RoomOperationsView roomOperations(
            @PathVariable UUID hotelId,
            @RequestHeader("X-Staff-Session") String token) {
        return roomOperations.list(token, hotelId);
    }

    @PostMapping("/rooms/{roomId}/operational-transitions")
    public RoomOperationalTransitionResult transitionRoom(
            @PathVariable UUID roomId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RoomOperationalTransitionRequest request) {
        return roomOperations.transition(token, roomId, idempotencyKey, request);
    }

    @GetMapping("/hotels/{hotelId}/reservations")
    public StaffReservationSearchView reservations(
            @PathVariable UUID hotelId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String status,
            @RequestHeader("X-Staff-Session") String token) {
        return reservationQuery.search(token, hotelId, date, query, status);
    }

    @PostMapping("/reservations/{reservationId}/assignments")
    public ResponseEntity<Void> assign(@PathVariable UUID reservationId, @Valid @RequestBody AssignmentRequest request,
            @RequestHeader("X-Staff-Session") String token) {
        operations.assign(token, reservationId, request.physicalRoomId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reservations/{reservationId}/assignable-rooms")
    public java.util.List<AssignableRoom> assignableRooms(@PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token) {
        return operations.assignableRooms(token, reservationId);
    }

    @GetMapping("/reservations/{reservationId}/room-reassignment-options")
    public RoomReassignmentOptions roomReassignmentOptions(
            @PathVariable UUID reservationId,
            @RequestHeader("X-Staff-Session") String token) {
        return roomReassignment.options(token, reservationId);
    }

    @PatchMapping("/reservations/{reservationId}/assignments/{currentPhysicalRoomId}")
    public RoomReassignmentResult reassignRoom(
            @PathVariable UUID reservationId,
            @PathVariable UUID currentPhysicalRoomId,
            @RequestHeader("X-Staff-Session") String token,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody RoomReassignmentRequest request) {
        return roomReassignment.reassign(
                token, reservationId, currentPhysicalRoomId, idempotencyKey, request);
    }

    @PostMapping("/reservations/{reservationId}/check-in")
    public ResponseEntity<Void> checkIn(@PathVariable UUID reservationId, @RequestHeader("X-Staff-Session") String token) {
        operations.checkIn(token, reservationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reservations/{reservationId}/no-show")
    public ResponseEntity<Void> markNoShow(@PathVariable UUID reservationId, @RequestHeader("X-Staff-Session") String token) {
        operations.markNoShow(token, reservationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reservations/{reservationId}/check-out")
    public ResponseEntity<Void> checkOut(@PathVariable UUID reservationId, @RequestHeader("X-Staff-Session") String token) {
        operations.checkOut(token, reservationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/rooms/{physicalRoomId}/housekeeping-complete")
    public ResponseEntity<Void> completeHousekeeping(@PathVariable UUID physicalRoomId,
            @RequestHeader("X-Staff-Session") String token) {
        operations.completeHousekeeping(token, physicalRoomId);
        return ResponseEntity.noContent().build();
    }

    public record AssignmentRequest(@NotNull UUID physicalRoomId) {}
}
