package team.hotelchain.staff;

import java.util.UUID;

public record StaffPrincipal(UUID id, String email, String displayName, String role, UUID hotelId) {
}
