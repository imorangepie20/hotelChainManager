import { expect, test } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";

test("lets a branch employee search reservations and open the selected reservation details", async ({ page }) => {
  let cancelled = false;
  let previewShouldFail = true;
  let cancellationMethod = "";
  const cancellationKeys: string[] = [];
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
    const reservations = cancelled || (query && query !== "김하늘") ? [] : [{
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
  await page.route("**/api/staff/reservations/*/cancellation-preview", (route) => {
    if (previewShouldFail) {
      return route.fulfill({
        status: 502,
        contentType: "application/json",
        body: JSON.stringify({ message: "취소 조건을 불러오지 못했습니다." }),
      });
    }
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        reservationId: "42000000-0000-0000-0000-000000000001",
        status: "CONFIRMED",
        cancellable: true,
        refundAmount: 420000,
        currency: "KRW",
        cutoffAt: "2026-09-14T09:00:00Z",
        unavailableReason: null,
      }),
    });
  });
  await page.route("**/api/staff/reservations/*/cancel", (route) => {
    cancellationMethod = route.request().method();
    cancellationKeys.push(route.request().headers()["idempotency-key"] ?? "");
    if (cancellationKeys.length === 1) {
      return route.fulfill({
        status: 502,
        contentType: "application/json",
        body: JSON.stringify({ message: "응답을 확인하지 못했습니다. 다시 시도해 주세요." }),
      });
    }
    if (cancellationKeys.length === 2) {
      return route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({ code: "REFUND_FAILED", message: "환불 처리에 실패했습니다. 잠시 후 다시 시도해 주세요." }),
      });
    }
    cancelled = true;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        reservationId: "42000000-0000-0000-0000-000000000001",
        status: "CANCELLED",
        refundAmount: 420000,
        currency: "KRW",
      }),
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

  await detailButton.click();
  const cancellationButton = page.getByRole("button", { name: "예약 취소", exact: true });
  await cancellationButton.press("Enter");
  await expect(page.getByRole("dialog").getByRole("alert")).toContainText("취소 조건을 불러오지 못했습니다");
  previewShouldFail = false;
  await cancellationButton.press("Enter");
  const confirmation = page.getByRole("alertdialog");
  await expect(confirmation).toContainText("예상 환불액 420,000원");
  await page.keyboard.press("Escape");
  await expect(confirmation).toBeHidden();
  await expect(cancellationButton).toBeFocused();

  await cancellationButton.press("Enter");
  await expect(confirmation).toBeVisible();
  const confirmButton = confirmation.getByRole("button", { name: "예약 취소 확정" });
  await confirmButton.press("Enter");

  await expect(confirmation.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await confirmButton.press("Enter");
  await expect(confirmation.getByRole("alert")).toContainText("환불 처리에 실패했습니다");
  await confirmButton.press("Enter");

  await expect(page.getByRole("status", { name: "예약 처리 결과" })).toContainText("예약이 취소되었습니다");
  expect(cancellationMethod).toBe("POST");
  expect(cancellationKeys).toHaveLength(3);
  expect(cancellationKeys[0]).not.toBe("");
  expect(cancellationKeys[1]).toBe(cancellationKeys[0]);
  expect(cancellationKeys[2]).not.toBe(cancellationKeys[1]);
  await expect(page.getByText("김하늘", { exact: true })).toHaveCount(0);
});

test("lets a branch employee correct confirmed reservation guest details", async ({ page }) => {
  let guestName = "김하늘";
  let guestEmail = "guest@example.com";
  const requestKeys: string[] = [];
  const requestBodies: unknown[] = [];
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
  await page.route("**/api/staff/hotels/*/reservations?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      hotelId: SOKCHO,
      date: "2026-09-13",
      truncated: false,
      reservations: [{
        reservationId: "42000000-0000-0000-0000-000000000001",
        guestName,
        guestEmail,
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
        assignedRoomNumbers: [],
      }],
    }),
  }));
  await page.route("**/api/staff/reservations/*/guest", async (route) => {
    requestKeys.push(route.request().headers()["idempotency-key"] ?? "");
    requestBodies.push(route.request().postDataJSON());
    if (requestKeys.length === 1) {
      return route.fulfill({
        status: 502,
        contentType: "application/json",
        body: JSON.stringify({ message: "응답을 확인하지 못했습니다. 다시 시도해 주세요." }),
      });
    }
    guestName = "김하늘 수정";
    guestEmail = "updated@example.com";
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        reservationId: "42000000-0000-0000-0000-000000000001",
        guestName,
        guestEmail,
      }),
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/reservations");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  const detail = page.getByRole("dialog");
  await detail.getByRole("button", { name: "예약자 정보 수정" }).press("Enter");
  await detail.getByLabel("예약자 이름").fill("  김하늘 수정  ");
  await detail.getByLabel("예약자 이메일").fill("updated@example.com");
  const saveButton = detail.getByRole("button", { name: "예약자 정보 저장" });
  await saveButton.press("Enter");
  await expect(detail.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await saveButton.press("Enter");

  await expect(page.getByRole("status", { name: "예약 처리 결과" })).toContainText("예약자 정보를 수정했습니다");
  await expect(page.getByRole("button", { name: "김하늘 수정 예약 상세" })).toBeVisible();
  expect(requestKeys).toHaveLength(2);
  expect(requestKeys[0]).not.toBe("");
  expect(requestKeys[1]).toBe(requestKeys[0]);
  expect(requestBodies).toEqual([
    { guestName: "김하늘 수정", guestEmail: "updated@example.com" },
    { guestName: "김하늘 수정", guestEmail: "updated@example.com" },
  ]);
});

test("lets a branch employee assign the remaining room from reservation details", async ({ page }) => {
  const assignedRoomNumbers = ["701"];
  const assignmentMethods: string[] = [];
  const assignmentBodies: unknown[] = [];
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
  await page.route("**/api/staff/hotels/*/reservations?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      hotelId: SOKCHO,
      date: "2026-09-13",
      truncated: false,
      reservations: [{
        reservationId: "42000000-0000-0000-0000-000000000001",
        guestName: "김하늘",
        guestEmail: "guest@example.com",
        roomTypeName: "디럭스 오션",
        ratePlanName: "조식 포함",
        checkIn: "2026-09-13",
        checkOut: "2026-09-15",
        adults: 4,
        children: 0,
        rooms: 2,
        status: "CONFIRMED",
        totalKrw: 840000,
        currency: "KRW",
        assignedRoomNumbers,
      }],
    }),
  }));
  await page.route("**/api/staff/reservations/*/assignable-rooms", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify([
      { id: "room-702", roomNumber: "702" },
      { id: "room-703", roomNumber: "703" },
    ]),
  }));
  await page.route("**/api/staff/reservations/*/assignments", (route) => {
    assignmentMethods.push(route.request().method());
    assignmentBodies.push(route.request().postDataJSON());
    if (assignmentBodies.length === 1) {
      assignedRoomNumbers.push("702");
      return route.fulfill({
        status: 502,
        contentType: "application/json",
        body: JSON.stringify({ message: "객실 배정에 실패했습니다. 다시 시도해 주세요." }),
      });
    }
    return route.fulfill({ status: 204 });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/reservations");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  const detail = page.getByRole("dialog");
  const openAssignment = detail.getByRole("button", { name: "객실 추가 배정" });
  await expect(openAssignment).toContainText("1/2");
  await openAssignment.press("Enter");
  const roomSelect = detail.getByLabel("배정할 객실");
  await roomSelect.focus();
  await page.keyboard.press("ArrowDown");
  await expect(roomSelect).toHaveValue("room-703");
  await page.keyboard.press("ArrowUp");
  await expect(roomSelect).toHaveValue("room-702");
  const assignButton = detail.getByRole("button", { name: "선택 객실 배정" });
  await assignButton.press("Enter");
  await expect(detail.getByRole("alert")).toContainText("객실 배정에 실패했습니다");

  await detail.getByRole("button", { name: "예약자 정보 수정" }).press("Enter");
  const dialogBox = await detail.boundingBox();
  expect(dialogBox).not.toBeNull();
  expect(dialogBox!.y).toBeGreaterThanOrEqual(16);
  expect(dialogBox!.y + dialogBox!.height).toBeLessThanOrEqual(828);
  const cancellationButton = detail.getByRole("button", { name: "예약 취소", exact: true });
  await detail.getByLabel("예약자 이름").focus();
  for (let index = 0; index < 6 && !await cancellationButton.evaluate((button) => button === document.activeElement); index += 1) {
    await page.keyboard.press("Tab");
  }
  await expect(cancellationButton).toBeFocused();
  await expect(cancellationButton).toBeInViewport();

  await assignButton.press("Enter");

  await expect(page.getByRole("status", { name: "예약 처리 결과" })).toContainText("702호를 배정했습니다");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  const refreshedDetail = page.getByRole("dialog");
  await expect(refreshedDetail).toContainText("701, 702호");
  await expect(refreshedDetail.getByRole("button", { name: "객실 추가 배정" })).toHaveCount(0);
  expect(assignmentMethods).toEqual(["POST", "POST"]);
  expect(assignmentBodies).toEqual([{ physicalRoomId: "room-702" }, { physicalRoomId: "room-702" }]);
});

test("lets a branch employee change one assigned room before check-in", async ({ page }) => {
  const assignedRoomNumbers = ["701"];
  const requestKeys: string[] = [];
  const requestMethods: string[] = [];
  const requestBodies: unknown[] = [];
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
  await page.route("**/api/staff/hotels/*/reservations?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      hotelId: SOKCHO,
      date: "2026-09-13",
      truncated: false,
      reservations: [{
        reservationId: "42000000-0000-0000-0000-000000000001",
        guestName: "김하늘",
        guestEmail: "guest@example.com",
        roomTypeName: "디럭스 오션",
        ratePlanName: "조식 포함",
        checkIn: "2026-09-13",
        checkOut: "2026-09-15",
        adults: 2,
        children: 0,
        rooms: 1,
        status: "CONFIRMED",
        totalKrw: 420000,
        currency: "KRW",
        assignedRoomNumbers,
      }],
    }),
  }));
  await page.route("**/api/staff/reservations/*/room-reassignment-options", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      reservationId: "42000000-0000-0000-0000-000000000001",
      assignments: [{ id: "room-701", roomNumber: "701" }],
      candidates: [
        { id: "room-702", roomNumber: "702" },
        { id: "room-703", roomNumber: "703" },
      ],
    }),
  }));
  await page.route("**/api/staff/reservations/*/assignments/*", (route) => {
    requestKeys.push(route.request().headers()["idempotency-key"] ?? "");
    requestMethods.push(route.request().method());
    requestBodies.push(route.request().postDataJSON());
    if (requestBodies.length < 3) {
      return route.fulfill({
        status: 502,
        contentType: "application/json",
        body: JSON.stringify({ message: "응답을 확인하지 못했습니다. 다시 시도해 주세요." }),
      });
    }
    assignedRoomNumbers.splice(0, 1, "703");
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        reservationId: "42000000-0000-0000-0000-000000000001",
        previousPhysicalRoomId: "room-701",
        previousRoomNumber: "701",
        physicalRoomId: "room-703",
        roomNumber: "703",
      }),
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/reservations");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  const detail = page.getByRole("dialog");
  await detail.getByRole("button", { name: "배정 객실 변경" }).press("Enter");
  await expect(detail.getByLabel("현재 배정 객실")).toHaveValue("room-701");
  const newRoom = detail.getByLabel("새 배정 객실");
  await expect(newRoom).toHaveValue("room-702");

  const submit = detail.getByRole("button", { name: "선택 객실로 변경" });
  await submit.press("Enter");
  await expect(detail.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await newRoom.selectOption("room-703");
  await submit.press("Enter");
  await expect(detail.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await submit.press("Enter");

  await expect(page.getByRole("status", { name: "예약 처리 결과" })).toContainText("701호를 703호로 변경했습니다");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  await expect(page.getByRole("dialog")).toContainText("703호");
  expect(requestMethods).toEqual(["PATCH", "PATCH", "PATCH"]);
  expect(requestBodies).toEqual([
    { newPhysicalRoomId: "room-702" },
    { newPhysicalRoomId: "room-703" },
    { newPhysicalRoomId: "room-703" },
  ]);
  expect(requestKeys[0]).not.toBe("");
  expect(requestKeys[1]).not.toBe(requestKeys[0]);
  expect(requestKeys[2]).toBe(requestKeys[1]);
});

test("lets a branch employee update the confirmed reservation party", async ({ page }) => {
  let adults = 2;
  let children = 0;
  const requestKeys: string[] = [];
  const requestBodies: unknown[] = [];
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
  await page.route("**/api/staff/hotels/*/reservations?*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({
      hotelId: SOKCHO,
      date: "2026-09-13",
      truncated: false,
      reservations: [{
        reservationId: "42000000-0000-0000-0000-000000000001",
        guestName: "김하늘",
        guestEmail: "guest@example.com",
        roomTypeName: "디럭스 오션",
        ratePlanName: "조식 포함",
        checkIn: "2026-09-13",
        checkOut: "2026-09-15",
        adults,
        children,
        rooms: 2,
        status: "CONFIRMED",
        totalKrw: 840000,
        currency: "KRW",
        assignedRoomNumbers: [],
      }],
    }),
  }));
  await page.route("**/api/staff/reservations/*/party", (route) => {
    requestKeys.push(route.request().headers()["idempotency-key"] ?? "");
    requestBodies.push(route.request().postDataJSON());
    if (requestBodies.length < 3) {
      return route.fulfill({
        status: 502,
        contentType: "application/json",
        body: JSON.stringify({ message: "응답을 확인하지 못했습니다. 다시 시도해 주세요." }),
      });
    }
    adults = 3;
    children = 0;
    return route.fulfill({
      contentType: "application/json",
      body: JSON.stringify({
        reservationId: "42000000-0000-0000-0000-000000000001",
        adults,
        children,
      }),
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/reservations");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  const detail = page.getByRole("dialog");
  await detail.getByRole("button", { name: "투숙 인원 변경" }).press("Enter");
  await detail.getByLabel("성인 인원").fill("3");
  await detail.getByLabel("아동 인원").fill("1");
  const submit = detail.getByRole("button", { name: "투숙 인원 저장" });
  await submit.press("Enter");
  await expect(detail.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");

  await detail.getByLabel("아동 인원").fill("0");
  await submit.press("Enter");
  await expect(detail.getByRole("alert")).toContainText("응답을 확인하지 못했습니다");
  await submit.press("Enter");

  await expect(page.getByRole("status", { name: "예약 처리 결과" })).toContainText("투숙 인원을 성인 3명 · 아동 0명으로 변경했습니다");
  await page.getByRole("button", { name: "김하늘 예약 상세" }).press("Enter");
  await expect(page.getByRole("dialog")).toContainText("성인 3명 · 아동 0명 · 2실");
  expect(requestBodies).toEqual([
    { adults: 3, children: 1 },
    { adults: 3, children: 0 },
    { adults: 3, children: 0 },
  ]);
  expect(requestKeys[0]).not.toBe("");
  expect(requestKeys[1]).not.toBe(requestKeys[0]);
  expect(requestKeys[2]).toBe(requestKeys[1]);
});
