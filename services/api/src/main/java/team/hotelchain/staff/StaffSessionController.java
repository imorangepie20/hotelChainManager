package team.hotelchain.staff;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/staff")
public class StaffSessionController {
    private final StaffAccessService access;

    public StaffSessionController(StaffAccessService access) {
        this.access = access;
    }

    @PostMapping("/sessions")
    public ResponseEntity<StaffSessionView> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(access.login(request.email(), request.password()));
    }

    @GetMapping("/me")
    public StaffPrincipal current(@RequestHeader("X-Staff-Session") String token) {
        return access.current(token);
    }

    @DeleteMapping("/sessions/current")
    public ResponseEntity<Void> logout(@RequestHeader(value = "X-Staff-Session", required = false) String token) {
        access.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/hotels/{hotelId}/access")
    public StaffPrincipal verifyHotelAccess(@PathVariable java.util.UUID hotelId,
            @RequestHeader("X-Staff-Session") String token) {
        return access.requireHotel(token, hotelId);
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }
}
