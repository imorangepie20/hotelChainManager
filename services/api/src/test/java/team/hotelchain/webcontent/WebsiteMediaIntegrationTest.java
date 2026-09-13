package team.hotelchain.webcontent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.transaction.TestTransaction;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAccessService;
import team.hotelchain.staff.StaffSessionView;

@SpringBootTest
@Transactional
class WebsiteMediaIntegrationTest {
    private static final UUID BUNDLED_ASSET = UUID.fromString("14000000-0000-0000-0000-000000000001");
    private static final UUID BRANCH_HOTEL = UUID.fromString("13000000-0000-0000-0000-000000000031");

    @org.springframework.beans.factory.annotation.Autowired JdbcTemplate jdbc;
    @org.springframework.beans.factory.annotation.Autowired StaffAccessService staffAccess;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaService media;
    @org.springframework.beans.factory.annotation.Autowired WebsiteMediaDraftReplacementService replacements;
    @org.springframework.beans.factory.annotation.Autowired PublicWebsiteMediaController publicMedia;
    @org.springframework.beans.factory.annotation.Autowired WebsitePageService pages;
    @org.springframework.beans.factory.annotation.Autowired WebsiteTranslationService translations;
    @org.springframework.beans.factory.annotation.Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void seed() throws IOException {
        removeCommittedPermanentDeleteFixtures();
        deleteStorage();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into hotel values (?, ?, ?, ?)", BRANCH_HOTEL, "미디어 테스트 호텔", "속초", "Asia/Seoul");
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_ADMIN')",
                UUID.randomUUID(), "media-hq@example.com", "미디어 본사 관리자", encoder.encode("hq-password"));
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role, hotel_id) values (?, ?, ?, ?, 'BRANCH_STAFF', ?)",
                UUID.randomUUID(), "media-branch@example.com", "미디어 지점 직원", encoder.encode("branch-password"), BRANCH_HOTEL);
    }

    @Test
    void exposesTheBundledCatalogOnlyToHeadquarters() {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("media-branch@example.com", "branch-password");

        assertThat(media.catalog(headquarters.token())).anySatisfy(asset -> {
            assertThat(asset.id()).isEqualTo(BUNDLED_ASSET);
            assertThat(asset.displayName()).isEqualTo("속초 해안 메인 이미지");
            assertThat(asset.deliveryUrl()).isEqualTo("/images/sokcho-coast-hero.png");
            assertThat(asset.mimeType()).isEqualTo("image/png");
            assertThat(asset.byteSize()).isEqualTo(2_336_001L);
            assertThat(asset.width()).isEqualTo(1672);
            assertThat(asset.height()).isEqualTo(941);
        });
        assertThatThrownBy(() -> media.catalog(branch.token()))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void detectsActualImageFormatStoresTheUploadAndDeliversItPublicly() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        byte[] png = image("png");

        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("hero.jpg", "image/jpeg", png), "테스트 히어로", "바다를 바라보는 객실");

        assertThat(uploaded.displayName()).isEqualTo("테스트 히어로");
        assertThat(uploaded.deliveryUrl()).isEqualTo("/api/website/media/" + uploaded.id() + "/content");
        assertThat(uploaded.mimeType()).isEqualTo("image/png");
        assertThat(uploaded.byteSize()).isEqualTo((long) png.length);
        assertThat(uploaded.width()).isEqualTo(2);
        assertThat(uploaded.height()).isEqualTo(1);
        assertThat(uploaded.defaultAltText()).isEqualTo("바다를 바라보는 객실");

        WebsiteMediaContent delivered = media.publicContent(uploaded.id());
        assertThat(delivered.mimeType()).isEqualTo("image/png");
        assertThat(delivered.bytes()).isEqualTo(png);

        WebsiteMediaAsset jpeg = media.upload(headquarters.token(),
                new MemoryMultipartFile("hero.png", "image/png", image("jpeg")), "테스트 JPEG", "숲을 바라보는 객실");
        assertThat(jpeg.mimeType()).isEqualTo("image/jpeg");
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesAnUploadedAssetThroughDraftAndPublishWithoutOverwritingFilesOtherPagesOrHistory() throws IOException {
        String token = staffAccess.login("media-hq@example.com", "hq-password").token();
        byte[] oldBytes = image("png");
        byte[] newBytes = image("jpeg");
        WebsiteMediaAsset oldAsset = media.upload(token,
                new MemoryMultipartFile("same-name.png", "image/png", oldBytes), "기존 자산", "기본 alt");
        Map<String, Object> content = Map.of("seo", Map.of("title", "교체 테스트", "description", "미디어 교체 검증"), "blocks", List.of(Map.of(
                "type", "HERO", "imageAssetId", oldAsset.id().toString(), "imageSrc", oldAsset.deliveryUrl(),
                "imageAlt", "페이지 문맥 alt", "eyebrow", "STAY HANEUL", "title", "교체 테스트", "description", "이미지 교체")));
        UUID parent = UUID.fromString("12000000-0000-0000-0000-000000000005");
        WebsitePageDocument created = pages.createContentPage(token, parent,
                new WebsitePageDraftMetadata("media-replacement", "교체 테스트", true, 10), content);
        WebsitePageDocument first = pages.publishPage(token, created.id(), created.draftVersion(), created.publishedVersion());
        WebsitePageDocument otherCreated = pages.createContentPage(token, parent,
                new WebsitePageDraftMetadata("media-unchanged", "다른 페이지", true, 20), content);
        WebsitePageDocument other = pages.publishPage(token, otherCreated.id(), otherCreated.draftVersion(), otherCreated.publishedVersion());
        String oldSnapshot = jdbc.queryForObject("select page_snapshot::text from website_page_version where page_id = ? and version = ?",
                String.class, first.id(), first.publishedVersion());

        WebsiteMediaAsset replacement = media.upload(token,
                new MemoryMultipartFile("same-name.png", "image/png", newBytes), "새 자산", "새 기본 alt");
        assertThat(replacement.id()).isNotEqualTo(oldAsset.id());
        assertThat(replacement.deliveryUrl()).isNotEqualTo(oldAsset.deliveryUrl());
        assertThat(pages.pageDraft(token, first.id())).isEqualTo(first);

        Map<String, Object> hero = new java.util.LinkedHashMap<>((Map<String, Object>) ((List<?>) first.draftContent().get("blocks")).getFirst());
        hero.put("imageAssetId", replacement.id().toString());
        hero.put("imageSrc", replacement.deliveryUrl());
        Map<String, Object> changed = new java.util.LinkedHashMap<>(first.draftContent());
        changed.put("blocks", List.of(hero));
        WebsitePageDocument saved = pages.saveContentPageDraft(token, first.id(), first.draftVersion(),
                new WebsitePageDraftMetadata("media-replacement", "교체 테스트", true, 10), changed);
        assertThat(saved.publishedContent()).isEqualTo(first.publishedContent());
        assertThat(pages.resolvePublished("/brand/media-replacement").content()).isEqualTo(first.publishedContent());
        assertThat(media.usages(token, replacement.id())).singleElement().satisfies(usage -> {
            assertThat(usage.documentState()).isEqualTo("DRAFT");
            assertThat(usage.altText()).isEqualTo("페이지 문맥 alt");
        });
        assertThat(media.usages(token, oldAsset.id())).hasSize(3);

        pages.publishPage(token, first.id(), saved.draftVersion(), saved.publishedVersion());
        Map<String, Object> publicHero = (Map<String, Object>) ((List<?>) pages.resolvePublished("/brand/media-replacement").content().get("blocks")).getFirst();
        assertThat(publicHero).containsEntry("imageAssetId", replacement.id().toString()).containsEntry("imageAlt", "페이지 문맥 alt");
        assertThat(media.usages(token, replacement.id())).extracting(WebsiteMediaUsage::documentState).containsExactly("DRAFT", "PUBLISHED");
        assertThat(media.usages(token, oldAsset.id())).hasSize(2).allSatisfy(usage -> assertThat(usage.pageId()).isEqualTo(other.id()));
        assertThat(pages.pageDraft(token, other.id())).isEqualTo(other);
        assertThat(jdbc.queryForObject("select page_snapshot::text from website_page_version where page_id = ? and version = ?",
                String.class, first.id(), first.publishedVersion())).isEqualTo(oldSnapshot);
        assertThat(media.publicContent(oldAsset.id()).bytes()).isEqualTo(oldBytes);
        assertThat(media.publicContent(replacement.id()).bytes()).isEqualTo(newBytes);
        assertThat(media.catalog(token)).filteredOn(asset -> asset.id().equals(oldAsset.id())).singleElement()
                .satisfies(asset -> assertThat(asset.version()).isEqualTo(oldAsset.version()));
    }

    @Test
    void reportsReplaceableDraftMediaUsagesWithoutWriting() throws IOException {
        String token = staffAccess.login("media-hq@example.com", "hq-password").token();
        WebsiteMediaAsset source = media.upload(token,
                new MemoryMultipartFile("source.png", "image/png", image("png")), "원본 자산", "원본 기본 alt");
        WebsiteMediaAsset target = media.upload(token,
                new MemoryMultipartFile("target.jpg", "image/jpeg", image("jpeg")), "교체 자산", "교체 기본 alt");
        UUID parent = UUID.fromString("12000000-0000-0000-0000-000000000005");
        Map<String, Object> content = mediaContent(source, "활성 위치 alt");
        WebsitePageDocument active = pages.createContentPage(token, parent,
                new WebsitePageDraftMetadata("bulk-media-active", "활성 교체 페이지", true, 30), content);
        WebsitePageDocument published = pages.publishPage(token, active.id(), active.draftVersion(), active.publishedVersion());
        translations.initialize(token, published.id(), published.draftVersion(), published.lifecycleVersion());
        WebsitePageDocument archived = pages.createContentPage(token, parent,
                new WebsitePageDraftMetadata("bulk-media-archived", "보관 교체 페이지", true, 40),
                mediaContent(source, "보관 위치 alt"));
        pages.archiveContentPage(token, archived.id(), new WebsitePageLifecycleRequest(
                archived.lifecycleVersion(), archived.draftVersion(), archived.publishedVersion()));
        int auditCount = jdbc.queryForObject("select count(*) from website_page_audit", Integer.class);

        WebsiteMediaDraftReplacementImpact impact = replacements.impact(token, source.id(), target.id());

        assertThat(impact.sourceAsset().id()).isEqualTo(source.id());
        assertThat(impact.targetAsset().id()).isEqualTo(target.id());
        assertThat(impact.replaceableUsages()).hasSize(2);
        assertThat(impact.replaceableUsages()).extracting(WebsiteMediaDraftReplacementUsage::locale)
                .containsExactly("ko", "en");
        assertThat(impact.replaceableUsages()).extracting(WebsiteMediaDraftReplacementUsage::fieldPath)
                .containsExactly("blocks[0].imageAssetId", "blocks[0].imageAssetId");
        assertThat(impact.replaceableUsages()).extracting(WebsiteMediaDraftReplacementUsage::expectedDraftVersion)
                .containsExactly(published.draftVersion(), 1);
        assertThat(impact.publishedUsageCount()).isEqualTo(1);
        assertThat(impact.archivedDraftUsageCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from website_page_audit", Integer.class)).isEqualTo(auditCount);
        assertThat(pages.pageDraft(token, published.id()).draftContent()).isEqualTo(published.draftContent());
    }

    @Test
    @SuppressWarnings("unchecked")
    void replacesAllActiveDraftUsagesAtomicallyAndPreservesPublishedState() throws IOException {
        String token = staffAccess.login("media-hq@example.com", "hq-password").token();
        WebsiteMediaAsset source = media.upload(token,
                new MemoryMultipartFile("bulk-source.png", "image/png", image("png")), "일괄 원본", "원본 기본 alt");
        WebsiteMediaAsset target = media.upload(token,
                new MemoryMultipartFile("bulk-target.jpg", "image/jpeg", image("jpeg")), "일괄 대상", "대상 기본 alt");
        WebsitePageDocument home = pages.homeDraft(token);
        pages.saveHomeDraft(token, home.draftVersion(), structuredMediaContent(home.draftContent(), source));
        WebsitePageDocument landing = pages.landingDraft(token, BRANCH_HOTEL);
        pages.saveLandingDraft(token, BRANCH_HOTEL, landing.draftVersion(), new WebsitePageDraftMetadata(
                landing.draftMetadata().slug(), landing.draftMetadata().menuLabel(),
                landing.draftMetadata().menuVisible(), landing.draftMetadata().menuOrder()),
                landingMediaContent(source));
        UUID parent = UUID.fromString("12000000-0000-0000-0000-000000000005");
        WebsitePageDocument created = pages.createContentPage(token, parent,
                new WebsitePageDraftMetadata("bulk-media-replace", "일괄 교체 페이지", true, 50),
                mediaContent(source, "한국어 위치 alt"));
        WebsitePageDocument published = pages.publishPage(token, created.id(), created.draftVersion(), created.publishedVersion());
        WebsitePageDocument english = translations.initialize(token, published.id(), published.draftVersion(), published.lifecycleVersion());
        translations.requestReview(token, published.id(), new WebsiteTranslationReviewActionRequest(english.draftVersion(), null));
        String publisher = publisher();
        translations.approveReview(publisher, published.id(), new WebsiteTranslationReviewActionRequest(english.draftVersion(), null));
        WebsiteMediaDraftReplacementImpact impact = replacements.impact(token, source.id(), target.id());
        WebsiteMediaDraftReplacementRequest request = new WebsiteMediaDraftReplacementRequest(
                target.id(), source.version(), target.version(), impact.replaceableUsages().stream()
                .map(usage -> new WebsiteMediaDraftReplacementTarget(
                        usage.pageId(), usage.locale(), usage.fieldPath(), usage.expectedDraftVersion()))
                .toList());
        String snapshot = jdbc.queryForObject(
                "select page_snapshot::text from website_page_version where page_id = ? and version = ?",
                String.class, published.id(), published.publishedVersion());

        WebsiteMediaDraftReplacementResult result = replacements.replace(token, source.id(), request);

        assertThat(result.replacedUsageCount()).isEqualTo(4);
        assertThat(result.changedDraftCount()).isEqualTo(4);
        WebsitePageDocument koreanAfter = pages.pageDraft(token, published.id());
        WebsitePageDocument englishAfter = translations.draft(token, published.id());
        Map<String, Object> koreanHero = (Map<String, Object>) ((List<?>) koreanAfter.draftContent().get("blocks")).getFirst();
        Map<String, Object> englishHero = (Map<String, Object>) ((List<?>) englishAfter.draftContent().get("blocks")).getFirst();
        assertThat(koreanHero).containsEntry("imageAssetId", target.id().toString())
                .containsEntry("imageSrc", target.deliveryUrl()).containsEntry("imageAlt", "한국어 위치 alt");
        assertThat(englishHero).containsEntry("imageAssetId", target.id().toString())
                .containsEntry("imageSrc", target.deliveryUrl()).containsEntry("imageAlt", "한국어 위치 alt");
        assertThat(koreanAfter.draftVersion()).isEqualTo(published.draftVersion() + 1);
        assertThat(englishAfter.draftVersion()).isEqualTo(english.draftVersion() + 1);
        assertThat(pages.resolvePublished("/brand/bulk-media-replace").content()).isEqualTo(published.publishedContent());
        assertThat(jdbc.queryForObject(
                "select page_snapshot::text from website_page_version where page_id = ? and version = ?",
                String.class, published.id(), published.publishedVersion())).isEqualTo(snapshot);
        assertThat(translations.review(token, published.id()).status()).isEqualTo(WebsiteTranslationReviewStatus.DRAFT);
        assertThat(translations.review(token, published.id()).events().getFirst().action()).isEqualTo("APPROVAL_INVALIDATED");
        assertThat(media.usages(token, target.id())).hasSize(4)
                .allSatisfy(usage -> assertThat(usage.documentState()).isEqualTo("DRAFT"));
        assertThat(media.usages(token, source.id())).extracting(WebsiteMediaUsage::documentState)
                .containsExactly("PUBLISHED");
        assertThat(pages.homeDraft(token).draftContent()).isEqualTo(structuredMediaContent(home.draftContent(), target));
        assertThat(pages.landingDraft(token, BRANCH_HOTEL).draftContent()).isEqualTo(landingMediaContent(target));
        assertThat(jdbc.queryForObject(
                "select count(*) from website_page_audit where page_id = ? and action = 'DRAFT_SAVED' and details->>'operation' = 'MEDIA_DRAFT_USAGES_REPLACED'",
                Integer.class, published.id())).isEqualTo(2);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rejectsAStaleConfirmedUsageSetBeforeWritingAnyDraft() throws IOException {
        String token = staffAccess.login("media-hq@example.com", "hq-password").token();
        WebsiteMediaAsset source = media.upload(token,
                new MemoryMultipartFile("rollback-source.png", "image/png", image("png")), "롤백 원본", "원본 alt");
        WebsiteMediaAsset target = media.upload(token,
                new MemoryMultipartFile("rollback-target.jpg", "image/jpeg", image("jpeg")), "롤백 대상", "대상 alt");
        UUID parent = UUID.fromString("12000000-0000-0000-0000-000000000005");
        WebsitePageDocument firstCreated = pages.createContentPage(token, parent,
                new WebsitePageDraftMetadata("bulk-rollback-one", "롤백 페이지 하나", true, 60),
                mediaContent(source, "하나 alt"));
        WebsitePageDocument secondCreated = pages.createContentPage(token, parent,
                new WebsitePageDraftMetadata("bulk-rollback-two", "롤백 페이지 둘", true, 70),
                mediaContent(source, "둘 alt"));
        WebsiteMediaDraftReplacementImpact impact = replacements.impact(token, source.id(), target.id());
        WebsiteMediaDraftReplacementUsage staleUsage = impact.replaceableUsages().getLast();
        WebsiteMediaDraftReplacementRequest request = new WebsiteMediaDraftReplacementRequest(
                target.id(), source.version(), target.version(), impact.replaceableUsages().stream()
                .map(usage -> new WebsiteMediaDraftReplacementTarget(
                        usage.pageId(), usage.locale(), usage.fieldPath(), usage.pageId().equals(staleUsage.pageId())
                                ? usage.expectedDraftVersion() + 1 : usage.expectedDraftVersion()))
                .toList());

        assertThatThrownBy(() -> replacements.replace(token, source.id(), request))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_REPLACEMENT_CONFLICT");

        for (UUID pageId : List.of(firstCreated.id(), secondCreated.id())) {
            WebsitePageDocument unchanged = pages.pageDraft(token, pageId);
            Map<String, Object> hero = (Map<String, Object>) ((List<?>) unchanged.draftContent().get("blocks")).getFirst();
            assertThat(hero).containsEntry("imageAssetId", source.id().toString())
                    .containsEntry("imageSrc", source.deliveryUrl());
        }
        assertThat(media.usages(token, source.id())).hasSize(2);
        assertThat(media.usages(token, target.id())).isEmpty();
        assertThat(jdbc.queryForObject(
                "select count(*) from website_page_audit where action = 'DRAFT_SAVED' and details->>'operation' = 'MEDIA_DRAFT_USAGES_REPLACED'",
                Integer.class)).isZero();
    }

    @Test
    void rejectsInvalidDraftReplacementImpactRequests() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("media-branch@example.com", "branch-password");
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_EDITOR')",
                UUID.randomUUID(), "media-editor@example.com", "미디어 편집자", encoder.encode("editor-password"));
        jdbc.update("insert into staff_member (id, email, display_name, password_hash, role) values (?, ?, ?, ?, 'HQ_PUBLISHER')",
                UUID.randomUUID(), "media-reviewer@example.com", "미디어 승인자", encoder.encode("reviewer-password"));
        StaffSessionView editor = staffAccess.login("media-editor@example.com", "editor-password");
        StaffSessionView publisher = staffAccess.login("media-reviewer@example.com", "reviewer-password");
        WebsiteMediaAsset source = media.upload(headquarters.token(),
                new MemoryMultipartFile("impact-source.png", "image/png", image("png")), "영향 원본", "원본 alt");
        WebsiteMediaAsset target = media.upload(headquarters.token(),
                new MemoryMultipartFile("impact-target.jpg", "image/jpeg", image("jpeg")), "영향 대상", "대상 alt");

        assertThatThrownBy(() -> replacements.impact(branch.token(), source.id(), target.id()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> replacements.impact(editor.token(), source.id(), target.id()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> replacements.impact(publisher.token(), source.id(), target.id()))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> replacements.impact(headquarters.token(), source.id(), source.id()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> replacements.impact(headquarters.token(), source.id(), BUNDLED_ASSET))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("새로 업로드");
    }

    @Test
    void requiresRevalidationBeforeReusingPublicUploadedContent() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("revalidate.png", "image/png", image("png")),
                "재검증 대상", "재검증 대상 기본 alt");

        ResponseEntity<byte[]> response = publicMedia.content(uploaded.id());

        assertThat(response.getHeaders().getCacheControl()).isEqualTo("public, max-age=0, must-revalidate");
    }

    @Test
    void rejectsInvalidOversizedAndOverPixelUploadsBeforePersistingThem() {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");

        assertThatThrownBy(() -> media.upload(headquarters.token(),
                new MemoryMultipartFile("not-image.txt", "text/plain", "not an image".getBytes()), "잘못된 파일", "대체 텍스트"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PNG 또는 JPEG");
        assertThatThrownBy(() -> media.upload(headquarters.token(),
                new MemoryMultipartFile("large.png", "image/png", new byte[10_485_761]), "큰 파일", "대체 텍스트"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("10 MiB");
        assertThatThrownBy(() -> media.upload(headquarters.token(),
                new MemoryMultipartFile("large-pixels.png", "image/png", overPixelPng()), "큰 이미지", "대체 텍스트"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24,000,000");

        assertThat(media.catalog(headquarters.token())).extracting(WebsiteMediaAsset::displayName)
                .doesNotContain("잘못된 파일", "큰 파일", "큰 이미지");
    }

    @Test
    void returnsDraftAndPublishedUsageOnlyToHeadquarters() {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("media-branch@example.com", "branch-password");

        List<WebsiteMediaUsage> usages = media.usages(headquarters.token(), BUNDLED_ASSET);

        assertThat(usages).anySatisfy(usage -> {
            assertThat(usage.pageType()).isEqualTo("HOME_PAGE");
            assertThat(usage.documentState()).isEqualTo("DRAFT");
            assertThat(usage.fieldPath()).isEqualTo("blocks[0].imageAssetId");
        });
        assertThat(usages).anySatisfy(usage -> {
            assertThat(usage.pageType()).isEqualTo("HOME_PAGE");
            assertThat(usage.documentState()).isEqualTo("PUBLISHED");
        });
        assertThatThrownBy(() -> media.usages(branch.token(), BUNDLED_ASSET))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    @Test
    void updatesCatalogMetadataAtTheCurrentVersionWithoutChangingPageSpecificAltText() {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");

        WebsiteMediaAsset updated = media.updateMetadata(headquarters.token(), BUNDLED_ASSET,
                new WebsiteMediaMetadataRequest("속초 해안 저녁 이미지", "저녁빛 속초 해안", 1));

        assertThat(updated.displayName()).isEqualTo("속초 해안 저녁 이미지");
        assertThat(updated.defaultAltText()).isEqualTo("저녁빛 속초 해안");
        assertThat(updated.status()).isEqualTo("ACTIVE");
        assertThat(updated.version()).isEqualTo(2);
        assertThat(media.usages(headquarters.token(), BUNDLED_ASSET))
                .extracting(WebsiteMediaUsage::altText)
                .doesNotContain("저녁빛 속초 해안");

        assertThatThrownBy(() -> media.updateMetadata(headquarters.token(), BUNDLED_ASSET,
                new WebsiteMediaMetadataRequest("오래된 저장", "오래된 alt", 1)))
                .isInstanceOf(WebsiteMediaConflictException.class);
    }

    @Test
    void archivesOnlyAnUnusedAssetAndAllowsItToBeRestored() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        byte[] uploadedBytes = image("png");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("unused.png", "image/png", uploadedBytes), "보관 대상", "보관 대상 기본 alt");

        assertThatThrownBy(() -> media.archive(headquarters.token(), BUNDLED_ASSET,
                new WebsiteMediaVersionRequest(1)))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .hasMessageContaining("사용 중");

        WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version()));
        assertThat(archived.status()).isEqualTo("ARCHIVED");
        assertThat(archived.version()).isEqualTo(uploaded.version() + 1);
        assertThat(archived.archivedAt()).isNotNull();
        assertThat(archived.permanentDeleteAvailableAt()).isEqualTo(archived.archivedAt().plusDays(30));
        assertThat(media.catalog(headquarters.token())).extracting(WebsiteMediaAsset::id)
                .doesNotContain(uploaded.id());
        assertThat(media.catalog(headquarters.token(), true)).extracting(WebsiteMediaAsset::id)
                .contains(uploaded.id());
        assertThatThrownBy(() -> media.publicContent(uploaded.id()))
                .isInstanceOf(WebsiteMediaNotFoundException.class);
        assertThatThrownBy(() -> media.requireActiveAsset(uploaded.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("사용할 수 없습니다");

        WebsiteMediaAsset restored = media.restore(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(archived.version()));
        assertThat(restored.status()).isEqualTo("ACTIVE");
        assertThat(restored.version()).isEqualTo(archived.version() + 1);
        assertThat(restored.archivedAt()).isNull();
        assertThat(restored.permanentDeleteAvailableAt()).isNull();
        assertThat(media.publicContent(uploaded.id()).bytes()).isEqualTo(uploadedBytes);
    }

    @Test
    void permanentlyDeletesAnEligibleArchivedUploadAndItsStoredFile() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("delete-me.png", "image/png", image("png")),
                "영구 삭제 대상", "영구 삭제 대상 기본 alt");
        Path variantFile = readyVariantFile(uploaded);
        WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version()));
        jdbc.update("update website_media_asset set archived_at = current_timestamp - interval '31 days' where id = ?",
                uploaded.id());
        Path storedFile = Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media", uploaded.id() + ".png");

        media.permanentlyDelete(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(archived.version()));

        assertThat(media.catalog(headquarters.token(), true)).extracting(WebsiteMediaAsset::id)
                .doesNotContain(uploaded.id());
        assertThat(Files.exists(storedFile)).isFalse();
        assertThat(Files.exists(variantFile)).isFalse();
        assertThat(jdbc.queryForObject(
                "select count(*) from website_media_variant where asset_id = ?", Integer.class, uploaded.id()))
                .isZero();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void committedPermanentDeleteRemovesRowsAndAllQuarantinedFiles() throws IOException {
        try {
            StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
            WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                    new MemoryMultipartFile("committed-delete.png", "image/png", image("png")),
                    "커밋 영구 삭제 대상", "커밋 영구 삭제 대상 기본 alt");
            Path variantFile = readyVariantFile(uploaded);
            WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                    new WebsiteMediaVersionRequest(uploaded.version()));
            jdbc.update("update website_media_asset set archived_at = current_timestamp - interval '31 days' where id = ?",
                    uploaded.id());
            Path storage = Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media");
            Path originalFile = storage.resolve(uploaded.id() + ".png");

            media.permanentlyDelete(headquarters.token(), uploaded.id(),
                    new WebsiteMediaVersionRequest(archived.version()));

            TransactionTemplate verification = new TransactionTemplate(transactionManager);
            verification.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            verification.executeWithoutResult(status -> {
                assertThat(jdbc.queryForObject(
                        "select count(*) from website_media_asset where id = ?", Integer.class, uploaded.id()))
                        .isZero();
                assertThat(jdbc.queryForObject(
                        "select count(*) from website_media_variant where asset_id = ?", Integer.class, uploaded.id()))
                        .isZero();
            });
            assertThat(originalFile).doesNotExist();
            assertThat(variantFile).doesNotExist();
            assertThat(regularFilesUnder(storage.resolve(".trash"))).isEmpty();
        } finally {
            removeCommittedPermanentDeleteFixtures();
            deleteStorage();
        }
    }

    @Test
    void restoresOriginalAndVariantFilesWhenDeleteVersionConflictRollsBack() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("rollback.png", "image/png", image("png")),
                "롤백 복구 대상", "롤백 복구 대상 기본 alt");
        Path variantFile = readyVariantFile(uploaded);
        WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version()));
        jdbc.update("update website_media_asset set archived_at = current_timestamp - interval '31 days' where id = ?",
                uploaded.id());
        Path storedFile = Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media", uploaded.id() + ".png");
        jdbc.execute("""
                create function pg_temp.prevent_task3_media_delete() returns trigger
                language plpgsql as 'begin return null; end'
                """);
        jdbc.execute("""
                create trigger prevent_task3_media_delete
                before delete on website_media_asset
                for each row execute function pg_temp.prevent_task3_media_delete()
                """);

        assertThatThrownBy(() -> media.permanentlyDelete(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(archived.version())))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_VERSION_CONFLICT");
        assertThat(Files.exists(storedFile)).isFalse();
        assertThat(Files.exists(variantFile)).isFalse();

        TestTransaction.flagForRollback();
        TestTransaction.end();

        assertThat(Files.exists(storedFile)).isTrue();
        assertThat(Files.exists(variantFile)).isTrue();
        Files.deleteIfExists(storedFile);
        Files.deleteIfExists(variantFile);
    }

    @Test
    void rejectsPermanentDeletionWhenAReadyVariantFileIsMissing() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("missing-variant.png", "image/png", image("png")),
                "variant 누락 대상", "variant 누락 대상 기본 alt");
        Path variantFile = readyVariantFile(uploaded);
        WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version()));
        jdbc.update("update website_media_asset set archived_at = current_timestamp - interval '31 days' where id = ?",
                uploaded.id());
        Path storedFile = Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media", uploaded.id() + ".png");
        Files.delete(variantFile);

        assertThatThrownBy(() -> media.permanentlyDelete(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(archived.version())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("영구 삭제할 미디어 파일");

        assertThat(storedFile).exists();
        assertThat(jdbc.queryForObject(
                "select count(*) from website_media_asset where id = ?", Integer.class, uploaded.id()))
                .isOne();
    }

    @Test
    void rejectsPermanentDeletionBeforeTheArchiveGracePeriodEnds() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("recent.png", "image/png", image("png")),
                "최근 보관 자산", "최근 보관 자산 기본 alt");
        WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version()));

        assertThatThrownBy(() -> media.permanentlyDelete(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(archived.version())))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_DELETE_CONFLICT");
    }

    @Test
    void rejectsPermanentDeletionOfActiveAndBundledAssets() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("active.png", "image/png", image("png")),
                "활성 자산", "활성 자산 기본 alt");

        assertThatThrownBy(() -> media.permanentlyDelete(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version())))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_DELETE_CONFLICT");
        assertThatThrownBy(() -> media.permanentlyDelete(headquarters.token(), BUNDLED_ASSET,
                new WebsiteMediaVersionRequest(1)))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_DELETE_CONFLICT");
    }

    @Test
    void rejectsPermanentDeletionWhenAnArchivedAssetHasAUsage() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("referenced.png", "image/png", image("png")),
                "참조 자산", "참조 자산 기본 alt");
        WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version()));
        jdbc.update("update website_media_asset set archived_at = current_timestamp - interval '31 days' where id = ?",
                uploaded.id());
        jdbc.update("""
                insert into website_media_usage (asset_id, page_id, document_state, field_path, alt_text)
                select ?, page_id, document_state, 'test.permanentDelete', '참조 중인 자산'
                  from website_media_usage
                 limit 1
                """, uploaded.id());

        assertThatThrownBy(() -> media.permanentlyDelete(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(archived.version())))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_IN_USE");
    }

    @Test
    void rejectsPermanentDeletionByBranchStaffAndWithAStaleVersion() throws IOException {
        StaffSessionView headquarters = staffAccess.login("media-hq@example.com", "hq-password");
        StaffSessionView branch = staffAccess.login("media-branch@example.com", "branch-password");
        WebsiteMediaAsset uploaded = media.upload(headquarters.token(),
                new MemoryMultipartFile("protected.png", "image/png", image("png")),
                "삭제 보호 자산", "삭제 보호 자산 기본 alt");
        WebsiteMediaAsset archived = media.archive(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version()));
        jdbc.update("update website_media_asset set archived_at = current_timestamp - interval '31 days' where id = ?",
                uploaded.id());

        assertThatThrownBy(() -> media.permanentlyDelete(branch.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(archived.version())))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> media.permanentlyDelete(headquarters.token(), uploaded.id(),
                new WebsiteMediaVersionRequest(uploaded.version())))
                .isInstanceOf(WebsiteMediaConflictException.class)
                .extracting(error -> ((WebsiteMediaConflictException) error).code())
                .isEqualTo("WEBSITE_MEDIA_VERSION_CONFLICT");
    }

    @Test
    void restrictsLifecycleChangesToHeadquarters() {
        StaffSessionView branch = staffAccess.login("media-branch@example.com", "branch-password");

        assertThatThrownBy(() -> media.updateMetadata(branch.token(), BUNDLED_ASSET,
                new WebsiteMediaMetadataRequest("변경", "변경 alt", 1)))
                .isInstanceOf(StaffAccessDeniedException.class);
        assertThatThrownBy(() -> media.archive(branch.token(), BUNDLED_ASSET,
                new WebsiteMediaVersionRequest(1)))
                .isInstanceOf(StaffAccessDeniedException.class);
    }

    private byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(2, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0x1E3A5F);
        image.setRGB(1, 0, 0xD7B47A);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, output)) throw new IllegalStateException("테스트 이미지 형식을 쓸 수 없습니다.");
        return output.toByteArray();
    }

    private Map<String, Object> mediaContent(WebsiteMediaAsset asset, String altText) {
        return Map.of("seo", Map.of("title", "일괄 교체", "description", "미디어 초안 일괄 교체 검증"),
                "blocks", List.of(Map.of(
                        "type", "HERO", "imageAssetId", asset.id().toString(), "imageSrc", asset.deliveryUrl(),
                        "imageAlt", altText, "eyebrow", "STAY HANEUL", "title", "일괄 교체",
                        "description", "여러 초안 위치의 미디어를 교체합니다.")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> structuredMediaContent(Map<String, Object> content, WebsiteMediaAsset asset) {
        Map<String, Object> changed = new LinkedHashMap<>(content);
        List<Object> blocks = new ArrayList<>((List<Object>) content.get("blocks"));
        Map<String, Object> hero = new LinkedHashMap<>((Map<String, Object>) blocks.getFirst());
        hero.put("imageAssetId", asset.id().toString());
        hero.put("imageSrc", asset.deliveryUrl());
        blocks.set(0, hero);
        changed.put("blocks", blocks);
        return changed;
    }

    private Map<String, Object> landingMediaContent(WebsiteMediaAsset asset) {
        return Map.of(
                "heroAssetId", asset.id().toString(), "heroImage", asset.deliveryUrl(), "heroAlt", "지점 위치 alt",
                "eyebrow", "SOKCHO · EAST SEA", "title", "지점 일괄 교체", "description", "지점 초안 이미지 교체",
                "arrival", Map.of("address", "가상 해안로 186", "checkInOut", "15:00 / 11:00", "highlight", "바다 곁의 하루"),
                "experiences", List.of(Map.of("category", "ROOM", "title", "수평선 객실", "description", "바다를 담은 객실")),
                "offers", List.of(Map.of("title", "푸른 아침", "detail", "조식 포함", "bookingPeriod", "2026.09.01 ~ 2026.12.31", "stayPeriod", "2026.09.15 ~ 2027.02.28")));
    }

    private String publisher() {
        jdbc.update("""
                insert into staff_member (id, email, display_name, password_hash, role)
                values (?, 'media-publisher@example.com', '미디어 승인자', ?, 'HQ_PUBLISHER')
                on conflict (email) do nothing
                """, UUID.randomUUID(), new BCryptPasswordEncoder().encode("publisher-password"));
        return staffAccess.login("media-publisher@example.com", "publisher-password").token();
    }

    private byte[] overPixelPng() {
        return Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAE4gAABOICAAAAAAK6Q0fAAAAAElFTkSuQmCC");
    }

    private Path readyVariantFile(WebsiteMediaAsset asset) throws IOException {
        String storageKey = asset.id() + "-640-task3-ready.webp";
        byte[] bytes = "task3-ready-webp".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path path = Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media", storageKey);
        Files.write(path, bytes);
        jdbc.update("""
                insert into website_media_variant (
                    id, asset_id, format, target_width, status, storage_key, mime_type,
                    byte_size, width, height, attempt_count
                ) values (?, ?, 'WEBP', 640, 'READY', ?, 'image/webp', ?, 640, 360, 1)
                """, UUID.randomUUID(), asset.id(), storageKey, (long) bytes.length);
        return path;
    }

    private void deleteStorage() throws IOException {
        Path storage = Path.of(System.getProperty("java.io.tmpdir"), "hotel-chain-media");
        if (!Files.exists(storage)) return;
        try (var paths = Files.walk(storage)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (IOException error) { throw new IllegalStateException(error); }
            });
        }
    }

    private void removeCommittedPermanentDeleteFixtures() {
        jdbc.update("delete from website_media_asset where display_name = '커밋 영구 삭제 대상'");
        jdbc.update("""
                delete from staff_session
                 where staff_id in (
                     select id from staff_member where email in ('media-hq@example.com', 'media-branch@example.com')
                 )
                """);
        jdbc.update("delete from staff_member where email in ('media-hq@example.com', 'media-branch@example.com')");
        jdbc.update("delete from hotel where id = ?", BRANCH_HOTEL);
    }

    private List<Path> regularFilesUnder(Path directory) throws IOException {
        if (!Files.exists(directory)) return List.of();
        try (var paths = Files.walk(directory)) {
            return paths.filter(Files::isRegularFile).toList();
        }
    }

    private record MemoryMultipartFile(String originalFilename, String contentType, byte[] bytes) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return originalFilename; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return bytes.length == 0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(java.io.File destination) throws IOException { Files.write(destination.toPath(), bytes); }
        @Override public void transferTo(Path destination) throws IOException { Files.write(destination, bytes); }
    }
}
