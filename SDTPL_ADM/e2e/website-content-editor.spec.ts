import { expect, test } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";
const HOME_PAGE = "home-page";
const BRAND_SECTION = "brand-section";
const STORY_PAGE = "brand-story";
const SEORAKSAN = "11000000-0000-0000-0000-000000000002";
const JEJU = "11000000-0000-0000-0000-000000000003";
const SEORAKSAN_ROOM = "12000000-0000-0000-0000-000000000002";
const JEJU_ROOM = "12000000-0000-0000-0000-000000000003";
const SEORAKSAN_ROOMS_SECTION = "seoraksan-rooms-section";
const ROOM_PAGE = "room-page";
const PROMOTION_PAGE = "promotion-page";
const BUNDLED_ASSET = "14000000-0000-0000-0000-000000000001";
const UPLOADED_ASSET = "14000000-0000-0000-0000-000000000002";
const ARCHIVED_ASSET = "14000000-0000-0000-0000-000000000003";
const INITIAL_PUBLISHED_VERSION = 1;

function mediaAsset(overrides: Record<string, unknown> = {}) {
  return {
    id: BUNDLED_ASSET,
    displayName: "속초 해안 대표 이미지",
    deliveryUrl: "/images/sokcho-coast-hero.png",
    mimeType: "image/png",
    byteSize: 248512,
    width: 1600,
    height: 900,
    defaultAltText: "동해와 설악산을 바라보는 속초 해안",
    usageCount: 4,
    status: "ACTIVE",
    version: 1,
    ...overrides,
  };
}

function contentPageDocument(overrides: Record<string, unknown> = {}) {
  return {
    id: STORY_PAGE,
    pageType: "CONTENT_PAGE",
    hotelId: null,
    contentKind: "BRAND",
    draftConnections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] },
    draftContent: {
      seo: { title: "브랜드 이야기 | STAY HANEUL", description: "STAY HANEUL이 만드는 머무름의 기준을 소개합니다." },
      blocks: [{ type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "브랜드 이야기", eyebrow: "STAY HANEUL", title: "브랜드 이야기", description: "머무름의 기준을 만듭니다." }],
    },
    draftVersion: 1,
    draftMetadata: { slug: "story", path: "/brand/story", menuLabel: "브랜드 이야기", menuVisible: true, menuOrder: 10 },
    publishedContent: {},
    publishedVersion: INITIAL_PUBLISHED_VERSION,
    publishedMetadata: { slug: "story", path: "/brand/story", menuLabel: "브랜드 이야기", menuVisible: false, menuOrder: 0 },
    lifecycleStatus: "ACTIVE",
    lifecycleVersion: 1,
    ...overrides,
  };
}

function typedPageDocument(overrides: Record<string, unknown> = {}) {
  return contentPageDocument({
    ...overrides,
    draftContent: {
      seo: { title: "유형 페이지", description: "유형별 콘텐츠를 소개합니다." },
      blocks: [
        { type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "유형 대표 이미지", eyebrow: "STAY HANEUL", title: "유형 페이지", description: "유형별 소개입니다." },
        { type: "SPEC_TABLE", title: "상세 정보", rows: [{ label: "안내", value: "내용" }] },
        { type: "PROMOTION_SUMMARY", title: "프로모션 안내", salesPeriod: "9월", stayPeriod: "10월", benefits: ["조식"] },
        { type: "BOOKING_CTA", title: "객실 검색", description: "실시간 객실을 확인합니다.", label: "객실 검색", hotelId: SEORAKSAN, roomTypeId: SEORAKSAN_ROOM },
      ],
    },
  });
}

function contentPageVersionComparison(baseVersion: number, compareVersion: number) {
  const snapshots = {
    2: {
      version: 2,
      publishedAt: "2026-09-09T09:00:00Z",
      metadata: { slug: "story", path: "/brand/story", menuLabel: "브랜드 이야기", menuVisible: true, menuOrder: 10 },
      publishedFromDraftVersion: 2,
      content: {
        seo: { title: "첫 브랜드 이야기 | STAY HANEUL", description: "첫 번째 발행본 소개 문구입니다." },
        blocks: [
          { type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "첫 번째 히어로", eyebrow: "STAY HANEUL", title: "첫 번째 이야기", description: "첫 번째 발행본 설명입니다.", cta: { label: "첫 예약", href: "/booking" } },
          { type: "TEXT", eyebrow: "OUR STORY", title: "첫 기준", paragraphs: ["첫 번째 본문입니다."] },
        ],
      },
    },
    3: {
      version: 3,
      publishedAt: "2026-09-10T09:00:00Z",
      metadata: { slug: "story", path: "/brand/story", menuLabel: "브랜드 이야기", menuVisible: true, menuOrder: 10 },
      publishedFromDraftVersion: 3,
      content: {
        seo: { title: "두 번째 브랜드 이야기 | STAY HANEUL", description: "두 번째 발행본 소개 문구입니다." },
        blocks: [
          { type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "두 번째 히어로", eyebrow: "STAY HANEUL", title: "두 번째 이야기", description: "두 번째 발행본 설명입니다.", cta: { label: "두 번째 예약", href: "/booking" } },
          { type: "TEXT", eyebrow: "OUR STORY", title: "두 번째 기준", paragraphs: ["두 번째 본문입니다."] },
        ],
      },
    },
    4: {
      version: 4,
      publishedAt: "2026-09-11T09:00:00Z",
      metadata: { slug: "story", path: "/brand/story", menuLabel: "새 브랜드 이야기", menuVisible: false, menuOrder: 20 },
      publishedFromDraftVersion: 4,
      content: {
        seo: { title: "현재 브랜드 이야기 | STAY HANEUL", description: "현재 발행본 소개 문구입니다." },
        blocks: [
          { type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "현재 히어로", eyebrow: "STAY HANEUL", title: "현재 이야기", description: "현재 발행본 설명입니다.", cta: { label: "현재 예약", href: "/booking" } },
          { type: "CTA", eyebrow: "BOOK", title: "현재 CTA", description: "현재 버튼 설명입니다.", cta: { label: "예약하기", href: "/booking" } },
        ],
      },
    },
  } as const;
  return { pageId: STORY_PAGE, base: snapshots[baseVersion as 2 | 3], compare: snapshots[compareVersion as 3 | 4] };
}

function homePageDocument(overrides: Record<string, unknown> = {}) {
  return {
    id: HOME_PAGE,
    pageType: "HOME_PAGE",
    hotelId: null,
    draftContent: {
      seo: { title: "STAY HANEUL | 홈", description: "가상의 호텔 체인 홈페이지입니다." },
      blocks: [{ type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "속초 해안", eyebrow: "STAY HANEUL", title: "홈페이지 제목", description: "홈페이지 소개입니다.", cta: { label: "객실 예약", href: "/#booking" } }],
    },
    draftVersion: 1,
    draftMetadata: { slug: "home", path: "/", menuLabel: "홈", menuVisible: false, menuOrder: 0 },
    publishedContent: {
      seo: { title: "STAY HANEUL | 홈", description: "가상의 호텔 체인 홈페이지입니다." },
      blocks: [{ type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "속초 해안", eyebrow: "STAY HANEUL", title: "홈페이지 제목", description: "홈페이지 소개입니다.", cta: { label: "객실 예약", href: "/#booking" } }],
    },
    publishedVersion: INITIAL_PUBLISHED_VERSION,
    publishedMetadata: { slug: "home", path: "/", menuLabel: "홈", menuVisible: false, menuOrder: 0 },
    lifecycleStatus: "ACTIVE",
    lifecycleVersion: 1,
    ...overrides,
  };
}

test.beforeEach(async ({ page }) => {
  let storyLifecycleStatus: "ACTIVE" | "ARCHIVED" = "ACTIVE";
  let storyLifecycleVersion = 1;
  let storyDeleted = false;
  let mediaCatalog = [
    mediaAsset(),
    mediaAsset({
      id: UPLOADED_ASSET,
      displayName: "보관 대상 제주 이미지",
      deliveryUrl: `/api/website/media/${UPLOADED_ASSET}/content`,
      mimeType: "image/jpeg",
      byteSize: 512000,
      width: 1280,
      height: 720,
      defaultAltText: "제주 해안의 오후",
      usageCount: 0,
      status: "ACTIVE",
      version: 1,
    }),
    mediaAsset({
      id: ARCHIVED_ASSET,
      displayName: "보관된 설악 이미지",
      deliveryUrl: `/api/website/media/${ARCHIVED_ASSET}/content`,
      usageCount: 0,
      status: "ARCHIVED",
      version: 2,
    }),
  ];

  await page.addInitScript(() => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({
      id: "hq-test",
      email: "hq@example.test",
      displayName: "본사 관리자",
      role: "HQ_ADMIN",
      hotelId: null,
    }));
  });
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/staff/website/content-reference", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      hotels: [
        { id: SEORAKSAN, name: "설악산", region: "강원", roomTypes: [{ id: SEORAKSAN_ROOM, name: "포레스트 스위트", maxOccupancy: 4 }] },
        { id: JEJU, name: "제주도", region: "제주", roomTypes: [{ id: JEJU_ROOM, name: "오션 스위트", maxOccupancy: 4 }] },
      ],
      pages: [],
    }),
  }));
  await page.route("**/api/staff/website/pages", (route) => {
    if (route.request().method() === "POST") {
      return route.fulfill({ contentType: "application/json", body: JSON.stringify(contentPageDocument()) });
    }
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify([{
        id: HOME_PAGE, hotelId: null, pageType: "HOME_PAGE", label: "홈", draftPath: "/", publishedPath: "/", status: "PUBLISHED", lifecycleStatus: "ACTIVE", lifecycleVersion: 1, children: [],
      }, {
        id: SEORAKSAN_ROOMS_SECTION, hotelId: SEORAKSAN, pageType: "SECTION", label: "설악산 객실", draftPath: "/stays/seoraksan/rooms", publishedPath: "/stays/seoraksan/rooms", status: "PUBLISHED", lifecycleStatus: "ACTIVE", lifecycleVersion: 1,
        children: [],
      }, {
        id: "section-stays", hotelId: null, pageType: "SECTION", label: "숙소", draftPath: "/stays", publishedPath: "/stays", status: "PUBLISHED", lifecycleStatus: "ACTIVE", lifecycleVersion: 1,
        children: [{
          id: "sokcho-page", hotelId: SOKCHO, pageType: "HOTEL_LANDING", label: "속초 랜딩", draftPath: "/stays/sokcho", publishedPath: "/stays/sokcho", status: "PUBLISHED", lifecycleStatus: "ACTIVE", lifecycleVersion: 1, children: [],
        }],
      }, {
        id: BRAND_SECTION, hotelId: null, pageType: "SECTION", label: "브랜드", draftPath: "/brand", publishedPath: "/brand", status: "PUBLISHED", lifecycleStatus: "ACTIVE", lifecycleVersion: 1, children: storyDeleted ? [] : [{
          id: STORY_PAGE, hotelId: null, pageType: "CONTENT_PAGE", label: "브랜드 이야기", draftPath: "/brand/story", publishedPath: "", status: "DRAFT", lifecycleStatus: storyLifecycleStatus, lifecycleVersion: storyLifecycleVersion, children: [],
        }],
      }]),
    });
  });
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}`, (route) => {
    if (route.request().method() === "DELETE") {
      storyDeleted = true;
      return route.fulfill({ status: 204 });
    }
    const document = contentPageDocument({ lifecycleStatus: storyLifecycleStatus, lifecycleVersion: storyLifecycleVersion });
    if (route.request().method() === "PUT") document.draftVersion = 2;
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(document) });
  });
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/publish`, (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ ...contentPageDocument(), publishedContent: contentPageDocument().draftContent, publishedVersion: 2, publishedMetadata: contentPageDocument().draftMetadata }),
  }));
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/archive`, (route) => {
    storyLifecycleStatus = "ARCHIVED";
    storyLifecycleVersion = 2;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(contentPageDocument({ lifecycleStatus: storyLifecycleStatus, lifecycleVersion: storyLifecycleVersion })),
    });
  });
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/restore`, (route) => {
    storyLifecycleStatus = "ACTIVE";
    storyLifecycleVersion = 3;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(contentPageDocument({ lifecycleStatus: storyLifecycleStatus, lifecycleVersion: storyLifecycleVersion })),
    });
  });
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/versions`, (route) => route.fulfill({ contentType: "application/json", body: "[]" }));
  await page.route("**/api/staff/website/home", (route) => {
    const document = homePageDocument();
    if (route.request().method() === "PUT") document.draftVersion = 2;
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(document) });
  });
  await page.route("**/api/staff/website/home/publish", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ ...homePageDocument(), publishedVersion: 2 }),
  }));
  await page.route("**/api/staff/website/home/versions", (route) => route.fulfill({ contentType: "application/json", body: "[]" }));
  await page.route(`**/api/staff/website/media/${BUNDLED_ASSET}/usages`, (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify([{
      pageId: HOME_PAGE,
      pageLabel: "홈",
      pagePath: "/",
      pageType: "HOME_PAGE",
      documentState: "PUBLISHED",
      fieldPath: "blocks[0].imageAssetId",
      altText: "속초 해안",
    }]),
  }));
  await page.route("**/api/staff/website/media", (route) => {
    if (route.request().method() === "POST") {
      if (route.request().postData()?.includes("실패 이미지")) {
        return route.fulfill({ status: 400, contentType: "application/json", body: JSON.stringify({ message: "PNG 또는 JPEG 이미지만 업로드할 수 있습니다." }) });
      }
      const uploaded = mediaAsset({
        id: UPLOADED_ASSET,
        displayName: "새로운 제주 이미지",
        deliveryUrl: `/api/website/media/${UPLOADED_ASSET}/content`,
        mimeType: "image/jpeg",
        byteSize: 512000,
        width: 1280,
        height: 720,
        defaultAltText: "제주 해안의 오후",
        usageCount: 0,
        status: "ACTIVE",
        version: 1,
      });
      mediaCatalog = [uploaded, ...mediaCatalog.filter((asset) => asset.id !== uploaded.id)];
      return route.fulfill({ contentType: "application/json", body: JSON.stringify(uploaded) });
    }
    return route.fulfill({ contentType: "application/json", body: JSON.stringify([mediaAsset()]) });
  });
  await page.route("**/api/staff/website/media?includeArchived=true", (route) => route.fulfill({
    contentType: "application/json", body: JSON.stringify(mediaCatalog),
  }));
  await page.route(`**/api/staff/website/media/${UPLOADED_ASSET}/usages`, (route) => route.fulfill({ contentType: "application/json", body: "[]" }));
  await page.route(`**/api/staff/website/media/${BUNDLED_ASSET}`, (route) => {
    const input = route.request().postDataJSON();
    const updated = mediaAsset({ displayName: input.displayName, defaultAltText: input.defaultAltText, version: 2 });
    mediaCatalog = mediaCatalog.map((asset) => asset.id === updated.id ? updated : asset);
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(updated) });
  });
  await page.route(`**/api/staff/website/media/${UPLOADED_ASSET}/archive`, (route) => {
    const archived = mediaAsset({
      id: UPLOADED_ASSET,
      displayName: "보관 대상 제주 이미지",
      deliveryUrl: `/api/website/media/${UPLOADED_ASSET}/content`,
      usageCount: 0,
      status: "ARCHIVED",
      version: 2,
    });
    mediaCatalog = mediaCatalog.map((asset) => asset.id === archived.id ? archived : asset);
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(archived) });
  });
  await page.route(`**/api/staff/website/media/${UPLOADED_ASSET}/restore`, (route) => {
    const restored = mediaAsset({
      id: UPLOADED_ASSET,
      displayName: "보관 대상 제주 이미지",
      deliveryUrl: `/api/website/media/${UPLOADED_ASSET}/content`,
      usageCount: 0,
      status: "ACTIVE",
      version: 3,
    });
    mediaCatalog = mediaCatalog.map((asset) => asset.id === restored.id ? restored : asset);
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(restored) });
  });
  await page.route("**/api/staff/web-content/hotels/**", (route) => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith("/versions")) {
      return route.fulfill({ contentType: "application/json", body: "[]" });
    }
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        draftContent: {
          heroAssetId: BUNDLED_ASSET, heroImage: "/images/sokcho-coast-hero.png", heroAlt: "속초 해안", eyebrow: "SOKCHO · EAST SEA",
          title: "속초 제목", description: "동해를 담은 휴식",
          arrival: { address: "가상 해안로 186", checkInOut: "15:00 / 11:00", highlight: "바다 곁의 하루" },
          experiences: [
            { category: "ROOM", title: "수평선을 향한 객실", description: "바다를 담은 객실" },
            { category: "DINING", title: "동해의 식탁", description: "제철 재료를 담은 식사" },
          ],
          offers: [],
        },
        draftVersion: route.request().method() === "PUT" ? 2 : 1,
        publishedContent: { hero: { eyebrow: "속초" } },
        publishedVersion: INITIAL_PUBLISHED_VERSION,
      }),
    });
  });
});

test("edits a landing block and requires saving before publishing", async ({ page }) => {
  await page.goto("/dashboard/website");

  await expect(page.getByRole("navigation", { name: "웹사이트 페이지" })).toBeVisible();
  await expect(page.getByRole("button", { name: "속초 랜딩" })).toBeVisible();

  await page.getByLabel("주소 슬러그").fill("sokcho-spring");
  await page.getByLabel("메뉴 이름").fill("속초 봄 스테이");
  await page.getByLabel("메뉴 순서").fill("11");

  const title = page.getByLabel("히어로 제목");
  await expect(title).toHaveValue("속초 제목");
  await title.fill("속초 새 문구");

  await page.getByLabel("검색 결과 제목").fill("속초 오션 호텔 | STAY HANEUL");
  await page.getByLabel("검색 결과 설명").fill("동해와 설악을 바라보는 속초 오션 호텔의 객실과 예약 정보를 확인하세요.");
  await expect(page.getByText("속초 오션 호텔 | STAY HANEUL", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "미리보기" }).click();
  await expect(page.getByRole("dialog").getByRole("heading", { name: "속초 새 문구" })).toBeVisible();
  await expect(page.getByRole("dialog").getByAltText("속초 해안")).toHaveAttribute(
    "src",
    "http://127.0.0.1:4000/images/sokcho-coast-hero.png",
  );
  await page.getByRole("button", { name: "Close" }).click();

  await page.getByRole("button", { name: "경험 2 위로 이동" }).click();
  await expect(page.getByLabel("경험 1 제목")).toHaveValue("동해의 식탁");

  await expect(page.getByRole("button", { name: "발행" })).toBeDisabled();
  const savedRequest = page.waitForRequest((request) => request.method() === "PUT" && request.url().includes(SOKCHO));
  await page.getByRole("button", { name: "초안 저장" }).click();
  const savedContent = (await savedRequest).postDataJSON().content;
  expect(savedContent.title).toBe("속초 새 문구");
  expect(savedContent.experiences[0].title).toBe("동해의 식탁");
  expect(savedContent.seo).toEqual({
    title: "속초 오션 호텔 | STAY HANEUL",
    description: "동해와 설악을 바라보는 속초 오션 호텔의 객실과 예약 정보를 확인하세요.",
  });
  expect((await savedRequest).postDataJSON().page).toEqual({
    slug: "sokcho-spring",
    menuLabel: "속초 봄 스테이",
    menuVisible: true,
    menuOrder: 11,
  });
  await expect(page.getByRole("button", { name: "발행" })).toBeEnabled();
});

test("confirms before discarding landing edits and clears landing dirtiness after switching pages", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByLabel("히어로 제목").fill("저장 전 랜딩 문구");
  await expect(page.getByText("저장되지 않은 변경사항이 있습니다.", { exact: true })).toBeVisible();

  await page.getByRole("button", { name: "브랜드 이야기" }).click();
  await expect(page.getByRole("alertdialog")).toBeVisible();
  await page.getByRole("button", { name: "변경 버리고 이동" }).click();
  await expect(page.getByRole("heading", { name: "일반 콘텐츠 페이지" })).toBeVisible();

  await page.getByRole("button", { name: "속초 랜딩" }).click();
  await expect(page.getByLabel("영문 지점 표기")).toBeVisible();
  await expect(page.getByRole("alertdialog")).toHaveCount(0);
});

test("creates, saves, and enables publishing a structured content page", async ({ page }) => {
  await page.goto("/dashboard/website");

  await page.getByRole("button", { name: "+ 페이지" }).click();
  const dialog = page.getByRole("dialog");
  await dialog.getByRole("combobox", { name: "상위 섹션" }).click();
  await page.getByRole("option", { name: "브랜드" }).click();
  await dialog.getByLabel("메뉴 이름").fill("브랜드 이야기");
  await dialog.getByLabel("주소 슬러그").fill("story");
  await dialog.getByLabel("메뉴 순서").fill("10");

  const createRequest = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/api/staff/website/pages"));
  await dialog.getByRole("button", { name: "페이지 만들기" }).click();
  expect((await createRequest).postDataJSON()).toEqual(expect.objectContaining({
    parentId: BRAND_SECTION,
    contentKind: "BRAND",
    hotelId: null,
    connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] },
    page: { slug: "story", menuLabel: "브랜드 이야기", menuVisible: true, menuOrder: 10 },
    content: expect.objectContaining({ seo: expect.objectContaining({ title: "브랜드 이야기" }), blocks: expect.arrayContaining([expect.objectContaining({ type: "HERO", title: "브랜드 이야기" })]) }),
  }));

  await expect(page.getByRole("heading", { name: "일반 콘텐츠 페이지" })).toBeVisible();
  await expect(page.getByRole("button", { name: "발행" })).toBeDisabled();
  await page.getByLabel("히어로 제목").fill("STAY HANEUL의 이야기");
  await page.getByRole("button", { name: "텍스트 블록 추가" }).click();
  await page.getByLabel("텍스트 1 제목").fill("우리가 만드는 휴식");
  await page.getByRole("button", { name: "CTA 블록 추가" }).click();
  await expect(page.getByRole("button", { name: "히어로 위로 이동" })).toHaveCount(0);
  await page.getByRole("button", { name: "CTA 1 위로 이동" }).click();
  await expect(page.getByRole("button", { name: "CTA 1 위로 이동" })).toBeDisabled();
  await expect(page.getByLabel("히어로 제목")).toHaveValue("STAY HANEUL의 이야기");

  const saveRequest = page.waitForRequest((request) => request.method() === "PUT" && request.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}`));
  await page.getByRole("button", { name: "초안 저장" }).click();
  const saved = (await saveRequest).postDataJSON();
  expect(saved.expectedDraftVersion).toBe(1);
  expect(saved.content.blocks).toEqual(expect.arrayContaining([
    expect.objectContaining({ type: "HERO", title: "STAY HANEUL의 이야기" }),
    expect.objectContaining({ type: "TEXT", title: "우리가 만드는 휴식" }),
    expect.objectContaining({ type: "CTA", cta: { label: "자세히 보기", href: "/" } }),
  ]));
  await expect(page.getByRole("button", { name: "발행" })).toBeEnabled();
  const publishRequest = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}/publish`));
  await page.getByRole("button", { name: "발행" }).click();
  expect((await publishRequest).postDataJSON()).toEqual({ expectedDraftVersion: 2, expectedPublishedVersion: INITIAL_PUBLISHED_VERSION });
});

test("creates typed pages with scoped references", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "+ 페이지" }).click();
  const dialog = page.getByRole("dialog", { name: "콘텐츠 페이지 만들기" });

  await dialog.getByLabel("콘텐츠 유형").click();
  await page.getByRole("option", { name: "객실" }).click();
  await dialog.getByLabel("상위 섹션").click();
  await expect(page.getByRole("option", { name: "브랜드" })).toHaveCount(0);
  await page.getByRole("option", { name: "설악산 객실" }).click();
  await dialog.getByLabel("객실 유형").click();
  await expect(page.getByRole("option", { name: "포레스트 스위트" })).toBeVisible();
  await expect(page.getByRole("option", { name: "오션 스위트" })).toHaveCount(0);
  await page.getByRole("option", { name: "포레스트 스위트" }).click();
  await dialog.getByLabel("메뉴 이름").fill("설악 포레스트 스위트");
  await dialog.getByLabel("주소 슬러그").fill("forest-suite");

  const createRequest = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/api/staff/website/pages"));
  await dialog.getByRole("button", { name: "페이지 만들기" }).click();
  const created = (await createRequest).postDataJSON();
  expect(created).toEqual(expect.objectContaining({
    parentId: SEORAKSAN_ROOMS_SECTION,
    contentKind: "ROOM",
    hotelId: SEORAKSAN,
    connections: { roomTypeIds: [SEORAKSAN_ROOM], targetHotelIds: [], relatedPages: [] },
    page: { slug: "forest-suite", menuLabel: "설악 포레스트 스위트", menuVisible: true, menuOrder: 0 },
  }));
  expect(JSON.stringify(created)).not.toMatch(/price|inventory/i);

  await page.getByRole("button", { name: "+ 페이지" }).click();
  const promotionDialog = page.getByRole("dialog", { name: "콘텐츠 페이지 만들기" });
  await promotionDialog.getByLabel("콘텐츠 유형").click();
  await page.getByRole("option", { name: "프로모션" }).click();
  await promotionDialog.getByLabel("상위 섹션").click();
  await page.getByRole("option", { name: "브랜드" }).click();
  await promotionDialog.getByLabel("메뉴 이름").fill("가을 휴식");
  await promotionDialog.getByLabel("주소 슬러그").fill("autumn-escape");
  await expect(promotionDialog.getByRole("button", { name: "페이지 만들기" })).toBeDisabled();
  await expect(promotionDialog.getByText("대상 지점을 하나 이상 선택해 주세요.", { exact: true })).toBeVisible();
});

test("edits room and promotion connections with typed preview", async ({ page }) => {
  await page.route("**/api/staff/website/pages", async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    const request = route.request().postDataJSON();
    const room = request.contentKind === "ROOM";
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(typedPageDocument({
        id: room ? ROOM_PAGE : PROMOTION_PAGE,
        contentKind: room ? "ROOM" : "PROMOTION",
        hotelId: room ? SEORAKSAN : null,
        draftConnections: room
          ? { roomTypeIds: [SEORAKSAN_ROOM], targetHotelIds: [], relatedPages: [] }
          : { roomTypeIds: [SEORAKSAN_ROOM], targetHotelIds: [SEORAKSAN, JEJU], relatedPages: [] },
      })),
    });
  });

  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "+ 페이지" }).click();
  let dialog = page.getByRole("dialog", { name: "콘텐츠 페이지 만들기" });
  await dialog.getByLabel("콘텐츠 유형").click();
  await page.getByRole("option", { name: "객실" }).click();
  await dialog.getByLabel("상위 섹션").click();
  await page.getByRole("option", { name: "설악산 객실" }).click();
  await dialog.getByLabel("객실 유형").click();
  await page.getByRole("option", { name: "포레스트 스위트" }).click();
  await dialog.getByLabel("메뉴 이름").fill("설악 포레스트 스위트");
  await dialog.getByLabel("주소 슬러그").fill("forest-suite-editor");
  await dialog.getByRole("button", { name: "페이지 만들기" }).click();

  await page.getByLabel("연결 객실 유형").selectOption(SEORAKSAN_ROOM);
  await page.getByRole("button", { name: "항목 추가" }).click();
  await page.getByLabel("예약 CTA 문구").fill("예약 조건 확인");
  await expect(page.getByLabel("예약 CTA 대상 지점")).toHaveValue(SEORAKSAN);
  await page.getByRole("button", { name: "미리보기", exact: true }).click();
  let preview = page.getByRole("dialog", { name: "일반 페이지 미리보기" });
  await preview.getByRole("button", { name: "모바일 390px", exact: true }).click();
  await expect(preview.locator("article")).toHaveCSS("width", "390px");
  await expect(preview.getByRole("button", { name: "예약 조건 확인" })).toBeDisabled();
  await preview.getByRole("button", { name: "닫기", exact: true }).click();

  const roomSave = page.waitForRequest((request) => request.method() === "PUT" && request.url().endsWith(`/api/staff/website/pages/${ROOM_PAGE}`));
  await page.getByRole("button", { name: "초안 저장" }).click();
  expect((await roomSave).postDataJSON()).toEqual(expect.objectContaining({ connections: { roomTypeIds: [SEORAKSAN_ROOM], targetHotelIds: [], relatedPages: [] } }));

  await page.getByRole("button", { name: "+ 페이지" }).click();
  dialog = page.getByRole("dialog", { name: "콘텐츠 페이지 만들기" });
  await dialog.getByLabel("콘텐츠 유형").click();
  await page.getByRole("option", { name: "프로모션" }).click();
  await dialog.getByLabel("상위 섹션").click();
  await page.getByRole("option", { name: "브랜드" }).click();
  await dialog.getByText("설악산", { exact: true }).click();
  await dialog.getByText("제주도", { exact: true }).click();
  await dialog.getByLabel("메뉴 이름").fill("가을 휴식");
  await dialog.getByLabel("주소 슬러그").fill("autumn-editor");
  await dialog.getByRole("button", { name: "페이지 만들기" }).click();

  await page.getByRole("checkbox", { name: /대상 지점 제주도/ }).first().click();
  await page.getByLabel("연결 객실 유형").selectOption(JEJU_ROOM);
  await expect(page.getByText("선택한 객실 유형은 대상 지점에 포함되어야 합니다.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "초안 저장" })).toBeDisabled();
  await page.getByRole("button", { name: "미리보기", exact: true }).click();
  preview = page.getByRole("dialog", { name: "일반 페이지 미리보기" });
  await expect(preview).toContainText("표시 정보이며 실제 예약 가격은 선택 조건에서 다시 계산됩니다.");
  await expect(preview).not.toContainText("실시간 가격");
});

test("previews unsaved content page edits without changing server state", async ({ page }) => {
  const mutations: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes("/api/staff/website/") && request.method() !== "GET") {
      mutations.push(`${request.method()} ${request.url()}`);
    }
  });

  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();

  await page.getByLabel("히어로 제목").fill("저장 전 미리보기 제목");
  await page.getByRole("button", { name: "텍스트 블록 추가" }).click();
  await page.getByLabel("텍스트 1 제목").fill("저장 전 본문 제목");
  await page.getByLabel("텍스트 1 본문 1").fill("저장하지 않은 본문도 미리보기에서 확인합니다.");

  const previewButton = page.getByRole("button", { name: "미리보기", exact: true });
  await previewButton.click();
  const dialog = page.getByRole("dialog", { name: "일반 페이지 미리보기" });
  await expect(dialog).toContainText("저장하거나 발행하지 않은 현재 편집 내용을 기준으로 표시합니다.");
  await expect(dialog.getByRole("heading", { name: "저장 전 미리보기 제목" })).toBeVisible();
  await expect(dialog.getByRole("heading", { name: "저장 전 본문 제목" })).toBeVisible();
  await expect(dialog).toContainText("저장하지 않은 본문도 미리보기에서 확인합니다.");
  await expect(dialog.getByAltText("브랜드 이야기")).toHaveAttribute(
    "src",
    "http://127.0.0.1:4000/images/sokcho-coast-hero.png",
  );

  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(previewButton).toBeFocused();
  await previewButton.click();
  await page.keyboard.press("Escape");
  await expect(previewButton).toBeFocused();
  expect(mutations).toEqual([]);
});

test("switches the content page preview to a 390px mobile frame", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();
  await page.getByRole("button", { name: "미리보기", exact: true }).click();

  const preview = page.getByRole("dialog", { name: "일반 페이지 미리보기" });
  const mobileButton = preview.getByRole("button", { name: "모바일 390px", exact: true });
  await mobileButton.click();

  await expect(mobileButton).toHaveAttribute("aria-pressed", "true");
  await expect(preview.locator("article")).toHaveAttribute("data-preview-viewport", "mobile");
  await expect(preview.locator("article")).toHaveCSS("width", "390px");
});

test("disables page archive until content page edits are saved", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();

  await page.getByLabel("히어로 제목").fill("저장 전 보관 확인");
  await expect(page.getByRole("button", { name: "페이지 보관", exact: true })).toBeDisabled();
});

test("archives a content page after confirmation and restores its editable draft", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();

  await expect(page.getByRole("heading", { name: "일반 콘텐츠 페이지" })).toBeVisible();
  await expect(page.getByLabel("히어로 제목")).toBeEnabled();

  const archiveRequest = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}/archive`));
  await page.getByRole("button", { name: "페이지 보관", exact: true }).click();
  const confirmation = page.getByRole("alertdialog", { name: "페이지를 보관할까요?" });
  await expect(confirmation).toContainText("고객 웹");
  await confirmation.getByRole("button", { name: "보관하기" }).click();
  expect((await archiveRequest).postDataJSON()).toEqual({
    expectedLifecycleVersion: 1,
    expectedDraftVersion: 1,
    expectedPublishedVersion: INITIAL_PUBLISHED_VERSION,
  });

  await expect(page.getByRole("button", { name: "초안으로 복원", exact: true })).toBeEnabled();
  await expect(page.getByRole("navigation", { name: "웹사이트 페이지" }).getByText("보관", { exact: true })).toBeVisible();
  await expect(page.getByLabel("히어로 제목")).toBeDisabled();
  await expect(page.getByRole("button", { name: "초안 저장", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "발행", exact: true })).toBeDisabled();

  const restoreRequest = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}/restore`));
  await page.getByRole("button", { name: "초안으로 복원", exact: true }).click();
  expect((await restoreRequest).postDataJSON()).toEqual({
    expectedLifecycleVersion: 2,
    expectedDraftVersion: 1,
    expectedPublishedVersion: INITIAL_PUBLISHED_VERSION,
  });

  await expect(page.getByRole("button", { name: "페이지 보관", exact: true })).toBeEnabled();
  await expect(page.getByLabel("히어로 제목")).toBeEnabled();
  await page.getByLabel("히어로 제목").fill("복원 뒤 수정한 제목");
  await expect(page.getByRole("button", { name: "초안 저장", exact: true })).toBeEnabled();
});

test("permanently deletes an archived content page after confirmation", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();
  await expect(page.getByRole("button", { name: "영구 삭제", exact: true })).toHaveCount(0);

  const archiveRequest = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}/archive`));
  await page.getByRole("button", { name: "페이지 보관", exact: true }).click();
  await page.getByRole("alertdialog", { name: "페이지를 보관할까요?" }).getByRole("button", { name: "보관하기" }).click();
  await archiveRequest;

  await page.getByRole("button", { name: "영구 삭제", exact: true }).click();
  const confirmation = page.getByRole("alertdialog", { name: "보관된 페이지를 영구 삭제할까요?" });
  await expect(confirmation).toContainText("발행 이력");
  await expect(confirmation).toContainText("되돌릴 수 없습니다");
  const deleteRequest = page.waitForRequest((request) => request.method() === "DELETE" && request.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}`));
  await confirmation.getByRole("button", { name: "영구 삭제", exact: true }).click();
  expect((await deleteRequest).postDataJSON()).toEqual({
    expectedLifecycleVersion: 2,
    expectedDraftVersion: 1,
    expectedPublishedVersion: INITIAL_PUBLISHED_VERSION,
  });

  await expect(page.getByRole("heading", { name: "홈페이지 콘텐츠" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "웹사이트 페이지" }).getByRole("button", { name: "브랜드 이야기" })).toHaveCount(0);
});

test("restores an earlier publication into a content page draft only after confirmation", async ({ page }) => {
  const earlierPublishedContent = {
    seo: { title: "이전 브랜드 이야기 | STAY HANEUL", description: "이전 발행본의 소개 문구입니다." },
    blocks: [{ type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "이전 브랜드 이야기", eyebrow: "STAY HANEUL", title: "이전 발행본 제목", description: "이전 발행본의 설명입니다." }],
  };
  const currentDocument = contentPageDocument({
    draftContent: {
      seo: { title: "현재 초안 | STAY HANEUL", description: "현재 초안의 소개 문구입니다." },
      blocks: [{ type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "현재 초안", eyebrow: "STAY HANEUL", title: "현재 초안 제목", description: "현재 초안의 설명입니다." }],
    },
    draftVersion: 4,
    publishedContent: {
      seo: { title: "현재 공개 | STAY HANEUL", description: "현재 공개본의 소개 문구입니다." },
      blocks: [{ type: "HERO", imageAssetId: BUNDLED_ASSET, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "현재 공개", eyebrow: "STAY HANEUL", title: "현재 공개 제목", description: "현재 공개본의 설명입니다." }],
    },
    publishedVersion: 3,
    publishedMetadata: { slug: "story", path: "/brand/story", menuLabel: "브랜드 이야기", menuVisible: true, menuOrder: 10 },
  });
  const restoredDocument = { ...currentDocument, draftContent: earlierPublishedContent, draftVersion: 5 };
  const versions = [
    { version: 3, publishedAt: "2026-09-11T09:00:00Z" },
    { version: 2, publishedAt: "2026-09-10T09:00:00Z" },
  ];

  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/versions`, (route) => route.fulfill({
    contentType: "application/json", body: JSON.stringify(versions),
  }));
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}`, (route) => route.fulfill({
    contentType: "application/json", body: JSON.stringify(currentDocument),
  }));
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/versions/2/restore-draft`, (route) => route.fulfill({
    contentType: "application/json", body: JSON.stringify(restoredDocument),
  }));

  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();

  const restoreButton = page.getByRole("button", { name: "발행본 v2 콘텐츠를 초안으로 복원" });
  await expect(restoreButton).toBeEnabled();
  await page.getByLabel("히어로 제목").fill("저장 전 복원 차단 확인");
  await expect(restoreButton).toBeDisabled();
  await expect(page.getByText("저장되지 않은 변경사항을 먼저 초안으로 저장한 뒤 이전 발행본 콘텐츠를 복원할 수 있습니다.", { exact: true })).toBeVisible();

  await page.reload();
  await page.getByRole("button", { name: "브랜드 이야기" }).click();
  const request = page.waitForRequest((item) => item.method() === "POST" && item.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}/versions/2/restore-draft`));
  await page.getByRole("button", { name: "발행본 v2 콘텐츠를 초안으로 복원" }).click();
  const confirmation = page.getByRole("alertdialog", { name: "발행본을 초안으로 복원할까요?" });
  await expect(confirmation).toContainText("현재 저장된 초안 콘텐츠가 선택한 발행본 콘텐츠로 대체됩니다.");
  await expect(confirmation).toContainText("고객 웹");
  await confirmation.getByRole("button", { name: "초안으로 복원" }).click();
  expect((await request).postDataJSON()).toEqual({
    expectedLifecycleVersion: 1,
    expectedDraftVersion: 4,
    expectedPublishedVersion: 3,
  });

  await expect(page.getByLabel("히어로 제목")).toHaveValue("이전 발행본 제목");
  await expect(page.getByRole("status")).toContainText("발행본 v2의 콘텐츠를 초안으로 복원했습니다");
  await expect(page.getByRole("button", { name: "발행", exact: true })).toBeEnabled();
});

test("compares content page publications in a read-only dialog", async ({ page }) => {
  const currentDocument = contentPageDocument({
    draftVersion: 4,
    publishedVersion: 4,
    publishedContent: contentPageDocument().draftContent,
    publishedMetadata: { slug: "story", path: "/brand/story", menuLabel: "새 브랜드 이야기", menuVisible: false, menuOrder: 20 },
  });
  const versions = [
    { version: 4, publishedAt: "2026-09-11T09:00:00Z" },
    { version: 3, publishedAt: "2026-09-10T09:00:00Z" },
    { version: 2, publishedAt: "2026-09-09T09:00:00Z" },
  ];
  const comparisonRequests: string[] = [];
  const mutations: string[] = [];
  page.on("request", (request) => {
    if (request.url().includes(`/api/staff/website/pages/${STORY_PAGE}`) && request.method() !== "GET") {
      mutations.push(`${request.method()} ${request.url()}`);
    }
  });
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/versions/compare?**`, (route) => {
    comparisonRequests.push(route.request().url());
    const url = new URL(route.request().url());
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(contentPageVersionComparison(Number(url.searchParams.get("baseVersion")), Number(url.searchParams.get("compareVersion")))),
    });
  });
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}/versions`, (route) => route.fulfill({
    contentType: "application/json", body: JSON.stringify(versions),
  }));
  await page.route(`**/api/staff/website/pages/${STORY_PAGE}`, (route) => route.fulfill({
    contentType: "application/json", body: JSON.stringify(currentDocument),
  }));

  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();

  const compareButton = page.getByRole("button", { name: "발행본 v2와 현재 발행본 비교" });
  await compareButton.click();
  const dialog = page.getByRole("dialog", { name: "발행본 비교" });
  await expect(dialog).toContainText("읽기 전용이며 초안과 고객 웹을 변경하지 않습니다.");
  await expect(dialog).toContainText("첫 번째 이야기");
  await expect(dialog).toContainText("현재 이야기");
  await expect(dialog).toContainText("변경");
  await expect(dialog).toContainText("제거");
  await expect.poll(() => comparisonRequests.length).toBe(1);
  expect(new URL(comparisonRequests[0]).searchParams.toString()).toBe("baseVersion=2&compareVersion=4");

  await dialog.getByLabel("비교 발행본").click();
  await page.getByRole("option", { name: "발행본 v3" }).click();
  await expect.poll(() => comparisonRequests.length).toBe(2);
  expect(new URL(comparisonRequests[1]).searchParams.toString()).toBe("baseVersion=2&compareVersion=3");

  await dialog.getByRole("button", { name: "닫기", exact: true }).click();
  await expect(compareButton).toBeFocused();
  await compareButton.click();
  await page.keyboard.press("Escape");
  await expect(compareButton).toBeFocused();
  expect(mutations).toEqual([]);
});

test("edits the fixed homepage through its dedicated CMS endpoints", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "홈" }).click();

  await expect(page.getByRole("heading", { name: "홈페이지 콘텐츠" })).toBeVisible();
  await expect(page.getByLabel("주소 슬러그")).toHaveCount(0);
  await page.getByLabel("히어로 제목").fill("홈페이지 새 문구");
  await expect(page.getByRole("button", { name: "발행" })).toBeDisabled();

  const saveRequest = page.waitForRequest((request) => request.method() === "PUT" && request.url().endsWith("/api/staff/website/home"));
  await page.getByRole("button", { name: "초안 저장" }).click();
  const saved = (await saveRequest).postDataJSON();
  expect(saved).toEqual({
    expectedDraftVersion: 1,
    content: expect.objectContaining({ blocks: expect.arrayContaining([expect.objectContaining({ type: "HERO", title: "홈페이지 새 문구" })]) }),
  });
  await expect(page.getByRole("button", { name: "발행" })).toBeEnabled();

  const publishRequest = page.waitForRequest((request) => request.method() === "POST" && request.url().endsWith("/api/staff/website/home/publish"));
  await page.getByRole("button", { name: "발행" }).click();
  expect((await publishRequest).postDataJSON()).toEqual({ expectedDraftVersion: 2, expectedPublishedVersion: INITIAL_PUBLISHED_VERSION });
});

test("edits rich content blocks and previews them in customer order", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "브랜드 이야기" }).click();
  await page.getByRole("button", { name: "갤러리 추가" }).click();
  await page.getByRole("button", { name: "사양 표 추가" }).click();
  await page.getByRole("button", { name: "FAQ 추가" }).click();
  await page.getByLabel("갤러리 1 제목").fill("객실 갤러리");
  await page.getByLabel("사양 표 1 제목").fill("객실 사양");
  await page.getByLabel("FAQ 1 제목").fill("자주 묻는 질문");
  await page.getByRole("button", { name: "미리보기" }).click();
  const preview = page.getByRole("dialog", { name: "일반 페이지 미리보기" });
  await expect(preview).toContainText("객실 갤러리");
  await expect(preview).toContainText("객실 사양");
  await expect(preview).toContainText("자주 묻는 질문");
  await preview.getByRole("button", { name: "닫기" }).click();
  const request = page.waitForRequest((item) => item.method() === "PUT" && item.url().endsWith(`/api/staff/website/pages/${STORY_PAGE}`));
  await page.getByRole("button", { name: "초안 저장" }).click();
  expect((await request).postDataJSON().content.blocks).toEqual(expect.arrayContaining([
    expect.objectContaining({ type: "IMAGE_GALLERY", title: "객실 갤러리" }),
    expect.objectContaining({ type: "SPEC_TABLE", title: "객실 사양" }),
    expect.objectContaining({ type: "ACCORDION", title: "자주 묻는 질문" }),
  ]));
});

test("selects a catalog asset for a structured page and saves its identifier", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "홈" }).click();

  await page.getByRole("button", { name: "미디어 선택" }).click();
  const dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await expect(dialog.getByText("속초 해안 대표 이미지", { exact: true })).toBeVisible();
  await dialog.getByRole("button", { name: "속초 해안 대표 이미지 선택" }).click();
  await expect(dialog.getByText("사용 위치", { exact: true })).toBeVisible();
  await expect(dialog.getByText("홈 · 발행", { exact: true })).toBeVisible();
  await dialog.getByRole("button", { name: "선택", exact: true }).click();
  await expect(page.getByLabel("대표 이미지 대체 텍스트")).toHaveValue("동해와 설악산을 바라보는 속초 해안");

  const saveRequest = page.waitForRequest((request) => request.method() === "PUT" && request.url().endsWith("/api/staff/website/home"));
  await page.getByRole("button", { name: "초안 저장" }).click();
  expect((await saveRequest).postDataJSON().content.blocks[0]).toMatchObject({
    imageAssetId: BUNDLED_ASSET,
    imageSrc: "/images/sokcho-coast-hero.png",
    imageAlt: "동해와 설악산을 바라보는 속초 해안",
  });
});

test("uploads an asset for a landing page and keeps its page-specific alt text", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  const dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByLabel("이미지 파일").setInputFiles({
    name: "jeju.jpg",
    mimeType: "image/jpeg",
    buffer: Buffer.from("mock-jpeg"),
  });
  await dialog.getByLabel("자산명").fill("새로운 제주 이미지");
  await dialog.getByLabel("기본 대체 텍스트", { exact: true }).fill("제주 해안의 오후");
  await dialog.getByRole("button", { name: "업로드" }).click();
  await expect(dialog.getByRole("status")).toContainText("업로드되었습니다");
  await dialog.getByRole("button", { name: "새로운 제주 이미지 선택" }).click();
  await dialog.getByRole("button", { name: "선택", exact: true }).click();
  await page.getByLabel("대표 이미지 대체 텍스트").fill("석양을 바라보는 제주 스테이");

  const saveRequest = page.waitForRequest((request) => request.method() === "PUT" && request.url().includes(SOKCHO));
  await page.getByRole("button", { name: "초안 저장" }).click();
  expect((await saveRequest).postDataJSON().content).toMatchObject({
    heroAssetId: UPLOADED_ASSET,
    heroImage: `/api/website/media/${UPLOADED_ASSET}/content`,
    heroAlt: "석양을 바라보는 제주 스테이",
  });
});

test("shows the upload validation error without changing the selected page image", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  const dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByLabel("이미지 파일").setInputFiles({
    name: "invalid.svg",
    mimeType: "image/svg+xml",
    buffer: Buffer.from("invalid"),
  });
  await dialog.getByLabel("자산명").fill("실패 이미지");
  await dialog.getByLabel("기본 대체 텍스트", { exact: true }).fill("유효하지 않은 파일");
  await dialog.getByRole("button", { name: "업로드" }).click();
  await expect(dialog.getByRole("alert")).toContainText("PNG 또는 JPEG 이미지만 업로드할 수 있습니다.");
  await expect(page.getByLabel("대표 이미지 대체 텍스트")).toHaveValue("속초 해안");
});

test("updates selected catalog metadata at the asset version and protects used media from archive", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  const dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "속초 해안 대표 이미지 선택" }).click();

  await dialog.getByLabel("선택한 자산 이름").fill("속초 해안 저녁 이미지");
  await dialog.getByLabel("선택한 자산 기본 대체 텍스트").fill("저녁빛 속초 해안");
  const request = page.waitForRequest((item) => item.method() === "PATCH" && item.url().endsWith(`/api/staff/website/media/${BUNDLED_ASSET}`));
  await dialog.getByRole("button", { name: "자산 정보 저장" }).click();
  expect((await request).postDataJSON()).toEqual({
    displayName: "속초 해안 저녁 이미지",
    defaultAltText: "저녁빛 속초 해안",
    expectedVersion: 1,
  });
  await expect(dialog.getByRole("status")).toContainText("자산 정보를 저장했습니다");
  await expect(dialog.getByRole("button", { name: "보관", exact: true })).toBeDisabled();
  await expect(dialog.getByText("사용 위치가 있어 보관할 수 없습니다.")).toBeVisible();
});

test("archives an unused catalog asset only after confirmation and restores it", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  const dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "보관 대상 제주 이미지 선택" }).click();

  const archiveRequest = page.waitForRequest((item) => item.method() === "POST" && item.url().endsWith(`/api/staff/website/media/${UPLOADED_ASSET}/archive`));
  await dialog.getByRole("button", { name: "보관", exact: true }).click();
  const confirmation = page.getByRole("alertdialog", { name: "미디어를 보관할까요?" });
  await confirmation.getByRole("button", { name: "보관하기" }).click();
  expect((await archiveRequest).postDataJSON()).toEqual({ expectedVersion: 1 });
  await expect(dialog.getByText("보관됨 · 버전 2 · 사용 위치 0곳", { exact: true })).toBeVisible();
  await expect(dialog.getByRole("button", { name: "선택", exact: true })).toBeDisabled();

  const restoreRequest = page.waitForRequest((item) => item.method() === "POST" && item.url().endsWith(`/api/staff/website/media/${UPLOADED_ASSET}/restore`));
  await dialog.getByRole("button", { name: "복원" }).click();
  expect((await restoreRequest).postDataJSON()).toEqual({ expectedVersion: 2 });
  await expect(dialog.getByText("활성 · 버전 3 · 사용 위치 0곳", { exact: true })).toBeVisible();
});

test("discards unsaved catalog metadata when the picker closes", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  let dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "속초 해안 대표 이미지 선택" }).click();
  await dialog.getByLabel("선택한 자산 이름").fill("저장하지 않은 이름");
  await dialog.getByLabel("선택한 자산 기본 대체 텍스트").fill("저장하지 않은 대체 텍스트");
  await dialog.getByRole("button", { name: "취소" }).click();

  await page.getByRole("button", { name: "미디어 선택" }).click();
  dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "속초 해안 대표 이미지 선택" }).click();
  await expect(dialog.getByLabel("선택한 자산 이름")).toHaveValue("속초 해안 대표 이미지");
  await expect(dialog.getByLabel("선택한 자산 기본 대체 텍스트")).toHaveValue("동해와 설악산을 바라보는 속초 해안");
});

test("protects the current unsaved page asset from archive", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  let dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "보관 대상 제주 이미지 선택" }).click();
  await dialog.getByRole("button", { name: "선택", exact: true }).click();

  await page.getByRole("button", { name: "미디어 선택" }).click();
  dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "보관 대상 제주 이미지 선택" }).click();
  await expect(dialog.getByRole("button", { name: "보관", exact: true })).toBeDisabled();
  await expect(dialog.getByText("현재 페이지의 저장되지 않은 초안에서 선택되어 보관할 수 없습니다.")).toBeVisible();
});

test("shows a placeholder instead of loading a preview for an archived upload", async ({ page }) => {
  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  const dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "보관 대상 제주 이미지 선택" }).click();
  await dialog.getByRole("button", { name: "보관", exact: true }).click();
  await page.getByRole("alertdialog", { name: "미디어를 보관할까요?" }).getByRole("button", { name: "보관하기" }).click();

  await expect(dialog.getByRole("button", { name: "보관 대상 제주 이미지 선택" }).getByText("보관된 업로드 자산은 미리보기를 표시하지 않습니다.")).toBeVisible();
});

test("reloads the current catalog after a media version conflict", async ({ page }) => {
  let catalogRequestCount = 0;
  await page.route("**/api/staff/website/media?includeArchived=true", (route) => {
    catalogRequestCount += 1;
    const current = catalogRequestCount === 1
      ? mediaAsset()
      : mediaAsset({ displayName: "다른 관리자의 속초 이미지", defaultAltText: "다른 관리자가 저장한 대체 텍스트", usageCount: 1, version: 2 });
    return route.fulfill({ contentType: "application/json", body: JSON.stringify([current]) });
  });
  await page.route(`**/api/staff/website/media/${BUNDLED_ASSET}`, (route) => route.fulfill({
    status: 409,
    contentType: "application/json",
    body: JSON.stringify({ code: "WEBSITE_MEDIA_VERSION_CONFLICT", message: "다른 관리자가 자산을 변경했습니다." }),
  }));

  await page.goto("/dashboard/website");
  await page.getByRole("button", { name: "미디어 선택" }).click();
  const dialog = page.getByRole("dialog", { name: "미디어 선택" });
  await dialog.getByRole("button", { name: "속초 해안 대표 이미지 선택" }).click();
  await dialog.getByLabel("선택한 자산 이름").fill("오래된 편집값");
  await dialog.getByRole("button", { name: "자산 정보 저장" }).click();

  await expect(dialog.getByLabel("선택한 자산 이름")).toHaveValue("다른 관리자의 속초 이미지");
  await expect(dialog.getByLabel("선택한 자산 기본 대체 텍스트")).toHaveValue("다른 관리자가 저장한 대체 텍스트");
  await expect(dialog.getByText("최신 자산 정보를 불러왔습니다. 변경 내용을 확인한 뒤 다시 저장해 주세요.")).toBeVisible();
  await expect(dialog.getByRole("button", { name: "보관", exact: true })).toBeDisabled();
});
