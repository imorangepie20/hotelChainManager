import { expect, test } from "@playwright/test";

const hotels = {
  sokcho: "11000000-0000-0000-0000-000000000001",
  seoraksan: "11000000-0000-0000-0000-000000000002",
  jeju: "11000000-0000-0000-0000-000000000003",
};

const dailyOperations = {
  [hotels.sokcho]: {
    arrivals: [
      { reservationId: "sokcho-a1", guestName: "김도착", roomTypeName: "스탠다드", status: "CONFIRMED", assignedRoomNumbers: [] },
      { reservationId: "sokcho-a2", guestName: "이도착", roomTypeName: "디럭스", status: "CONFIRMED", assignedRoomNumbers: ["901"] },
    ],
    departures: [
      { reservationId: "sokcho-d1", guestName: "박출발", roomTypeName: "스탠다드", status: "CHECKED_IN", assignedRoomNumbers: ["902"] },
    ],
    roomsNeedingCleaning: [
      { physicalRoomId: "sokcho-r1", roomNumber: "903", roomTypeName: "스탠다드", housekeepingStatus: "NEEDS_CLEANING" },
    ],
  },
  [hotels.seoraksan]: { arrivals: [], departures: [], roomsNeedingCleaning: [] },
  [hotels.jeju]: {
    arrivals: [
      { reservationId: "jeju-a1", guestName: "최도착", roomTypeName: "오션", status: "CONFIRMED", assignedRoomNumbers: [] },
    ],
    departures: [],
    roomsNeedingCleaning: [],
  },
};

test.beforeEach(async ({ page }) => {
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/staff/hotels/*/operations?date=*", (route) => {
    const hotelId = new URL(route.request().url()).pathname.split("/")[4];
    const data = dailyOperations[hotelId as keyof typeof dailyOperations];
    return route.fulfill({
      status: data ? 200 : 404,
      contentType: "application/json",
      body: JSON.stringify(data ? { hotelId, date: "2026-09-27", ...data } : { message: "지점을 찾지 못했습니다." }),
    });
  });
});

test("loads live daily metrics and switches from head office to Sokcho", async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff-session", "test-session-token"));
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "test", email: "hq@example.com", displayName: "본사 관리자", role: "HQ_ADMIN", hotelId: null })));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("heading", { name: "호텔 운영 현황" })).toBeVisible();
  await expect(page.getByText("본사 오늘의 운영 현황")).toBeVisible();
  await expect(page.getByRole("group", { name: "속초 지점 오늘 업무" })).toContainText("도착 2건");
  await expect(page.getByRole("group", { name: "속초 지점 오늘 업무" })).toContainText("출발 1건");
  await expect(page.getByRole("group", { name: "속초 지점 오늘 업무" })).toContainText("배정 필요 1건");
  await expect(page.getByRole("group", { name: "속초 지점 오늘 업무" })).toContainText("청소 필요 1건");

  await page.getByRole("button", { name: "속초 지점" }).click();

  await expect(page.getByText("속초 지점 오늘의 업무")).toBeVisible();
  await expect(page.getByRole("group", { name: "속초 지점 도착 예정 2건" })).toBeVisible();
  await expect(page.getByRole("group", { name: "속초 지점 출발 예정 1건" })).toBeVisible();
  await expect(page.getByRole("group", { name: "속초 지점 객실 배정 필요 1건" })).toBeVisible();
  await expect(page.getByRole("group", { name: "속초 지점 청소 필요 1건" })).toBeVisible();
  await expect(page.getByRole("link", { name: "오늘의 운영 열기" })).toHaveAttribute("href", "/dashboard/operations");
  await expect(page.getByText("실제 운영 데이터 연결 전")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "속초 지점" })).toHaveAttribute("aria-pressed", "true");
});

test("limits a branch employee to their assigned hotel", async ({ page }) => {
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff-session", "test-session-token"));
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "test", email: "sokcho@example.com", displayName: "속초 직원", role: "BRANCH_STAFF", hotelId: "11000000-0000-0000-0000-000000000001" })));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("button", { name: "속초 지점" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("group", { name: "속초 지점 도착 예정 2건" })).toBeVisible();
  await expect(page.getByRole("button", { name: "본사" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "제주 지점" })).toHaveCount(0);
});

test("shows a retryable error when daily operations cannot be loaded", async ({ page }) => {
  await page.route("**/api/staff/hotels/*/operations?date=*", (route) => route.fulfill({
    status: 503,
    contentType: "application/json",
    body: JSON.stringify({ message: "점검 중" }),
  }));
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff-session", "test-session-token"));
  await page.addInitScript(() => window.localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "test", email: "hq@example.com", displayName: "본사 관리자", role: "HQ_ADMIN", hotelId: null })));

  await page.goto("/dashboard/default");

  await expect(page.getByRole("alert")).toContainText("오늘의 운영 데이터를 불러오지 못했습니다.");
  await expect(page.getByRole("button", { name: "다시 시도" })).toBeVisible();
});
