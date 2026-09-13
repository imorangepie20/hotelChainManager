import { expect, test, type Page } from "@playwright/test";

const TOKEN = "P".repeat(43);
const hero = { blockId: "15000000-0000-0000-0000-000000000001", type: "HERO", imageAssetId: "14000000-0000-0000-0000-000000000001", imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "해안", eyebrow: "STAY", title: "저장 초안", description: "검토용 문서", cta: { label: "초안 CTA", href: "/#booking" } };
async function mockPreview(page: Page, options: { status?: number; expiresIn?: number; type?: string; locale?: string; kind?: string; blocks?: unknown[]; hotelFailure?: boolean } = {}) {
  const calls = { preview: 0, public: 0, mutation: 0, availability: 0, recovered: 0 };
  await page.addInitScript(() => { sessionStorage.setItem("latestReservation", "real-reservation"); sessionStorage.setItem("reservation:real-reservation", "preserve-me"); });
  await page.route("**/api/**", async route => {
    const request = route.request(), url = new URL(request.url());
    if (request.method() !== "GET") calls.mutation++;
    if (url.pathname.startsWith("/api/reservations")) calls.recovered++;
    if (url.pathname.startsWith("/api/reservations")) return route.fulfill({ status: 404, json: { code: "RESERVATION_NOT_FOUND", message: "검증용 예약 없음" } });
    if (url.pathname === "/api/availability") calls.availability++;
    if (url.pathname === "/api/hotels") return route.fulfill({ status: options.hotelFailure ? 503 : 200, json: options.hotelFailure ? { code: "UNAVAILABLE", message: "검증용 호텔 조회 실패" } : [{ id: "hotel", name: "호텔", region: "속초", timezone: "Asia/Seoul" }] });
    if (url.pathname === "/api/website/navigation") return route.fulfill({ json: [{ id: "brand", hotelId: null, label: "브랜드", path: "/brand", children: [{ id: "other", hotelId: null, label: "공개 이야기", path: "/brand/other", children: [] }] }] });
    if (url.pathname === "/api/website/pages/preview") {
      calls.preview++;
      expect(request.headers()["x-website-preview"]).toBe(TOKEN);
      expect(request.headers()["x-reservation-token"]).toBeUndefined();
      expect(url.searchParams.get("locale")).toBe(options.locale ?? "ko");
      if (options.status) return route.fulfill({ status: options.status, json: { code: "WEBSITE_PREVIEW_UNAVAILABLE", message: "만료" } });
      const type = options.type ?? "CONTENT_PAGE";
      return route.fulfill({ headers: { "Cache-Control": "no-store", "X-Website-Preview-Expires-At": new Date(Date.now() + (options.expiresIn ?? 600_000)).toISOString() }, json: {
        id: "draft", type, contentKind: options.kind ?? (type === "HOME_PAGE" ? "HOME" : type === "HOTEL_LANDING" ? "DESTINATION" : "BRAND"), path: url.searchParams.get("path"), hotelId: type === "HOTEL_LANDING" ? "hotel" : null,
        connections: { roomTypeIds: [], targetHotelIds: options.kind === "EXPERIENCE" ? ["11000000-0000-0000-0000-000000000001"] : [], relatedPages: [] }, content: type === "HOTEL_LANDING" ? { title: "저장 초안", description: "검토용", eyebrow: "STAY", heroAssetId: hero.imageAssetId, heroImage: hero.imageSrc, heroAlt: hero.imageAlt, arrival: { address: "Coast", checkInOut: "15:00 / 11:00", highlight: "Sea" }, experiences: [], offers: [] } : {
          seo: { title: "검토 제목", description: "초안 SEO" }, blocks: options.blocks ?? [hero, { blockId: "15000000-0000-0000-0000-000000000002", type: "CTA", title: "안내", cta: { label: "초안 안내 CTA", href: "/brand/other" } }],
        },
      } });
    }
    if (url.pathname === "/api/website/pages/resolve") {
      calls.public++;
      return route.fulfill({ json: { id: "public", type: "CONTENT_PAGE", contentKind: "BRAND", path: "/brand/other", hotelId: null, connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, content: { seo: { title: "공개본", description: "공개 문서" }, blocks: [{ ...hero, title: "공개본" }] } } });
    }
    return route.fulfill({ json: {} });
  });
  return calls;
}

for (const width of [1280, 390]) test(`captures reloadable draft session and blocks CTA actions (${width}px)`, async ({ page }) => {
  await page.setViewportSize({ width, height: 844 });
  const calls = await mockPreview(page);
  await page.goto(`/brand/story#preview=${TOKEN}`);
  await expect(page).toHaveURL("http://127.0.0.1:4000/brand/story");
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "저장 초안 미리보기" })).toBeVisible();
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", "noindex,nofollow");
  await expect(page.getByRole("button", { name: "초안 CTA", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: "초안 안내 CTA", exact: true })).toBeDisabled();
  await page.reload();
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toBeVisible();
  expect(calls.public).toBe(0); expect(calls.mutation).toBe(0); expect(calls.recovered).toBe(0);
  expect(await page.evaluate(() => sessionStorage.getItem("reservation:real-reservation"))).toBe("preserve-me");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  if (width === 390) await page.getByRole("button", { name: "메뉴", exact: true }).click();
  await page.getByRole("link", { name: "공개 이야기", exact: true }).click();
  await expect(page.getByRole("heading", { name: "공개본", exact: true })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("website-preview"))).toBeNull();
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
});

test("does not fall back to public content when the grant is unavailable", async ({ page }) => {
  const calls = await mockPreview(page, { status: 410 });
  await page.goto(`/brand/story#preview=${TOKEN}`);
  await expect(page.getByRole("heading", { name: "미리보기를 사용할 수 없습니다.", exact: true })).toBeVisible();
  expect(calls.public).toBe(0); expect(calls.recovered).toBe(0);
  await expect(page.locator('meta[name="robots"]')).toHaveAttribute("content", "noindex,nofollow");
});

test("normal navigation to the current path exits preview before reload", async ({ page }) => {
  await mockPreview(page);
  await page.goto(`/brand/story#preview=${TOKEN}`);
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toBeVisible();
  await page.getByRole("link", { name: "KO", exact: true }).click();
  await expect(page.getByRole("heading", { name: "공개본", exact: true })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem("website-preview"))).toBeNull();
  await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
});

test("removes the draft from the screen at expiry", async ({ page }) => {
  await mockPreview(page, { expiresIn: 1500 });
  await page.goto(`/brand/story#preview=${TOKEN}`);
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "미리보기를 사용할 수 없습니다.", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toHaveCount(0);
});

for (const type of ["HOME_PAGE", "HOTEL_LANDING"]) test(`uses the normal ${type} renderer while blocking booking`, async ({ page }) => {
  const calls = await mockPreview(page, { type });
  await page.goto(`${type === "HOME_PAGE" ? "/" : "/stays/sokcho"}#preview=${TOKEN}`);
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "객실 검색", exact: true })).toBeDisabled();
  await expect(page.getByRole("button", { name: /AI 예약 도우미/ })).toBeDisabled();
  for (const control of await page.locator('.search-grid button, .search-grid input, .search-grid select').all()) await expect(control).toBeDisabled();
  expect(calls.public).toBe(0); expect(calls.availability).toBe(0); expect(calls.mutation).toBe(0); expect(calls.recovered).toBe(0);
});

for (const type of ["HOME_PAGE", "HOTEL_LANDING", "CONTENT_PAGE"]) test(`renders English ${type} draft, including deep CMS paths`, async ({ page }) => {
  const calls = await mockPreview(page, { type, locale: "en" });
  const path = type === "HOME_PAGE" ? "/en" : type === "HOTEL_LANDING" ? "/en/stays/sokcho" : "/en/brand/section/story";
  await page.goto(`${path}#preview=${TOKEN}`);
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toBeVisible();
  await expect(page.getByRole("region", { name: "Saved draft preview" })).toBeVisible();
  expect(calls.public).toBe(0); expect(calls.mutation).toBe(0);
});

test("blocks map and booking content actions", async ({ page }) => {
  await mockPreview(page, { kind: "GUIDE", blocks: [hero, { blockId: "15000000-0000-0000-0000-000000000002", type: "LOCATION", title: "위치", address: "해안", mapHref: "/guide/directions" }] });
  await page.goto(`/guide/map#preview=${TOKEN}`);
  await expect(page.getByRole("button", { name: "오시는 길 보기" })).toBeDisabled();
});

test("blocks booking CTA without changing reservation state", async ({ page }) => {
  const calls = await mockPreview(page, { kind: "EXPERIENCE", blocks: [hero, { blockId: "15000000-0000-0000-0000-000000000002", type: "RICH_TEXT", eyebrow: "EXPERIENCE", title: "경험", paragraphs: ["검토용"] }, { blockId: "15000000-0000-0000-0000-000000000003", type: "BOOKING_CTA", title: "예약", description: "검토용", label: "초안 예약 시작", hotelId: "11000000-0000-0000-0000-000000000001" }] });
  await page.goto(`/experiences/story#preview=${TOKEN}`);
  await expect(page.getByRole("button", { name: "초안 예약 시작" })).toBeDisabled();
  expect(calls.availability).toBe(0); expect(calls.mutation).toBe(0); expect(calls.recovered).toBe(0);
});

test("does not show fallback landing content when hotel lookup fails", async ({ page }) => {
  await mockPreview(page, { type: "HOTEL_LANDING", hotelFailure: true });
  await page.goto(`/stays/sokcho#preview=${TOKEN}`);
  await expect(page.getByRole("heading", { name: "미리보기를 사용할 수 없습니다.", exact: true })).toBeVisible();
  await expect(page.locator('.hero')).toHaveCount(0);
});

test("captures and removes the fragment even when tab storage is blocked", async ({ page }) => {
  const calls = await mockPreview(page);
  await page.addInitScript(() => Object.defineProperty(window, "sessionStorage", { configurable: true, get() { throw new DOMException("검증용 저장소 차단", "SecurityError"); } }));
  await page.goto(`/brand/story#preview=${TOKEN}`);
  await expect(page).toHaveURL("http://127.0.0.1:4000/brand/story");
  await expect(page.getByRole("heading", { name: "저장 초안", exact: true })).toBeVisible();
  await page.getByRole("button", { name: "미리보기 종료", exact: true }).click();
  await expect(page.getByRole("heading", { name: "공개본", exact: true })).toBeVisible();
  expect(calls.mutation).toBe(0); expect(calls.recovered).toBe(0);
});
