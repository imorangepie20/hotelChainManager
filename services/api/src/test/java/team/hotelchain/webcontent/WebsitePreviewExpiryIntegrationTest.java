package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
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
@Transactional
class WebsitePreviewExpiryIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebsitePageService pages;
    @Autowired WebsitePreviewGrantService previewGrants;
    @Autowired WebApplicationContext context;

    @Test
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
                .andExpect(status().isOk());

        // TTL 1분 + 여유 5초를 실제 시간으로 기다린다.
        AtomicReference<Integer> finalStatus = new AtomicReference<>();
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(1).plusSeconds(5).toMillis();
        while (System.currentTimeMillis() < deadline) {
            int status = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .get("/api/website/pages/preview")
                            .param("path", grant.previewPath())
                            .param("locale", "ko")
                            .header("X-Website-Preview", grant.previewToken()))
                    .andReturn().getResponse().getStatus();
            if (status == 410) {
                finalStatus.set(status);
                break;
            }
            Thread.sleep(2_000);
        }

        assertThat(finalStatus.get())
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
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role)
                values (?, ?, ?, ?, 'HQ_EDITOR')
                """, staffId, "expiry-editor@example.com", "만료 검증 편집자", encoder.encode("editor-password"));
        return staffAccess.login("expiry-editor@example.com", "editor-password");
    }
}
