package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;
import team.hotelchain.reservation.BusinessConflictException;

@SpringBootTest
@Transactional
class WebContentIntegrationTest {
    private static final UUID HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000001");

    @Autowired JdbcTemplate jdbc;
    @Autowired StaffAccessService staffAccess;
    @Autowired WebContentService content;

    @BeforeEach
    void seed() {
        jdbc.update("insert into hotel values (?, ?, ?, ?)", HOTEL, "CMS 테스트 호텔", "속초", "Asia/Seoul");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_ADMIN')",
                UUID.randomUUID(), "cms-hq@example.com", "CMS 본사 관리자", encoder.encode("hq-password"));
    }

    @Test
    void exposesOnlyThePublishedDocumentAfterHeadOfficePublishesTheDraft() {
        StaffSessionView session = staffAccess.login("cms-hq@example.com", "hq-password");
        Map<String, Object> draft = validContent("새로운 속초의 하루");

        content.saveDraft(session.token(), HOTEL, 1, draft);

        assertThat(content.published(HOTEL)).isEmpty();

        content.publish(session.token(), HOTEL, 2, 1);

        assertThat(content.published(HOTEL)).containsExactlyInAnyOrderEntriesOf(draft);
    }

    @Test
    void rejectsAnInvalidDocumentAndAStaleDraftVersion() {
        StaffSessionView session = staffAccess.login("cms-hq@example.com", "hq-password");

        Map<String, Object> blankTitle = new HashMap<>(validContent("유효한 제목"));
        blankTitle.put("title", "  ");
        assertThatThrownBy(() -> content.saveDraft(session.token(), HOTEL, 1, blankTitle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");

        assertThatThrownBy(() -> content.saveDraft(session.token(), HOTEL, 1,
                Map.of("heroAssetId", WebsiteMediaService.BUNDLED_ASSET_ID.toString(), "title", "제목만 있는 문서")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heroImage");

        content.saveDraft(session.token(), HOTEL, 1, validContent("첫 번째 편집"));

        assertThatThrownBy(() -> content.saveDraft(session.token(), HOTEL, 1, validContent("오래된 편집")))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("최신 초안");
        assertThatThrownBy(() -> content.publish(session.token(), HOTEL, 1, 1))
                .isInstanceOf(BusinessConflictException.class)
                .hasMessageContaining("최신 초안");
    }

    @Test
    void rejectsIncompleteSeoMetadataWhenItIsProvided() {
        StaffSessionView session = staffAccess.login("cms-hq@example.com", "hq-password");
        Map<String, Object> incompleteSeo = new HashMap<>(validContent("유효한 제목"));
        incompleteSeo.put("seo", Map.of("title", "  ", "description", "지점 검색 결과 설명"));

        assertThatThrownBy(() -> content.saveDraft(session.token(), HOTEL, 1, incompleteSeo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seo.title");
    }

    @Test
    void rejectsSeoDescriptionLongerThanTheSearchSnippetLimit() {
        StaffSessionView session = staffAccess.login("cms-hq@example.com", "hq-password");
        Map<String, Object> overlongSeo = new HashMap<>(validContent("유효한 제목"));
        overlongSeo.put("seo", Map.of("title", "속초 오션 호텔 | STAY HANEUL", "description", "가".repeat(161)));

        assertThatThrownBy(() -> content.saveDraft(session.token(), HOTEL, 1, overlongSeo))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seo.description")
                .hasMessageContaining("160자");
    }

    private Map<String, Object> validContent(String title) {
        return Map.of(
                "heroAssetId", WebsiteMediaService.BUNDLED_ASSET_ID.toString(),
                "heroImage", "/images/sokcho-coast-hero.png",
                "heroAlt", "속초 해안 호텔",
                "eyebrow", "SOKCHO · EAST SEA",
                "title", title,
                "description", "동해와 설악을 담은 휴식",
                "arrival", Map.of("address", "가상 해안로 186", "checkInOut", "15:00 / 11:00", "highlight", "바다 곁의 하루"),
                "experiences", List.of(Map.of("category", "ROOM", "title", "수평선 객실", "description", "바다를 담은 객실")),
                "offers", List.of(Map.of("title", "푸른 아침", "detail", "조식 포함", "bookingPeriod", "2026.09.01 ~ 2026.12.31", "stayPeriod", "2026.09.15 ~ 2027.02.28")));
    }
}
