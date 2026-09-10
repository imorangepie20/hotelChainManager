import { expect, test } from "@playwright/test";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
});

test("switches from head office to Sokcho operational readiness", async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff-session", "test-session-token"));
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "test", email: "hq@example.com", displayName: "본사 관리자", role: "HQ_ADMIN", hotelId: null })));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("heading", { name: "호텔 운영 현황" })).toBeVisible();
  await expect(page.getByText("본사 운영 준비 현황")).toBeVisible();

  await page.getByRole("button", { name: "속초 지점" }).click();

  await expect(page.getByText("속초 지점 오늘의 업무")).toBeVisible();
  await expect(page.getByText("도착 확인 준비")).toBeVisible();
  await expect(page.getByRole("button", { name: "속초 지점" })).toHaveAttribute("aria-pressed", "true");
});

test("limits a branch employee to their assigned hotel", async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff-session", "test-session-token"));
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "test", email: "sokcho@example.com", displayName: "속초 직원", role: "BRANCH_STAFF", hotelId: "11000000-0000-0000-0000-000000000001" })));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("button", { name: "속초 지점" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("button", { name: "본사" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "제주 지점" })).toHaveCount(0);
});
