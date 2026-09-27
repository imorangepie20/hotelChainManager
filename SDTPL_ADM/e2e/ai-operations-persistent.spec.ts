import { expect, test } from "@playwright/test";

const METRICS_BODY = JSON.stringify({
  timezone: "Asia/Seoul",
  period: "24H",
  bucket: "HOUR",
  fromInclusive: "2026-09-26T01:00:00Z",
  toExclusive: "2026-09-27T01:00:00Z",
  retentionDays: 90,
  models: ["gemini-3.6-flash"],
  totals: { calls: 5, avgElapsedMs: 2600, policyViolations: 2 },
  outcomes: {
    success: { count: 3, totalElapsedMs: 9000, avgElapsedMs: 3000 },
    schema_rejected: { count: 1, totalElapsedMs: 1500, avgElapsedMs: 1500 },
    unparsable: { count: 0, totalElapsedMs: 0, avgElapsedMs: 0 },
    empty_response: { count: 0, totalElapsedMs: 0, avgElapsedMs: 0 },
    api_error: { count: 1, totalElapsedMs: 2500, avgElapsedMs: 2500 },
    no_key: { count: 0, totalElapsedMs: 0, avgElapsedMs: 0 },
  },
  series: [
    {
      startAt: "2026-09-26T23:00:00Z",
      endAt: "2026-09-27T00:00:00Z",
      calls: 2,
      avgElapsedMs: 2000,
      policyViolations: 1,
    },
    {
      startAt: "2026-09-27T00:00:00Z",
      endAt: "2026-09-27T01:00:00Z",
      calls: 3,
      avgElapsedMs: 3000,
      policyViolations: 1,
    },
  ],
});

function seedStaffScript(role: "HQ_ADMIN" | "BRANCH_STAFF") {
  const staff =
    role === "HQ_ADMIN"
      ? { id: "test", email: "hq@example.com", displayName: "본사 관리자", role, hotelId: null }
      : {
          id: "test",
          email: "branch@example.com",
          displayName: "지점 직원",
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

test("loads persistent time-series metrics through the authenticated Spring API", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let sessionHeader = "";
  await page.route("**/api/staff/ai-operations/metrics?period=24H", (route) => {
    sessionHeader = route.request().headers()["x-staff-session"] ?? "";
    return route.fulfill({ status: 200, contentType: "application/json", body: METRICS_BODY });
  });

  await page.goto("/dashboard/ai-operations");

  await expect(page.getByText("5").first()).toBeVisible();
  await expect(page.getByText("2,600ms")).toBeVisible();
  await expect(page.getByText("정책 위반", { exact: true }).first()).toBeVisible();
  await expect(
    page.locator('[data-slot="card"]').filter({ hasText: "정책 위반" }).first().getByText("2", { exact: true }),
  ).toBeVisible();
  await expect(page.getByRole("figure", { name: "LLM 호출 및 정책 위반 시계열" })).toBeVisible();
  const seriesTable = page.getByRole("table", { name: "시간 구간별 LLM 운영 지표" });
  await expect(seriesTable).toBeVisible();
  await expect(seriesTable.getByRole("row").nth(1)).toContainText("2");
  await expect(seriesTable.getByRole("row").nth(1)).toContainText("2,000ms");
  await expect(seriesTable.getByRole("row").nth(2)).toContainText("3,000ms");
  expect(sessionHeader).toBe("test-session-token");
});

test("switches periods with the keyboard and fits a 390px viewport", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const periods: string[] = [];
  await page.route("**/api/staff/ai-operations/metrics?period=*", (route) => {
    periods.push(new URL(route.request().url()).searchParams.get("period") ?? "");
    return route.fulfill({ status: 200, contentType: "application/json", body: METRICS_BODY });
  });

  await page.goto("/dashboard/ai-operations");
  const sevenDays = page.getByRole("button", { name: "7일" });
  await sevenDays.focus();
  await page.keyboard.press("Enter");
  await expect.poll(() => periods).toContain("7D");
  const thirtyDays = page.getByRole("button", { name: "30일" });
  await thirtyDays.focus();
  await page.keyboard.press("Space");
  await expect.poll(() => periods).toContain("30D");

  const hasHorizontalOverflow = await page.evaluate(
    () => document.documentElement.scrollWidth > document.documentElement.clientWidth,
  );
  expect(hasHorizontalOverflow).toBe(false);
});

test("does not request persistent metrics for branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  let requests = 0;
  await page.route("**/api/staff/ai-operations/metrics*", (route) => {
    requests += 1;
    return route.abort();
  });

  await page.goto("/dashboard/ai-operations");

  await expect(page.getByText("본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  expect(requests).toBe(0);
});
