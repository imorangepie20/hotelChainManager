package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

/**
 * 미리보기 grant가 실제 시간이 흐른 뒤 만료하는지 확인한다.
 * 클럭을 조작하는 기존 테스트와 달리 시스템 클럭을 그대로 두고
 * TTL(1분)보다 길게 대기한 뒤 410로 바뀌는지 본다.
 * TTL이 10분으로 고정되면 10분을 기다려야 하므로 1분으로 줄여서 검증한다.
 */
@SpringBootTest(properties = { "website.preview.require-https=false", "website.preview.ttl-minutes=1" })
class WebsitePreviewExpiryIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebsitePageService pages;
    @Autowired WebsitePreviewGrantService previewGrants;
    @Autowired WebApplicationContext context;
    private UUID seededStaffId;

    @AfterEach
    void cleanUp() {
        if (seededStaffId == null) return;
        jdbc.update("delete from website_preview_grant where issued_by = ?", seededStaffId);
        jdbc.update("delete from staff_session where staff_id = ?", seededStaffId);
        jdbc.update("delete from staff_member where id = ?", seededStaffId);
    }

    @Test
    @Timeout(90)
    void expiresAfterTheConfiguredMinutesInRealTime() throws Exception {
        StaffSessionView editor = seedEditor();
        WebsitePageDocument home = pages.homeDraft(editor.token());
        WebsitePreviewGrantResponse grant = previewGrants.issue(
                editor.token(), home.id(), new WebsitePreviewGrantRequest("ko", home.draftVersion()));

        // 발급 직후에는 저장 초안이 표시된다.
        var mvc = MockMvcBuilders.webAppContextSetup(context).build();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/website/pages/preview")
                        .param("path", grant.previewPath())
                        .param("locale", "ko")
                        .header("X-Website-Preview", grant.previewToken()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Website-Preview-Expires-At", grant.expiresAt().toString()));

        // 임의의 65초 sleep 대신 서버가 발급한 만료 시각까지 실제로 기다린다.
        long millisUntilExpiry = Duration.between(Instant.now(), grant.expiresAt()).toMillis();
        if (millisUntilExpiry > 0) {
            Thread.sleep(millisUntilExpiry);
        }

        Integer finalStatus = null;
        Instant deadline = grant.expiresAt().plusSeconds(15);
        do {
            int currentStatus = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/website/pages/preview")
                            .param("path", grant.previewPath())
                            .param("locale", "ko")
                            .header("X-Website-Preview", grant.previewToken()))
                    .andReturn().getResponse().getStatus();
            if (currentStatus == 410) {
                finalStatus = currentStatus;
                break;
            }
            assertThat(currentStatus).as("만료 전에는 200만 반환해야 한다").isEqualTo(200);
            if (Instant.now().isBefore(deadline)) {
                Thread.sleep(250);
            }
        } while (Instant.now().isBefore(deadline));

        assertThat(finalStatus)
                .as("1분 TTL 이후에는 410로 바뀌어야 한다")
                .isEqualTo(410);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/website/pages/preview")
                        .param("path", grant.previewPath())
                        .param("locale", "ko")
                        .header("X-Website-Preview", grant.previewToken()))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("WEBSITE_PREVIEW_UNAVAILABLE"));
    }

    private StaffSessionView seedEditor() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        UUID staffId = UUID.randomUUID();
        seededStaffId = staffId;
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role)
                values (?, ?, ?, ?, 'HQ_EDITOR')
                """, staffId, "expiry-editor-" + staffId + "@example.com", "만료 검증 편집자", encoder.encode("editor-password"));
        return staffAccess.login("expiry-editor-" + staffId + "@example.com", "editor-password");
    }
}
