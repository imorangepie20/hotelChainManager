import { expect, test, type Page } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";
const RESERVATION_ID = "42000000-0000-0000-0000-000000000001";
const REQUEST_ID = "43000000-0000-0000-0000-000000000001";

const reservation = {
  reservationId: RESERVATION_ID,
  guestName: "김하늘",
  guestEmail: "guest@example.com",
  roomTypeName: "스탠다드 시티",
  ratePlanName: "룸 온리",
  checkIn: "2026-10-10",
  checkOut: "2026-10-12",
  adults: 2,
  children: 0,
  rooms: 1,
  status: "CONFIRMED",
  totalKrw: 340000,
  currency: "KRW",
  assignedRoomNumbers: [],
};

function changeRequest(overrides: Record<string, unknown> = {}) {
  return {
    id: REQUEST_ID,
    reservationId: RESERVATION_ID,
    hotelId: SOKCHO,
    hotelName: "속초 지점",
    guestName: "김하늘",
    status: "PENDING_APPROVAL",
    settlementDirection: "CHARGE",
    version: 3,
    previousCheckIn: "2026-10-10",
    previousCheckOut: "2026-10-12",
    previousRoomTypeId: "room-standard",
    previousRoomTypeName: "스탠다드 시티",
    previousRatePlanId: "rate-room-only",
    previousRatePlanName: "룸 온리",
    targetCheckIn: "2026-10-20",
    targetCheckOut: "2026-10-23",
    targetRoomTypeId: "room-suite",
    targetRoomTypeName: "패밀리 스위트",
    targetRatePlanId: "rate-breakfast",
    targetRatePlanName: "패밀리 조식",
    rooms: 1,
    adults: 2,
    children: 0,
    approvalExpiresAt: "2026-09-15T00:00:00Z",
    quote: {
      id: "44000000-0000-0000-0000-000000000001",
      revision: 1,
      previousTotalKrw: 340000,
      totalKrw: 440001,
      differenceKrw: 100001,
      currency: "KRW",
      nightlyPrices: [
        { date: "2026-10-20", amount: 140000 },
        { date: "2026-10-21", amount: 150000 },
        { date: "2026-10-22", amount: 150001 },
      ],
      createdAt: "2026-09-14T00:00:00Z",
    },
    approval: null,
    actions: ["REPRICE", "CANCEL", "APPROVE", "REJECT"],
    events: [{
      id: "45000000-0000-0000-0000-000000000001",
      eventType: "REQUEST_CREATED",
      fromStatus: null,
      toStatus: "PENDING_APPROVAL",
      actorStaffId: "staff",
      reason: null,
      createdAt: "2026-09-14T00:00:00Z",
    }],
    ...overrides,
  };
}

async function mockSession(page: Page, role: "BRANCH_STAFF" | "HQ_ADMIN") {
  await page.addInitScript(({ hotelId, staffRole }) => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({
      id: "staff",
      email: staffRole === "HQ_ADMIN" ? "hq@hotel-chain.local" : "sokcho@hotel-chain.local",
      displayName: staffRole === "HQ_ADMIN" ? "본사 관리자" : "속초 직원",
      role: staffRole,
      hotelId: staffRole === "HQ_ADMIN" ? null : hotelId,
    }));
  }, { hotelId: SOKCHO, staffRole: role });
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/staff/hotels/*/reservations?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ hotelId: SOKCHO, date: "2026-09-14", truncated: false, reservations: [reservation] }),
  }));
  await page.route("**/api/staff/reservation-change-policy", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ settlementEnabled: false, directLimitKrw: 100000, approvalTtlSeconds: 86400, holdTtlSeconds: 900 }),
  }));
}

test("shows the branch limit and reuses the request key after a lost response", async ({ page }) => {
  await mockSession(page, "BRANCH_STAFF");
  const requestKeys: string[] = [];
  const requestBodies: unknown[] = [];
  const settlementCalls: string[] = [];
  let created = false;

  page.on("request", (request) => {
    if (/hold|payment-link|refund/.test(request.url())) settlementCalls.push(request.url());
  });
  await page.route("**/api/staff/reservation-change-requests?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify(created ? [changeRequest({ actions: ["REPRICE", "CANCEL"] })] : []),
  }));
  await page.route("**/api/staff/reservations/*/stay-change-preview", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      reservationId: RESERVATION_ID,
      checkIn: "2026-10-20",
      checkOut: "2026-10-23",
      currentTotalKrw: 340000,
      currency: "KRW",
      offers: [
        {
          roomTypeId: "room-deluxe",
          roomTypeName: "디럭스 오션",
          ratePlanId: "rate-room-only",
          ratePlanName: "룸 온리",
          breakfastIncluded: false,
          remaining: 2,
          nightlyPrices: [{ date: "2026-10-20", amount: 140000 }, { date: "2026-10-21", amount: 150000 }, { date: "2026-10-22", amount: 150000 }],
          totalKrw: 440000,
          differenceKrw: 100000,
          currency: "KRW",
        },
        {
          roomTypeId: "room-suite",
          roomTypeName: "패밀리 스위트",
          ratePlanId: "rate-breakfast",
          ratePlanName: "패밀리 조식",
          breakfastIncluded: true,
          remaining: 1,
          nightlyPrices: [{ date: "2026-10-20", amount: 140000 }, { date: "2026-10-21", amount: 150000 }, { date: "2026-10-22", amount: 150001 }],
          totalKrw: 440001,
          differenceKrw: 100001,
          currency: "KRW",
        },
      ],
    }),
  }));
  await page.route("**/api/staff/reservations/*/change-requests", (route) => {
    requestKeys.push(route.request().headers()["idempotency-key"] ?? "");
    requestBodies.push(route.request().postDataJSON());
    created = true;
    if (requestBodies.length === 1) {
      return route.fulfill({ status: 502, contentType: "application/json", body: JSON.stringify({ message: "응답을 확인하지 못했습니다. 다시 시도해 주세요." }) });
    }
    return route.fulfill({ contentType: "application/json", body: JSON.stringify(changeRequest({ actions: ["REPRICE", "CANCEL"] })) });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/reservations");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  const detail = page.getByRole("dialog");
  await detail.getByRole("button", { name: "숙박 일정·객실 유형 변경" }).press("Enter");
  await expect(detail).toContainText("직접 처리 한도 100,000원");
  await detail.getByLabel("변경 체크인").fill("2026-10-20");
  await detail.getByLabel("변경 체크아웃").fill("2026-10-23");
  await detail.getByRole("button", { name: "변경안 조회" }).press("Enter");
  await expect(detail.getByRole("status", { name: "예약 변경 승인 기준" })).toContainText("직접 처리 가능");
  await detail.getByLabel("변경할 객실·요금제").selectOption("room-suite:rate-breakfast");
  await expect(detail.getByRole("status", { name: "예약 변경 승인 기준" })).toContainText("본사 승인 필요");
  await expect(detail).toContainText("10월 20일");
  await expect(detail).toContainText("140,000원");

  const submit = detail.getByRole("button", { name: "본사 승인 요청" });
  await submit.press("Enter");
  await expect(detail.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await submit.press("Enter");

  await expect(detail.getByRole("status", { name: "예약 변경 상태" })).toContainText("본사 승인 대기");
  expect(requestKeys).toHaveLength(2);
  expect(requestKeys[0]).not.toBe("");
  expect(requestKeys[1]).toBe(requestKeys[0]);
  expect(requestBodies[1]).toEqual(requestBodies[0]);
  expect(settlementCalls).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test("lets HQ review the approval queue and sends the current version", async ({ page }) => {
  await mockSession(page, "HQ_ADMIN");
  const pending = changeRequest();
  const approvalBodies: unknown[] = [];
  await page.route("**/api/staff/reservation-change-requests?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify([pending]),
  }));
  await page.route(`**/api/staff/reservation-change-requests/${REQUEST_ID}/approve`, (route) => {
    approvalBodies.push(route.request().postDataJSON());
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(changeRequest({ status: "APPROVED", version: 4, actions: ["REPRICE", "CANCEL"] })),
    });
  });

  await page.goto("/dashboard/reservations");
  const queue = page.getByRole("region", { name: "승인 대기" });
  await expect(queue).toContainText("속초 지점");
  await expect(queue).toContainText("김하늘");
  await queue.getByRole("button", { name: "김하늘 변경 요청 검토" }).press("Enter");
  const review = page.getByRole("dialog");
  await expect(review).toContainText("스탠다드 시티");
  await expect(review).toContainText("패밀리 스위트");
  await expect(review).toContainText("10월 20일");

  await review.getByRole("button", { name: "승인" }).press("Enter");
  const confirmation = page.getByRole("alertdialog");
  await confirmation.getByRole("button", { name: "변경 승인 확정" }).press("Enter");
  await expect(page.getByRole("status", { name: "예약 변경 처리 결과" })).toContainText("승인했습니다");
  expect(approvalBodies).toEqual([{ version: 3 }]);
});

test("requires a rejection reason and restores focus when the dialog closes", async ({ page }) => {
  await mockSession(page, "HQ_ADMIN");
  const rejectionBodies: unknown[] = [];
  await page.route("**/api/staff/reservation-change-requests?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify([changeRequest()]),
  }));
  await page.route(`**/api/staff/reservation-change-requests/${REQUEST_ID}/reject`, (route) => {
    rejectionBodies.push(route.request().postDataJSON());
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify(changeRequest({ status: "REJECTED", version: 4, actions: [] })),
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/reservations");
  await page.getByRole("button", { name: "김하늘 변경 요청 검토" }).press("Enter");
  const rejectTrigger = page.getByRole("dialog").getByRole("button", { name: "반려" });
  await rejectTrigger.press("Enter");
  const confirmation = page.getByRole("alertdialog");
  await expect(confirmation.getByRole("button", { name: "변경 요청 반려" })).toBeDisabled();
  await confirmation.getByLabel("반려 사유").fill("객실 운영 계획과 맞지 않습니다.");
  await confirmation.getByRole("button", { name: "돌아가기" }).press("Enter");
  await expect(rejectTrigger).toBeFocused();

  await rejectTrigger.press("Enter");
  await page.getByRole("alertdialog").getByLabel("반려 사유").fill("객실 운영 계획과 맞지 않습니다.");
  await page.getByRole("alertdialog").getByRole("button", { name: "변경 요청 반려" }).press("Enter");
  await expect(page.getByRole("status", { name: "예약 변경 처리 결과" })).toContainText("반려했습니다");
  expect(rejectionBodies).toEqual([{ version: 3, reason: "객실 운영 계획과 맞지 않습니다." }]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});
