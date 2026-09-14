import { expect, test } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";
const RESERVATION = "reservation-1";

async function setup(page: import("@playwright/test").Page) {
  await page.addInitScript((hotelId) => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({
      id: "staff", email: "sokcho@hotel-chain.local", displayName: "속초 직원",
      role: "BRANCH_STAFF", hotelId,
    }));
  }, SOKCHO);
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/staff/hotels/*/reservations?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      hotelId: SOKCHO,
      date: new URL(route.request().url()).searchParams.get("date"),
      truncated: false,
      reservations: [{
        reservationId: RESERVATION, guestName: "김하늘", guestEmail: "guest@example.com",
        roomTypeName: "디럭스 오션", ratePlanName: "조식 포함", checkIn: "2026-09-13",
        checkOut: "2026-09-15", adults: 2, children: 0, rooms: 1, status: "CHECKED_IN",
        totalKrw: 420000, currency: "KRW", assignedRoomNumbers: ["701"],
      }],
    }),
  }));
  await page.route("**/api/staff/reservations/reservation-1/checked-in-room-move-options", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      reservationId: RESERVATION,
      assignments: [{ id: "room-701", roomNumber: "701" }],
      candidates: [{ id: "room-702", roomNumber: "702" }, { id: "room-703", roomNumber: "703" }],
    }),
  }));
}

test("moves a checked-in guest with confirmation and safe retry", async ({ page }) => {
  await setup(page);
  const keys: string[] = [];
  const bodies: unknown[] = [];
  await page.route("**/api/staff/reservations/reservation-1/checked-in-room-moves", (route) => {
    keys.push(route.request().headers()["idempotency-key"] ?? "");
    bodies.push(route.request().postDataJSON());
    if (keys.length === 1) {
      return route.fulfill({ status: 502, contentType: "application/json", body: JSON.stringify({ message: "응답을 확인하지 못했습니다." }) });
    }
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        reservationId: RESERVATION, previousPhysicalRoomId: "room-701", previousRoomNumber: "701",
        physicalRoomId: "room-702", roomNumber: "702", reason: "에어컨 소음", movedAt: "2026-09-14T09:00:00Z",
      }),
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/reservations");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).click();
  const detail = page.getByRole("dialog");
  await expect(detail.getByRole("heading", { name: "투숙 중 객실 이동" })).toBeVisible();
  await detail.getByRole("button", { name: "이동 후보 조회" }).click();
  await detail.getByLabel("현재 객실").selectOption("room-701");
  await detail.getByLabel("새 객실").selectOption("room-702");
  const confirmTrigger = detail.getByRole("button", { name: "객실 이동 확인" });
  await expect(confirmTrigger).toBeDisabled();
  await detail.getByLabel("이동 사유").fill("  에어컨 소음  ");
  await confirmTrigger.click();

  const confirmation = page.getByRole("alertdialog");
  const box = await confirmation.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(12);
  expect(box!.x + box!.width).toBeLessThanOrEqual(378);
  await page.keyboard.press("Escape");
  await expect(confirmation).toBeHidden();
  await expect(confirmTrigger).toBeFocused();

  await confirmTrigger.click();
  const submit = confirmation.getByRole("button", { name: "객실 이동 확정" });
  await submit.click();
  await expect(confirmation.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await submit.click();

  await expect(page.getByText("702호로 이동했습니다.")).toBeVisible();
  expect(keys).toHaveLength(2);
  expect(keys[0]).not.toBe("");
  expect(keys[1]).toBe(keys[0]);
  expect(bodies).toEqual([
    { currentPhysicalRoomId: "room-701", newPhysicalRoomId: "room-702", reason: "에어컨 소음" },
    { currentPhysicalRoomId: "room-701", newPhysicalRoomId: "room-702", reason: "에어컨 소음" },
  ]);
});

test("opens the matching reservation once from a room-impact deep link", async ({ page }) => {
  await setup(page);
  const reservationRequest = page.waitForRequest((request) => {
    const url = new URL(request.url());
    return url.pathname.includes("/reservations") && url.searchParams.get("date") === "2026-09-14";
  });
  await page.goto(`/dashboard/reservations?date=2026-09-14&reservationId=${RESERVATION}`);
  await reservationRequest;

  const detail = page.getByRole("dialog");
  await expect(detail).toContainText("김하늘 고객 예약");
  await page.keyboard.press("Escape");
  await expect(detail).toBeHidden();
  await page.waitForTimeout(100);
  await expect(detail).toBeHidden();
});
