import { expect, test, type Page } from "@playwright/test";

const customerUrl = "http://127.0.0.1:4000";
const assetId = "14000000-0000-0000-0000-000000000001";
const hero = { blockId: "15000000-0000-0000-0000-000000000001", type: "HERO", imageAssetId: assetId, imageSrc: "/images/sokcho-coast-hero.png", imageAlt: "English coast", eyebrow: "OUR BRAND", title: "Our story", description: "A slower stay", cta: { label: "English home", href: "/" } };

async function routeKoreanHeaderPage(page: Page) {
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    if (url.pathname === "/api/hotels") return route.fulfill({ json: [{ id: "seoraksan", name: "설악 마운틴 호텔", region: "설악산", timezone: "Asia/Seoul" }] });
    if (url.pathname === "/api/website/navigation") return route.fulfill({ json: [{
      id: "root", hotelId: null, label: "숙소", path: "/stays", children: [
        { id: "sokcho", hotelId: "sokcho", label: "속초 오션 호텔", path: "/stays/sokcho", children: [] },
        { id: "seoraksan", hotelId: "seoraksan", label: "설악 마운틴 호텔", path: "/stays/seoraksan", children: [] },
        { id: "jeju", hotelId: "jeju", label: "제주 아일랜드 호텔", path: "/stays/jeju", children: [] },
        { id: "story", hotelId: null, label: "브랜드 이야기", path: "/brand/story", children: [] },
        { id: "haneul", hotelId: null, label: "하늘의 이야기", path: "/brand/haneul-story", children: [] },
      ],
    }] });
    if (url.pathname === "/api/website/pages/resolve") return route.fulfill({ json: {
      id: "seoraksan", type: "HOTEL_LANDING", contentKind: "DESTINATION", hotelId: "seoraksan", path: "/stays/seoraksan",
      connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, content: { title: "숲의 결을 따라 깊어지는 쉼" },
    } });
    if (url.pathname === "/api/availability") return route.fulfill({ json: { offers: [] } });
    return route.fulfill({ status: 500, json: { code: "UNEXPECTED_API", message: url.pathname } });
  });
}

for (const width of [1280, 390]) test(`serves only published English CMS content (${width}px)`, async ({ page }) => {
  await page.setViewportSize({ width, height: 844 });
  const requestedLocales: string[] = [];
  let missing = false;
  await page.route("**/api/**", async route => {
    const url = new URL(route.request().url());
    if (url.pathname.startsWith("/api/website/")) requestedLocales.push(url.searchParams.get("locale") ?? "missing");
    if (url.pathname === "/api/hotels") return route.fulfill({ json: [] });
    if (url.pathname === "/api/website/navigation") return route.fulfill({ json: [{ id: "story", hotelId: null, label: "Our story", path: "/en/brand/story", children: [] }] });
    if (url.pathname === "/api/website/pages/resolve") {
      if (missing) return route.fulfill({ status: 404, json: { code: "WEBSITE_PAGE_NOT_FOUND", message: "Not published" } });
      const path = url.searchParams.get("path");
      return route.fulfill({ json: { id: "story", type: path === "/en" ? "HOME_PAGE" : "CONTENT_PAGE", contentKind: path === "/en" ? "HOME" : "BRAND", hotelId: null, path,
        connections: { roomTypeIds: [], targetHotelIds: [], relatedPages: [] }, content: {
          seo: { title: "Our story | STAY HANEUL", description: "English story description" }, blocks: [hero,
            { blockId: "15000000-0000-0000-0000-000000000002", type: "IMAGE_GALLERY", title: "Gallery", items: [
              { imageAssetId: assetId, imageSrc: hero.imageSrc, imageAlt: "Coast", caption: "Coast caption" },
              { imageAssetId: assetId, imageSrc: hero.imageSrc, imageAlt: "Hotel", caption: "Hotel caption" },
            ] },
          ],
        } } });
    }
    if (url.pathname === "/api/website/collections") return route.fulfill({ json: [] });
    return route.fulfill({ status: 500, json: { code: "UNEXPECTED_API", message: url.pathname } });
  });
  await page.goto(`${customerUrl}/en/brand/story`);
  await expect(page.getByRole("heading", { name: "Our story", exact: true })).toBeVisible();
  const renderedFontFamilies = await Promise.all([
    page.locator("html").evaluate(element => getComputedStyle(element).fontFamily),
    page.locator(".topbar .brand").evaluate(element => getComputedStyle(element).fontFamily),
    page.getByRole("heading", { name: "Our story", exact: true }).evaluate(element => getComputedStyle(element).fontFamily),
  ]);
  expect(renderedFontFamilies.every(fontFamily => fontFamily.includes("Pretendard Variable"))).toBe(true);
  await expect(page.locator("html")).toHaveAttribute("lang", "en");
  await expect(page).toHaveTitle("Our story | STAY HANEUL");
  await expect(page.getByRole("link", { name: "English home", exact: true })).toHaveAttribute("href", "/en");
  await page.getByRole("button", { name: "Next image", exact: true }).click();
  await expect(page.getByText("Hotel caption", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  if (process.env.CMS_LOCALE_SCREENSHOTS) await page.screenshot({ path: `../.tmp/cms-locale-customer-${width}.png`, fullPage: true });
  await page.goto(`${customerUrl}/en`);
  await expect(page.getByRole("heading", { name: "Our story", exact: true })).toBeVisible();
  await expect(page).toHaveTitle("Our story | STAY HANEUL");
  missing = true;
  await page.goto(`${customerUrl}/en`);
  await expect(page.getByRole("heading", { name: "English content is not available.", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Our story", exact: true })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "View the Korean website" })).toHaveAttribute("href", "/");
  await page.goto(`${customerUrl}/en/stays/sokcho/rooms`);
  await expect(page.getByRole("heading", { name: "No English rooms have been published yet." })).toBeVisible();
  expect(requestedLocales.every(locale => locale === "en")).toBe(true);
});

test("keeps the full Korean desktop navigation on one line at 1186px", async ({ page }) => {
  await page.setViewportSize({ width: 1186, height: 844 });
  await routeKoreanHeaderPage(page);

  await page.goto(`${customerUrl}/stays/seoraksan`);
  const navigation = page.getByRole("navigation", { name: "주요 메뉴" });
  await expect(navigation.getByRole("link")).toHaveCount(9);
  const textLineCounts = await navigation.getByRole("link").evaluateAll(links => links.map(link => {
    const range = document.createRange();
    range.selectNodeContents(link);
    return range.getClientRects().length;
  }));
  expect(textLineCounts).toEqual(Array(9).fill(1));
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("keeps Korean hero title words intact at 390px", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await routeKoreanHeaderPage(page);

  await page.goto(`${customerUrl}/stays/seoraksan`);
  const heading = page.locator("#hero-title");
  await expect(heading).toBeVisible();
  await expect(heading).toHaveCSS("word-break", "keep-all");
});

test("aligns the desktop booking lookup and active locale indicators", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 844 });
  await routeKoreanHeaderPage(page);
  await page.goto(`${customerUrl}/stays/seoraksan`);

  const indicators = await page.evaluate(() => {
    const bookingLookup = document.querySelector<HTMLElement>(".manage-link");
    const activeLocale = document.querySelector<HTMLElement>('.lang a[aria-current="page"]');
    if (!bookingLookup || !activeLocale) throw new Error("Desktop header indicators were not rendered");
    const bookingRect = bookingLookup.getBoundingClientRect();
    const localeRect = activeLocale.getBoundingClientRect();
    return {
      bookingBottom: bookingRect.bottom,
      localeBottom: localeRect.bottom,
      bookingHeight: bookingRect.height,
      localeHeight: localeRect.height,
      localeBorderStyle: getComputedStyle(activeLocale).borderBottomStyle,
      localeTextDecoration: getComputedStyle(activeLocale).textDecorationLine,
    };
  });

  expect(Math.abs(indicators.bookingBottom - indicators.localeBottom)).toBeLessThanOrEqual(1);
  expect(Math.abs(indicators.bookingHeight - indicators.localeHeight)).toBeLessThanOrEqual(1);
  expect(indicators.localeBorderStyle).toBe("solid");
  expect(indicators.localeTextDecoration).toBe("none");
});

test("switches the crowded Korean navigation to the menu button at 1024px", async ({ page }) => {
  await page.setViewportSize({ width: 1024, height: 844 });
  await routeKoreanHeaderPage(page);
  await page.goto(`${customerUrl}/stays/seoraksan`);

  const navigation = page.getByRole("navigation", { name: "주요 메뉴" });
  const menuButton = page.getByRole("button", { name: "메뉴" });
  await expect(navigation).toBeHidden();
  await expect(menuButton).toBeVisible();
  await menuButton.click();
  await expect(navigation).toBeVisible();
  await expect(navigation.getByRole("link")).toHaveCount(9);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
