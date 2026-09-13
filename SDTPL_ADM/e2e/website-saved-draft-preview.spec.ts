import { expect, test } from "@playwright/test";

const TOKEN = "P".repeat(43);
const pageId = "preview-story";
const connections = { roomTypeIds: [], targetHotelIds: [], relatedPages: [] };
const content = { seo: { title: "미리보기 이야기", description: "저장 초안 검토" }, blocks: [{ type: "HERO", imageAssetId: "14000000-0000-0000-0000-000000000001", imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "해안", title: "저장 제목", description: "저장 설명", eyebrow: "STAY" }] };
const draftDocument = { id: pageId, pageType: "CONTENT_PAGE", contentKind: "BRAND", hotelId: null, draftContent: content, draftConnections: connections, draftVersion: 1, draftMetadata: { slug: "preview-story", path: "/brand/preview-story", menuLabel: "미리보기 이야기", menuVisible: true, menuOrder: 0 }, publishedContent: {}, publishedConnections: connections, publishedVersion: 1, publishedMetadata: null, lifecycleStatus: "ACTIVE", lifecycleVersion: 1 };

test.beforeEach(async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("hotel-chain-staff-session", "test-session");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "admin", email: "admin@example.test", displayName: "관리자", role: "HQ_ADMIN", hotelId: null }));
    window.open = () => null;
  });
  await page.route("**/api/staff/**", async route => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith("/me")) return route.fulfill({ json: {} });
    if (path.endsWith("/versions")) return route.fulfill({ json: [] });
    if (path.endsWith("/content-reference")) return route.fulfill({ json: { hotels: [], pages: [] } });
    if (path.endsWith("/pages")) return route.fulfill({ json: [{ id: "stays", pageType: "SECTION", label: "숙소", draftPath: "/stays", children: [{ id: "sokcho", pageType: "HOTEL_LANDING", hotelId: "11000000-0000-0000-0000-000000000001", label: "속초", draftPath: "/stays/sokcho", children: [] }] }, { id: "brand", pageType: "SECTION", hotelId: null, label: "브랜드", draftPath: "/brand", publishedPath: "/brand", lifecycleStatus: "ACTIVE", children: [{ id: pageId, pageType: "CONTENT_PAGE", hotelId: null, label: "미리보기 이야기", draftPath: "/brand/preview-story", publishedPath: "", lifecycleStatus: "ACTIVE", lifecycleVersion: 1, status: "DRAFT", children: [] }] }] });
    if (path.endsWith(`/pages/${pageId}`)) return route.fulfill({ json: { ...draftDocument, draftVersion: route.request().method() === "PUT" ? 2 : 1 } });
    if (path.endsWith("/translations/en")) return route.fulfill({ json: { ...draftDocument, id: "sokcho", pageType: "HOTEL_LANDING", contentKind: "DESTINATION", hotelId: "11000000-0000-0000-0000-000000000001", draftContent: {}, draftMetadata: { ...draftDocument.draftMetadata, path: "/en/stays/sokcho" } } });
    if (path.endsWith("/review")) return route.fulfill({ json: { status: "DRAFT", reviewedDraftVersion: null, events: [] } });
    return route.fulfill({ json: { draftContent: {}, draftVersion: 1, publishedContent: {}, publishedVersion: 1 } });
  });
  await page.goto("/dashboard/website");
  // Initial selection is loaded by explicitly selecting its leaf after the tree appears.
  await page.getByRole("button", { name: "미리보기 이야기", exact: true }).click();
  if (!(await page.getByLabel("히어로 제목", { exact: true }).count())) {
    await page.getByRole("button", { name: "한국어", exact: true }).click();
  }
});

for (const width of [1280, 390]) test(`requires a saved draft and supports blocked popup copy and revoke (${width}px)`, async ({ page }) => {
  await page.setViewportSize({ width, height: 844 });
  const issued: unknown[] = [];
  let revoked = 0;
  await page.route(`**/pages/${pageId}/preview-grants`, route => {
    issued.push(route.request().postDataJSON());
    return route.fulfill({ json: { grantId: "grant-one", previewToken: TOKEN, previewPath: "/brand/preview-story", expiresAt: new Date(Date.now() + 600_000).toISOString() } });
  });
  await page.route("**/preview-grants/grant-one", route => { revoked++; return route.fulfill({ status: 204 }); });
  const action = page.getByRole("button", { name: "실제 화면 미리보기", exact: true });
  await expect(action).toBeEnabled();
  await page.getByLabel("히어로 제목", { exact: true }).fill("미저장 제목");
  await expect(action).toBeDisabled();
  await page.getByRole("button", { name: "초안 저장", exact: true }).click();
  await expect(action).toBeEnabled();
  await action.click();
  const dialog = page.getByRole("dialog", { name: "저장 초안 미리보기" });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel("미리보기 링크")).toHaveValue(`http://127.0.0.1:4000/brand/preview-story#preview=${TOKEN}`);
  expect(issued).toEqual([{ locale: "ko", expectedDraftVersion: 2 }]);
  await expect(dialog.getByText(/10분|만료/).first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await dialog.getByRole("button", { name: "링크 폐기" }).click();
  await expect(dialog.getByLabel("미리보기 링크")).toHaveCount(0);
  expect(revoked).toBe(1);
  await page.keyboard.press("Escape");
  await expect(action).toBeFocused();
});

test("closes the blank popup when issuing fails", async ({ page }) => {
  await page.evaluate(() => {
    const state = { closed: false, opener: "original" };
    Object.assign(window, { previewPopupState: state });
    window.open = () => ({ ...state, close: () => { state.closed = true; }, set opener(value: string) { state.opener = value; } }) as unknown as Window;
  });
  await page.route(`**/pages/${pageId}/preview-grants`, route => route.fulfill({ status: 409, json: { code: "WEBSITE_PAGE_VERSION_CONFLICT", message: "최신 초안을 다시 불러와 주세요." } }));
  await page.getByRole("button", { name: "실제 화면 미리보기", exact: true }).click();
  await expect(page.getByRole("alert").filter({ hasText: "최신 초안" })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { previewPopupState: { closed: boolean } }).previewPopupState.closed)).toBe(true);
});

test("opens an isolated popup and copies the actual saved URL", async ({ page }) => {
  await page.evaluate(() => {
    const state = { isolated: false, navigated: false, copied: false };
    Object.assign(window, { previewState: state });
    window.open = () => ({ closed: false, close() {}, set opener(value: unknown) { state.isolated = value === null; }, location: { replace(value: string) { state.navigated = value.startsWith("http://127.0.0.1:4000/brand/preview-story#preview="); } } }) as unknown as Window;
    Object.defineProperty(navigator, "clipboard", { configurable: true, value: { async writeText(value: string) { state.copied = value.startsWith("http://127.0.0.1:4000/brand/preview-story#preview="); } } });
  });
  await page.route(`**/pages/${pageId}/preview-grants`, route => route.fulfill({ json: { grantId: "grant-one", previewToken: TOKEN, previewPath: "/brand/preview-story", expiresAt: new Date(Date.now() + 600_000).toISOString() } }));
  await page.getByRole("button", { name: "실제 화면 미리보기", exact: true }).click();
  await page.getByRole("dialog").getByRole("button", { name: "링크 복사" }).click();
  await expect(page.getByRole("status").filter({ hasText: "링크를 복사했습니다." })).toBeVisible();
  expect(await page.evaluate(() => (window as unknown as { previewState: unknown }).previewState)).toEqual({ isolated: true, navigated: true, copied: true });
});

test("publisher can preview a saved English draft without editing it", async ({ page }) => {
  await page.addInitScript(() => localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "publisher", email: "publisher@example.test", displayName: "게시자", role: "HQ_PUBLISHER", hotelId: null })));
  await page.route(`**/pages/${pageId}/translations/en`, route => route.fulfill({ json: { ...draftDocument, draftMetadata: { ...draftDocument.draftMetadata, path: "/en/brand/preview-story" } } }));
  await page.route(`**/pages/${pageId}/translations/en/review`, route => route.fulfill({ json: { status: "DRAFT", reviewedDraftVersion: null, events: [] } }));
  const bodies: unknown[] = [];
  await page.route(`**/pages/${pageId}/preview-grants`, route => { bodies.push(route.request().postDataJSON()); return route.fulfill({ json: { grantId: "grant-en", previewToken: TOKEN, previewPath: "/en/brand/preview-story", expiresAt: new Date(Date.now() + 600_000).toISOString() } }); });
  await page.reload();
  await page.getByRole("button", { name: "미리보기 이야기", exact: true }).click();
  await page.getByRole("button", { name: "영어", exact: true }).click();
  await expect(page.getByLabel("히어로 제목", { exact: true })).toBeDisabled();
  const action = page.getByRole("button", { name: "실제 화면 미리보기", exact: true });
  await expect(action).toBeEnabled(); await action.click();
  await expect(page.getByRole("dialog").getByLabel("미리보기 링크")).toHaveValue(`http://127.0.0.1:4000/en/brand/preview-story#preview=${TOKEN}`);
  expect(bodies).toEqual([{ locale: "en", expectedDraftVersion: 1 }]);
});

test("does not offer a URL preview for a missing English draft", async ({ page }) => {
  await page.route(`**/pages/${pageId}/translations/en`, route => route.fulfill({ json: { ...draftDocument, draftVersion: 0, draftContent: {}, publishedContent: {}, publishedVersion: 0 } }));
  await page.route(`**/pages/${pageId}/translations/en/review`, route => route.fulfill({ json: { status: "DRAFT", reviewedDraftVersion: null, events: [] } }));
  await page.getByRole("button", { name: "영어", exact: true }).click();
  await expect(page.getByText("영어 번역 초안이 없습니다.", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "실제 화면 미리보기", exact: true })).toHaveCount(0);
});
