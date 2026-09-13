import { expect, test, type Page } from "@playwright/test";

async function openDashboardAs(page: Page, role: "HQ_ADMIN" | "BRANCH_STAFF") {
  await page.addInitScript((staffRole) => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({
      id: "staff-test",
      email: staffRole === "HQ_ADMIN" ? "hq@example.test" : "sokcho@example.test",
      displayName: staffRole === "HQ_ADMIN" ? "본사 관리자" : "속초 직원",
      role: staffRole,
      hotelId: staffRole === "HQ_ADMIN" ? null : "11000000-0000-0000-0000-000000000001",
    }));
  }, role);
  await page.route("**/api/staff/me", (route) => route.fulfill({
    status: 200,
    contentType: "application/json",
    body: "{}",
  }));
  await page.goto("/dashboard/default");
}

test("본사 관리자는 호텔 운영과 CMS 메뉴만 본다", async ({ page }) => {
  await openDashboardAs(page, "HQ_ADMIN");

  await expect(page.getByRole("link", { name: "운영 대시보드" })).toBeVisible();
  await expect(page.getByRole("link", { name: "오늘의 운영" })).toBeVisible();
  await expect(page.getByRole("link", { name: "웹사이트 CMS" })).toBeVisible();
  await expect(page.getByRole("link", { name: "E-commerce" })).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Crypto" })).toHaveCount(0);
});

test("지점 직원에게 본사 CMS 메뉴와 검색 항목을 노출하지 않는다", async ({ page }) => {
  await openDashboardAs(page, "BRANCH_STAFF");

  await expect(page.getByRole("link", { name: "운영 대시보드" })).toBeVisible();
  await expect(page.getByRole("link", { name: "오늘의 운영" })).toBeVisible();
  await expect(page.getByRole("link", { name: "웹사이트 CMS" })).toHaveCount(0);

  await page.getByRole("button", { name: /검색/ }).click();
  await expect(page.getByRole("dialog")).toContainText("운영 대시보드");
  await expect(page.getByRole("dialog")).not.toContainText("웹사이트 CMS");
});
