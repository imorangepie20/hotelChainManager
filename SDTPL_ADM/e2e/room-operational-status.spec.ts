import { expect, test } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";

async function seedSession(page: import("@playwright/test").Page) {
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
  await page.route("**/api/staff/hotels/*/operations?date=*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ hotelId: SOKCHO, date: "2026-09-14", arrivals: [], departures: [], roomsNeedingCleaning: [] }),
  }));
}

test("shows impacted reservations and blocks an unsafe sales stop", async ({ page }) => {
  await seedSession(page);
  await page.route("**/api/staff/hotels/*/room-operations", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      hotelId: SOKCHO,
      summary: { inspectionRequired: 1, outOfService: 0, overdueRecovery: 0 },
      rooms: [{
        physicalRoomId: "room-702",
        roomNumber: "702",
        roomTypeName: "디럭스 오션",
        housekeepingStatus: "CLEAN",
        operationalStatus: "INSPECTION_REQUIRED",
        operationalReason: "소음 점검",
        expectedRecoveryAt: null,
        operationalVersion: 1,
        impactedAssignments: [{
          reservationId: "reservation-1",
          guestName: "김하늘",
          status: "CHECKED_IN",
          checkIn: "2026-09-13",
          checkOut: "2026-09-15",
        }],
        events: [],
      }],
    }),
  }));

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/operations");
  const trigger = page.getByRole("button", { name: "702호 판매 중지" });
  await expect(page.getByRole("region", { name: "객실 운영 상태" })).toBeVisible();
  await expect(page.getByText("점검 필요 1건")).toBeVisible();
  await trigger.press("Enter");

  const dialog = page.getByRole("alertdialog");
  await expect(dialog.getByText("영향 예약 1건")).toBeVisible();
  await expect(dialog.getByRole("button", { name: "판매 중지 확정" })).toBeDisabled();
  await expect(dialog.getByRole("link", { name: "김하늘 예약 보기" }))
    .toHaveAttribute("href", `/dashboard/reservations?date=2026-09-13&hotelId=${SOKCHO}&reservationId=reservation-1`);
  const box = await dialog.boundingBox();
  expect(box).not.toBeNull();
  expect(box!.x).toBeGreaterThanOrEqual(12);
  expect(box!.x + box!.width).toBeLessThanOrEqual(378);

  await page.keyboard.press("Escape");
  await expect(dialog).toBeHidden();
  await expect(trigger).toBeFocused();
});

test("keeps the idempotency key for a retry and refreshes after success", async ({ page }) => {
  await seedSession(page);
  let reads = 0;
  let transitioned = false;
  const requestKeys: string[] = [];
  const requestBodies: unknown[] = [];
  await page.route("**/api/staff/hotels/*/room-operations", (route) => {
    reads += 1;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: SOKCHO,
        summary: { inspectionRequired: transitioned ? 1 : 0, outOfService: 0, overdueRecovery: 0 },
        rooms: [{
          physicalRoomId: "room-701", roomNumber: "701", roomTypeName: "디럭스 오션",
          housekeepingStatus: "CLEAN", operationalStatus: transitioned ? "INSPECTION_REQUIRED" : "AVAILABLE",
          operationalReason: transitioned ? "배관 점검" : null, expectedRecoveryAt: null,
          operationalVersion: transitioned ? 1 : 0, impactedAssignments: [], events: [],
        }],
      }),
    });
  });
  await page.route("**/api/staff/rooms/room-701/operational-transitions", (route) => {
    requestKeys.push(route.request().headers()["idempotency-key"] ?? "");
    requestBodies.push(route.request().postDataJSON());
    if (requestKeys.length === 1) {
      return route.fulfill({ status: 502, contentType: "application/json", body: JSON.stringify({ message: "응답을 확인하지 못했습니다." }) });
    }
    transitioned = true;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        physicalRoomId: "room-701", roomNumber: "701", housekeepingStatus: "CLEAN",
        operationalStatus: "INSPECTION_REQUIRED", operationalReason: "배관 점검",
        expectedRecoveryAt: null, operationalVersion: 1,
      }),
    });
  });

  await page.goto("/dashboard/operations");
  await page.getByRole("button", { name: "701호 점검 필요로 변경" }).click();
  const dialog = page.getByRole("alertdialog");
  await dialog.getByLabel("변경 사유").fill("  배관 점검  ");
  const submit = dialog.getByRole("button", { name: "점검 필요 확정" });
  await submit.click();
  await expect(dialog.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await submit.click();

  await expect(dialog).toBeHidden();
  await expect(page.getByText("객실 운영 상태를 변경했습니다.")).toBeVisible();
  expect(requestKeys).toHaveLength(2);
  expect(requestKeys[0]).not.toBe("");
  expect(requestKeys[1]).toBe(requestKeys[0]);
  expect(requestBodies).toEqual([
    { targetStatus: "INSPECTION_REQUIRED", reason: "배관 점검", expectedRecoveryAt: null, expectedVersion: 0 },
    { targetStatus: "INSPECTION_REQUIRED", reason: "배관 점검", expectedRecoveryAt: null, expectedVersion: 0 },
  ]);
  expect(reads).toBeGreaterThan(1);
});

test("refreshes impacted reservations and version after a transition conflict", async ({ page }) => {
  await seedSession(page);
  let reads = 0;
  let conflictSeen = false;
  let submittedBody: Record<string, unknown> | null = null;
  const assignment = {
    reservationId: "future-reservation", guestName: "박미래", status: "CONFIRMED",
    checkIn: "2026-10-10", checkOut: "2026-10-12",
  };
  await page.route("**/api/staff/hotels/*/room-operations", (route) => {
    reads += 1;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: SOKCHO,
        summary: { inspectionRequired: 0, outOfService: 0, overdueRecovery: 0 },
        rooms: [{
          physicalRoomId: "room-801", roomNumber: "801", roomTypeName: "스위트",
          housekeepingStatus: "CLEAN", operationalStatus: "AVAILABLE", operationalReason: null,
          expectedRecoveryAt: null, operationalVersion: conflictSeen ? 1 : 0,
          impactedAssignments: conflictSeen ? [assignment] : [], events: [],
        }],
      }),
    });
  });
  await page.route("**/api/staff/rooms/room-801/operational-transitions", (route) => {
    conflictSeen = true;
    submittedBody = route.request().postDataJSON();
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({ code: "ROOM_HAS_ACTIVE_ASSIGNMENTS", message: "영향 예약이 있습니다.", assignments: [assignment] }),
    });
  });

  await page.goto("/dashboard/operations");
  await page.getByRole("button", { name: "801호 판매 중지" }).click();
  const dialog = page.getByRole("alertdialog");
  await dialog.getByLabel("변경 사유").fill("장기 수리");
  await dialog.getByRole("button", { name: "판매 중지 확정" }).click();

  await expect(dialog.getByText("영향 예약 1건")).toBeVisible();
  await expect(dialog.getByRole("button", { name: "판매 중지 확정" })).toBeDisabled();
  await expect(dialog.getByRole("link", { name: "박미래 예약 보기" })).toHaveAttribute(
    "href",
    `/dashboard/reservations?date=2026-10-10&hotelId=${SOKCHO}&reservationId=future-reservation`,
  );
  expect(submittedBody).toMatchObject({ expectedVersion: 0 });
  expect(reads).toBeGreaterThan(1);
});
