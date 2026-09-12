package team.hotelchain.staff;

import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class StaffDevAccountInitializer {
    private static final UUID SOKCHO = UUID.fromString("11000000-0000-0000-0000-000000000001");
    private static final UUID SEORAKSAN = UUID.fromString("11000000-0000-0000-0000-000000000002");
    private static final UUID JEJU = UUID.fromString("11000000-0000-0000-0000-000000000003");

    private final JdbcTemplate jdbc;
    private final boolean enabled;
    private final List<DevAccount> accounts;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public StaffDevAccountInitializer(JdbcTemplate jdbc,
            @Value("${staff.dev.enabled:false}") boolean enabled,
            @Value("${staff.dev.hq-password:}") String hqPassword,
            @Value("${staff.dev.sokcho-password:}") String sokchoPassword,
            @Value("${staff.dev.seoraksan-password:}") String seoraksanPassword,
            @Value("${staff.dev.jeju-password:}") String jejuPassword) {
        this.jdbc = jdbc;
        this.enabled = enabled;
        this.accounts = List.of(
                new DevAccount("hq@hotel-chain.local", "본사 관리자", "HQ_ADMIN", null, hqPassword),
                new DevAccount("editor@hotel-chain.local", "영문 편집자", "HQ_EDITOR", null, hqPassword),
                new DevAccount("publisher@hotel-chain.local", "영문 승인자", "HQ_PUBLISHER", null, hqPassword),
                new DevAccount("sokcho@hotel-chain.local", "속초 지점 직원", "BRANCH_STAFF", SOKCHO, sokchoPassword),
                new DevAccount("seoraksan@hotel-chain.local", "설악산 지점 직원", "BRANCH_STAFF", SEORAKSAN, seoraksanPassword),
                new DevAccount("jeju@hotel-chain.local", "제주 지점 직원", "BRANCH_STAFF", JEJU, jejuPassword));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void initialize() {
        if (!enabled) {
            return;
        }
        if (accounts.stream().anyMatch(account -> account.password().isBlank())) {
            throw new IllegalStateException("직원 개발 계정 비밀번호 환경 변수를 모두 설정해야 합니다.");
        }
        for (DevAccount account : accounts) {
            jdbc.update("""
                    INSERT INTO staff_member (id, email, display_name, password_hash, role, hotel_id)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT (email) DO UPDATE SET display_name = EXCLUDED.display_name,
                        password_hash = EXCLUDED.password_hash, role = EXCLUDED.role, hotel_id = EXCLUDED.hotel_id
                    """, UUID.randomUUID(), account.email(), account.displayName(), passwordEncoder.encode(account.password()),
                    account.role(), account.hotelId());
        }
    }

    private record DevAccount(String email, String displayName, String role, UUID hotelId, String password) {
    }
}
