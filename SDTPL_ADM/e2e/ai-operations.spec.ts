import { expect, test } from "@playwright/test";

const METRICS_BODY = JSON.stringify({
  model: "gemini-3.6-flash",
  outcomes: {
    success: { count: 3, totalElapsedMs: 9000.0, avgElapsedMs: 3000.0 },
    schema_rejected: { count: 1, totalElapsedMs: 1500.0, avgElapsedMs: 1500.0 },
    unparsable: { count: 0, totalElapsedMs: 0.0, avgElapsedMs: 0.0 },
    empty_response: { count: 0, totalElapsedMs: 0.0, avgElapsedMs: 0.0 },
    api_error: { count: 1, totalElapsedMs: 2500.0, avgElapsedMs: 2500.0 },
    no_key: { count: 0, totalElapsedMs: 0.0, avgElapsedMs: 0.0 },
  },
});

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

test("exposes the AI operations menu to headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "AI 도우미 운영" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "AI 도우미 운영" })).toHaveCount(0);
});

test("reports LLM outcome counts and latency to headquarters", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/concierge/metrics/llm", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: METRICS_BODY }),
  );

  await page.goto("/dashboard/ai-operations");
  await page.getByRole("button", { name: "새로고침" }).click();

  await expect(page.getByText("gemini-3.6-flash")).toBeVisible();
  await expect(page.getByText("5").first()).toBeVisible();
  await expect(page.getByRole("cell", { name: "스키마 위반" })).toBeVisible();
  await expect(page.getByText("3,000ms")).toBeVisible();
});

test("tells a branch employee the AI operations view is headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/ai-operations");

  await expect(page.getByText("AI 도우미 운영은 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("shows a recovery notice when the concierge is unreachable", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/concierge/metrics/llm", (route) => route.fulfill({ status: 502 }));

  await page.goto("/dashboard/ai-operations");
  await page.getByRole("button", { name: "새로고침" }).click();

  await expect(page.getByText("AI 도우미 측정을 불러오지 못했습니다.")).toBeVisible();
});

test("explains that the concierge is not part of this deployment", async ({ page }) => {
  // 도우미가 없는 배포에서는 /concierge rewrite 자체가 없어서 404가 돌아온다.
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/concierge/metrics/llm", (route) => route.fulfill({ status: 404 }));

  await page.goto("/dashboard/ai-operations");
  await page.getByRole("button", { name: "새로고침" }).click();

  await expect(page.getByText("AI 도우미가 실행 중이 아닙니다.")).toBeVisible();
});
