import { expect, test } from "@playwright/test";

const HOTELS = [
  {
    hotelId: "11000000-0000-0000-0000-000000000001",
    hotelName: "속초 지점",
    region: "sokcho",
    reservations: 6,
    cancelled: 1,
    noShow: 1,
    expired: 0,
    revenueKrw: 400000,
    changeRequestsPending: 1,
    changeRequestsCompleted: 1,
    occupancyRate: 0.2,
  },
  {
    hotelId: "11000000-0000-0000-0000-000000000002",
    hotelName: "제주 지점",
    region: "jeju",
    reservations: 2,
    cancelled: 0,
    noShow: 0,
    expired: 0,
    revenueKrw: 400000,
    changeRequestsPending: 0,
    changeRequestsCompleted: 0,
    occupancyRate: 0,
  },
];

function reportBody() {
  return JSON.stringify({
    from: "2026-09-14",
    to: "2026-09-20",
    days: 7,
    hotels: HOTELS,
    totals: {
      reservations: 8,
      cancelled: 1,
      noShow: 1,
      expired: 0,
      revenueKrw: 800000,
      changeRequestsPending: 1,
      changeRequestsCompleted: 1,
    },
  });
}

function seedStaffScript(role: "HQ_ADMIN" | "BRANCH_STAFF") {
  const staff =
    role === "HQ_ADMIN"
      ? { id: "test", email: "hq@example.com", displayName: "본사 관리자", role, hotelId: null }
      : {
          id: "test",
          email: "sokcho@example.com",
          displayName: "속초 직원",
          role,
          hotelId: "11000000-0000-0000-0000-000000000001",
        };
  return `
    window.localStorage.setItem("hotel-chain-staff", ${JSON.stringify(JSON.stringify(staff))});
    window.localStorage.setItem("hotel-chain-staff-session", "test-session-token");
  `;
}

test.beforeEach(async ({ page }) => {
  await page.route("**/api/staff/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );
});

test("exposes the report menu to headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "운영 통계" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "운영 통계" })).toHaveCount(0);
});

test("reports per-hotel metrics and totals", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: reportBody() }),
  );

  await page.goto("/dashboard/reports");

  await expect(page.getByTestId("report-range")).toHaveText("9월 14일 ~ 9월 20일 · 7일");
  await expect(page.getByTestId("report-total-reservations")).toHaveText("8");
  await expect(page.getByTestId("report-total-revenue")).toHaveText("800,000원");
  await expect(page.getByTestId("report-total-pending")).toHaveText("1");
  await expect(page.getByTestId("report-total-completed")).toHaveText("1");

  await expect(page.getByRole("cell", { name: "속초 지점" })).toBeVisible();
  await expect(page.getByTestId("report-reservations").first()).toHaveText("6");
  await expect(page.getByText("20.0%").first()).toBeVisible();
  await expect(page.getByRole("cell", { name: "제주 지점" })).toBeVisible();
});

test("switches the range with the 30 day preset", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const ranges: string[] = [];
  await page.route("**/api/staff/reports/operations*", async (route) => {
    const url = new URL(route.request().url());
    ranges.push(`${url.searchParams.get("from")}~${url.searchParams.get("to")}`);
    await route.fulfill({ status: 200, contentType: "application/json", body: reportBody() });
  });

  await page.goto("/dashboard/reports");
  await expect(page.getByTestId("report-range")).toBeVisible();

  await page.getByTestId("report-preset-30").click();

  await expect.poll(() => ranges.length).toBeGreaterThan(1);
  const last = ranges[ranges.length - 1];
  const [from, to] = last.split("~");
  expect(from).not.toBe("");
  expect(to).not.toBe("");
  const days = (Date.parse(to) - Date.parse(from)) / 86400000;
  expect(days).toBe(29);
});

test("tells a branch employee the view is headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/reports");

  await expect(page.getByText("운영 통계는 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("shows a recovery notice when the report is unreachable", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations*", (route) => route.fulfill({ status: 500 }));

  await page.goto("/dashboard/reports");

  await expect(page.getByText("운영 통계를 불러오지 못했습니다.")).toBeVisible();
});

test("reports an empty range without metrics", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        from: "2026-09-14",
        to: "2026-09-20",
        days: 7,
        hotels: [],
        totals: {
          reservations: 0,
          cancelled: 0,
          noShow: 0,
          expired: 0,
          revenueKrw: 0,
          changeRequestsPending: 0,
          changeRequestsCompleted: 0,
        },
      }),
    }),
  );

  await page.goto("/dashboard/reports");

  await expect(page.getByTestId("report-empty")).toBeVisible();
});
