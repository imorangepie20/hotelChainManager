import { expect, test } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";

test("lets a branch employee complete checkout and housekeeping from daily operations", async ({ page }) => {
  await page.addInitScript((hotelId) => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "staff", email: "sokcho@hotel-chain.local", displayName: "속초 직원", role: "BRANCH_STAFF", hotelId }));
  }, SOKCHO);
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
  await page.route("**/api/staff/hotels/*/operations?date=*", (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify({
    hotelId: SOKCHO, date: "2026-09-10", arrivals: [{ reservationId: "arrival-1", guestName: "이바다", roomTypeName: "디럭스 오션", status: "CONFIRMED", assignedRoomNumbers: [] }],
    departures: [{ reservationId: "reservation-1", guestName: "김하늘", roomTypeName: "디럭스 오션", status: "CHECKED_IN", assignedRoomNumbers: ["701"] }],
    roomsNeedingCleaning: [{ physicalRoomId: "room-701", roomNumber: "701", roomTypeName: "디럭스 오션", housekeepingStatus: "NEEDS_CLEANING" }],
  }) }));
  await page.route("**/api/staff/reservations/reservation-1/check-out", (route) => route.fulfill({ status: 204 }));
  await page.route("**/api/staff/rooms/room-701/housekeeping-complete", (route) => route.fulfill({ status: 204 }));
  await page.route("**/api/staff/reservations/arrival-1/assignable-rooms", (route) => route.fulfill({ contentType: "application/json", body: JSON.stringify([{ id: "room-702", roomNumber: "702" }]) }));
  await page.route("**/api/staff/reservations/arrival-1/assignments", (route) => route.fulfill({ status: 204 }));
  await page.route("**/api/staff/reservations/arrival-1/no-show", (route) => route.fulfill({ status: 204 }));

  await page.goto("/dashboard/operations");
  await expect(page.getByText("도착 예정", { exact: true })).toBeVisible();
  await expect(page.getByText("출발 예정", { exact: true })).toBeVisible();
  await expect(page.getByText("청소 필요 객실", { exact: true })).toBeVisible();
  await expect(page.getByRole("button", { name: "체크아웃 처리" })).toBeVisible();
  await expect(page.getByRole("button", { name: "청소 완료" })).toBeVisible();
  await page.getByRole("button", { name: "객실 배정" }).click();
  await page.getByLabel("이바다 객실 선택").selectOption("room-702");
  await page.getByRole("button", { name: "배정 완료" }).click();
  await expect(page.getByText("702호를 배정했습니다.")).toBeVisible();
  await page.getByRole("button", { name: "노쇼 처리" }).click();
  await expect(page.getByText("노쇼 처리가 완료되었습니다.")).toBeVisible();

  await page.getByRole("button", { name: "체크아웃 처리" }).click();
  await expect(page.getByText("체크아웃 처리가 완료되었습니다.")).toBeVisible();
  await page.getByRole("button", { name: "청소 완료" }).click();
  await expect(page.getByText("청소 완료 처리가 완료되었습니다.")).toBeVisible();
});
