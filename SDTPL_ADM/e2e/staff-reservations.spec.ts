import { expect, test } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";

test("lets a branch employee search reservations and open the selected reservation details", async ({ page }) => {
  await page.addInitScript((hotelId) => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({
      id: "staff",
      email: "sokcho@hotel-chain.local",
      displayName: "속초 직원",
      role: "BRANCH_STAFF",
      hotelId,
    }));
  }, SOKCHO);
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/staff/hotels/*/reservations?*", (route) => {
    const requestUrl = new URL(route.request().url());
    const query = requestUrl.searchParams.get("query") ?? "";
    const reservations = query && query !== "김하늘" ? [] : [{
      reservationId: "42000000-0000-0000-0000-000000000001",
      guestName: "김하늘",
      guestEmail: "guest@example.com",
      roomTypeName: "디럭스 오션",
      ratePlanName: "조식 포함",
      checkIn: "2026-09-13",
      checkOut: "2026-09-15",
      adults: 2,
      children: 1,
      rooms: 1,
      status: "CONFIRMED",
      totalKrw: 420000,
      currency: "KRW",
      assignedRoomNumbers: ["701"],
    }];
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({ hotelId: SOKCHO, date: requestUrl.searchParams.get("date"), truncated: false, reservations }),
    });
  });

  await page.goto("/dashboard/reservations");

  await expect(page.getByRole("heading", { name: "예약 관리" })).toBeVisible();
  await expect(page.getByRole("group", { name: "예약 날짜 달력" })).toBeVisible();
  await expect(page.getByText("김하늘", { exact: true })).toBeVisible();

  const filtered = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return url.pathname.includes("/reservations") && url.searchParams.get("status") === "CONFIRMED";
  });
  await page.getByLabel("예약 상태").selectOption("CONFIRMED");
  await filtered;

  const searched = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return url.pathname.includes("/reservations") && url.searchParams.get("query") === "김하늘" && url.searchParams.get("status") === "CONFIRMED";
  });
  await page.getByLabel("예약 검색").fill("김하늘");
  await page.getByRole("button", { name: "검색", exact: true }).click();
  await searched;

  const detailButton = page.getByRole("button", { name: "김하늘 예약 상세" });
  await detailButton.press("Enter");
  await expect(page.getByRole("dialog")).toContainText("guest@example.com");
  await expect(page.getByRole("dialog")).toContainText("420,000원");
  await expect(page.getByRole("dialog")).toContainText("701호");
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toBeHidden();
  await expect(detailButton).toBeFocused();

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.getByLabel("예약 검색")).toBeVisible();
  await expect(detailButton).toBeVisible();
});
