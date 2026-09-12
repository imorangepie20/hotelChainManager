package team.hotelchain.staff;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffAccessService {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final Duration sessionTtl;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public StaffAccessService(JdbcTemplate jdbc, Clock clock,
            @Value("${staff.session-ttl:12h}") Duration sessionTtl) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.sessionTtl = sessionTtl;
    }

    @Transactional
    public StaffSessionView login(String email, String password) {
        StaffMember member = jdbc.query("""
                SELECT id, email, display_name, password_hash, role, hotel_id
                  FROM staff_member WHERE email = ?
                """, rs -> rs.next() ? new StaffMember(
                        rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("password_hash"), rs.getString("role"), rs.getObject("hotel_id", UUID.class)) : null,
                email.trim().toLowerCase());
        if (member == null || !passwordEncoder.matches(password, member.passwordHash())) {
            throw new StaffAuthenticationException();
        }

        String token = newToken();
        jdbc.update("insert into staff_session (token_hash, staff_id, expires_at) values (?, ?, ?)",
                hash(token), member.id(), Timestamp.from(clock.instant().plus(sessionTtl)));
        return new StaffSessionView(token, member.principal());
    }

    public StaffPrincipal current(String token) {
        if (token == null || token.isBlank()) {
            throw new StaffAuthenticationException();
        }
        StaffPrincipal principal = jdbc.query("""
                SELECT m.id, m.email, m.display_name, m.role, m.hotel_id
                  FROM staff_session s JOIN staff_member m ON m.id = s.staff_id
                 WHERE s.token_hash = ? AND s.expires_at > ?
                """, rs -> rs.next() ? new StaffPrincipal(
                        rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("display_name"),
                        rs.getString("role"), rs.getObject("hotel_id", UUID.class)) : null,
                hash(token), Timestamp.from(clock.instant()));
        if (principal == null) {
            throw new StaffAuthenticationException();
        }
        return principal;
    }

    public StaffPrincipal requireHeadquarters(String token) {
        StaffPrincipal principal = current(token);
        if ("HQ_ADMIN".equals(principal.role())) return principal;
        throw new StaffAccessDeniedException();
    }

    public StaffPrincipal requireContentStaff(String token) {
        StaffPrincipal principal = current(token);
        if (java.util.Set.of("HQ_ADMIN", "HQ_EDITOR", "HQ_PUBLISHER").contains(principal.role())) return principal;
        throw new StaffAccessDeniedException();
    }

    public StaffPrincipal requireContentEditor(String token) {
        StaffPrincipal principal = current(token);
        if (java.util.Set.of("HQ_ADMIN", "HQ_EDITOR").contains(principal.role())) return principal;
        throw new StaffAccessDeniedException();
    }

    public StaffPrincipal requireContentPublisher(String token) {
        StaffPrincipal principal = current(token);
        if (java.util.Set.of("HQ_ADMIN", "HQ_PUBLISHER").contains(principal.role())) return principal;
        throw new StaffAccessDeniedException();
    }

    public StaffPrincipal requireHotel(String token, UUID hotelId) {
        StaffPrincipal principal = current(token);
        if ("HQ_ADMIN".equals(principal.role()) || hotelId.equals(principal.hotelId())) {
            return principal;
        }
        throw new StaffAccessDeniedException();
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            jdbc.update("delete from staff_session where token_hash = ?", hash(token));
        }
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record StaffMember(UUID id, String email, String displayName, String passwordHash, String role, UUID hotelId) {
        StaffPrincipal principal() {
            return new StaffPrincipal(id, email, displayName, role, hotelId);
        }
    }
}
