import { expect, test } from "@playwright/test";

const EVENT_TYPES = [
  "GUEST_UPDATE",
  "PARTY_UPDATE",
  "ROOM_REASSIGNMENT",
  "STAY_CHANGE",
  "CANCELLATION",
  "ROOM_OPERATIONAL_TRANSITION",
  "CHECKED_IN_ROOM_MOVE",
  "CHANGE_REQUEST_EVENT",
];

function sampleEvents(count: number) {
  return Array.from({ length: count }, (_, index) => ({
    eventType: EVENT_TYPES[index % EVENT_TYPES.length],
    createdAt: `2026-09-20T10:${String(index).padStart(2, "0")}:00+09:00`,
    staffEmail: "hq@example.test",
    staffDisplayName: "본사 관리자",
    staffRole: "HQ_ADMIN",
    hotelId: "11000000-0000-0000-0000-000000000001",
    hotelName: "속초 지점",
    reservationId: index % 2 === 0 ? "56000000-0000-0000-0000-000000000001" : null,
    guestName: index % 2 === 0 ? "감사 고객" : null,
    roomNumber: index % 3 === 0 ? "102" : null,
    summary: `감사 요약 ${index}`,
  }));
}

const EVENTS_BODY = (count: number, offset: number) =>
  JSON.stringify({
    events: sampleEvents(count),
    totalCount: 25,
    limit: 20,
    offset,
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

test("exposes the audit menu to headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "감사 이력" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "감사 이력" })).toHaveCount(0);
});

test("reports audit events with staff and reservation context", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/audit*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: EVENTS_BODY(8, 0) }),
  );

  await page.goto("/dashboard/audit");

  await expect(page.getByText("감사 이력 25건")).toBeVisible();
  await expect(page.getByRole("cell", { name: "예약자 정정" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "투숙 중 객실 이동" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "본사 관리자" }).first()).toBeVisible();
  await expect(page.getByRole("cell", { name: "속초 지점" }).first()).toBeVisible();
  await expect(page.getByRole("cell", { name: "감사 요약 0" })).toBeVisible();
});

test("labels event types in korean", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/audit*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: EVENTS_BODY(8, 0) }),
  );

  await page.goto("/dashboard/audit");

  for (const label of [
    "예약자 정정",
    "투숙 인원 변경",
    "배정 객실 변경",
    "숙박 조건 변경",
    "예약 취소",
    "객실 운영 상태",
    "투숙 중 객실 이동",
    "예약 변경 요청",
  ]) {
    await expect(page.getByRole("cell", { name: label })).toBeVisible();
  }
});

test("moves to the next page", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let offset = 0;
  await page.route("**/api/staff/audit*", async (route) => {
    const url = new URL(route.request().url());
    offset = Number(url.searchParams.get("offset") ?? 0);
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: EVENTS_BODY(5, offset),
    });
  });

  await page.goto("/dashboard/audit");
  await expect(page.getByText("감사 이력 25건")).toBeVisible();

  await page.getByRole("button", { name: "다음 페이지" }).click();

  await expect(page.getByTestId("audit-page")).toHaveText("2 / 2");
  await expect(page.getByRole("button", { name: "다음 페이지" })).toBeDisabled();
});

test("tells a branch employee the view is headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/audit");

  await expect(page.getByText("감사 이력은 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("shows a recovery notice when the audit is unreachable", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/audit*", (route) => route.fulfill({ status: 500 }));

  await page.goto("/dashboard/audit");

  await expect(page.getByText("감사 이력을 불러오지 못했습니다.")).toBeVisible();
});

test("reports an empty range without events", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/audit*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ events: [], totalCount: 0, limit: 20, offset: 0 }),
    }),
  );

  await page.goto("/dashboard/audit");

  await expect(page.getByText("해당 범위에 감사 이력이 없습니다.")).toBeVisible();
});
