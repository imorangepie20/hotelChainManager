import { expect, test } from "@playwright/test";

const INVENTORY_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  roomTypes: [
    {
      roomTypeId: "21000000-0000-0000-0000-000000000001",
      name: "스탠다드 시티",
      maxOccupancy: 2,
      days: [
        { stayDate: "2026-11-01", capacity: 10, held: 2, confirmed: 7, remaining: 1, salesStatus: "OPEN" },
        { stayDate: "2026-11-02", capacity: 5, held: 0, confirmed: 5, remaining: 0, salesStatus: "OPEN" },
      ],
    },
    {
      roomTypeId: "21000000-0000-0000-0000-000000000002",
      name: "디럭스 오션",
      maxOccupancy: 3,
      days: [{ stayDate: "2026-11-01", capacity: 8, held: 1, confirmed: 2, remaining: 5, salesStatus: "OPEN" }],
    },
  ],
});

const HOTELS = [
  {
    id: "11000000-0000-0000-0000-000000000001",
    name: "속초 지점",
    region: "속초",
    timezone: "Asia/Seoul",
    active: true,
  },
  {
    id: "11000000-0000-0000-0000-000000000099",
    name: "강릉 지점",
    region: "강릉",
    timezone: "Asia/Seoul",
    active: true,
  },
];

// 중지한 날짜가 있으면 재고 화면이 "매진"과 다르게 표시한다.
const STOPPED_INVENTORY_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  roomTypes: [
    {
      roomTypeId: "21000000-0000-0000-0000-000000000001",
      name: "스탠다드 시티",
      maxOccupancy: 2,
      days: [
        { stayDate: "2026-11-01", capacity: 10, held: 2, confirmed: 7, remaining: 1, salesStatus: "STOPPED" },
        { stayDate: "2026-11-02", capacity: 5, held: 0, confirmed: 5, remaining: 0, salesStatus: "OPEN" },
      ],
    },
  ],
});

const STOPPED_STATUS_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  roomTypeId: "21000000-0000-0000-0000-000000000001",
  stoppedRanges: [{ fromDate: "2026-11-01", toDate: "2026-11-01" }],
});

const EMPTY_STATUS_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  roomTypeId: "21000000-0000-0000-0000-000000000001",
  stoppedRanges: [],
});

// 재고 화면의 기본 범위는 오늘부터 14일이다. 날짜 입력의 기본값을
// 확인하려면 오늘 날짜가 필요하다.
function today(): string {
  return new Date().toISOString().slice(0, 10);
}

function todayPlus(days: number): string {
  return new Date(Date.now() + days * 86400000).toISOString().slice(0, 10);
}

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

// "판매 중지" 버튼이 든 셀도 매칭되므로 정확한 텍스트로 좁힌다.
function stoppedCell(page: import("@playwright/test").Page) {
  return page
    .locator("[data-room-type-id='21000000-0000-0000-0000-000000000001']")
    .locator("td")
    .filter({ hasText: "중지" })
    .filter({ hasNot: page.getByRole("button") });
}

function soldOutCell(page: import("@playwright/test").Page) {
  return page
    .locator("[data-room-type-id='21000000-0000-0000-0000-000000000001']")
    .locator("td")
    .filter({ hasText: "매진" })
    .filter({ hasNot: page.getByRole("button") });
}

test.beforeEach(async ({ page }) => {
  await page.route("**/api/staff/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );
  await page.route("**/api/staff/hotels", (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(HOTELS),
    });
  });
});

test("exposes the inventory menu to headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "재고·가격" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "재고·가격" })).toHaveCount(0);
});

test("reports daily inventory with sold-out markers in a room-type grid", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );

  await page.goto("/dashboard/inventory");

  await expect(page.getByText("객실 유형 2종 · 숙박일 2일")).toBeVisible();
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();
  await expect(page.getByRole("rowheader", { name: "디럭스 오션" })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: /11월 1일/ })).toBeVisible();
  // 매진 셀과 잔여 셀이 같은 그리드에 함께 표시된다.
  await expect(page.getByRole("cell", { name: "매진" })).toBeVisible();
  await expect(page.locator("td").filter({ hasText: "5" })).toBeVisible();
});

test("uses the server hotel list and keeps the selected inventory branch explicit", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const requestedHotelIds: string[] = [];
  await page.route("**/api/staff/hotels/*/inventory**", (route) => {
    const hotelId = new URL(route.request().url()).pathname.split("/").at(-2);
    if (hotelId) requestedHotelIds.push(hotelId);
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ...JSON.parse(INVENTORY_BODY),
        hotelId,
      }),
    });
  });

  await page.goto("/dashboard/inventory");

  const sokchoSelector = page.getByRole("button", { name: "속초 지점" });
  const gangneungSelector = page.getByRole("button", { name: "강릉 지점" });
  await expect(sokchoSelector).toHaveAttribute("aria-pressed", "true");
  await expect(gangneungSelector).toHaveAttribute("aria-pressed", "false");
  await expect(page.getByRole("button", { name: "설악산 지점" })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "제주 지점" })).toHaveCount(0);
  await expect(
    page.getByText("속초 지점 · 객실 유형 2종 · 숙박일 2일", { exact: true }),
  ).toBeVisible();

  await gangneungSelector.click();

  await expect(gangneungSelector).toHaveAttribute("aria-pressed", "true");
  await expect(sokchoSelector).toHaveAttribute("aria-pressed", "false");
  await expect(
    page.getByText("강릉 지점 · 객실 유형 2종 · 숙박일 2일", { exact: true }),
  ).toBeVisible();
  await expect
    .poll(() => requestedHotelIds)
    .toContain("11000000-0000-0000-0000-000000000099");
});

test("hides the previous inventory while the next branch loads", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let releaseGangneung: (() => void) | undefined;
  const gangneungPending = new Promise<void>((resolve) => {
    releaseGangneung = resolve;
  });
  await page.route("**/api/staff/hotels/*/inventory**", async (route) => {
    const hotelId = new URL(route.request().url()).pathname.split("/").at(-2);
    if (hotelId === "11000000-0000-0000-0000-000000000099") {
      await gangneungPending;
    }
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ ...JSON.parse(INVENTORY_BODY), hotelId }),
    });
  });

  await page.goto("/dashboard/inventory");
  await expect(
    page.getByText("속초 지점 · 객실 유형 2종 · 숙박일 2일", { exact: true }),
  ).toBeVisible();

  await page.getByRole("button", { name: "강릉 지점" }).click();

  await expect(
    page.getByText("속초 지점 · 객실 유형 2종 · 숙박일 2일", { exact: true }),
  ).toHaveCount(0);
  await expect(page.getByText("강릉 지점 재고를 불러오는 중입니다.")).toBeVisible();

  releaseGangneung?.();
  await expect(
    page.getByText("강릉 지점 · 객실 유형 2종 · 숙박일 2일", { exact: true }),
  ).toBeVisible();
});

test("ignores late rate details from the previously selected branch", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const firstRoomTypeId = "21000000-0000-0000-0000-000000000001";
  const secondRoomTypeId = "21000000-0000-0000-0000-000000000099";
  let releaseFirstRates: (() => void) | undefined;
  const firstRatesPending = new Promise<void>((resolve) => {
    releaseFirstRates = resolve;
  });

  await page.route("**/api/staff/hotels/*/room-types/*/rates**", async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    const url = new URL(route.request().url());
    const roomTypeId = url.pathname.split("/").at(-2);
    if (roomTypeId === firstRoomTypeId) await firstRatesPending;
    const isSecond = roomTypeId === secondRoomTypeId;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: isSecond ? HOTELS[1].id : HOTELS[0].id,
        roomTypeId,
        ratePlanId: "31000000-0000-0000-0000-000000000099",
        ratePlanName: "current plan",
        days: [{ stayDate: "2026-11-01", amountKrw: isSecond ? 222000 : 111000 }],
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/inventory**", (route) => {
    const hotelId = new URL(route.request().url()).pathname.split("/").at(-2);
    const isSecond = hotelId === HOTELS[1].id;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId,
        roomTypes: [
          {
            roomTypeId: isSecond ? secondRoomTypeId : firstRoomTypeId,
            name: isSecond ? "Gangneung room" : "Sokcho room",
            maxOccupancy: 2,
            days: [
              {
                stayDate: "2026-11-01",
                capacity: 10,
                held: 0,
                confirmed: 0,
                remaining: 10,
                salesStatus: "OPEN",
              },
            ],
          },
        ],
      }),
    });
  });

  await page.goto("/dashboard/inventory");
  await page.getByTestId(`adjust-rates-${firstRoomTypeId}`).click();
  await page.keyboard.press("Escape");
  await page.getByRole("button", { name: HOTELS[1].name }).click();
  await page.getByTestId(`adjust-rates-${secondRoomTypeId}`).click();

  await expect(page.getByTestId("rate-day-2026-11-01")).toHaveValue("222000");
  releaseFirstRates?.();
  await expect(page.getByTestId("rate-day-2026-11-01")).toHaveValue("222000");
});

test("keeps every room type visible without switching", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );

  await page.goto("/dashboard/inventory");

  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();
  await expect(page.getByRole("rowheader", { name: "디럭스 오션" })).toBeVisible();

  // 두 유형 모두 같은 숙박일 열에 값을 둔다.
  const standardRow = page.getByRole("row").filter({ has: page.getByRole("rowheader", { name: "스탠다드 시티" }) });
  const deluxeRow = page.getByRole("row").filter({ has: page.getByRole("rowheader", { name: "디럭스 오션" }) });
  await expect(standardRow.locator("td").filter({ hasText: "1" })).toBeVisible();
  await expect(deluxeRow.locator("td").filter({ hasText: "5" })).toBeVisible();
});

test("applies a quick range preset and reloads", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let requested = "";
  await page.route("**/api/staff/hotels/*/inventory**", (route) => {
    const url = new URL(route.request().url());
    requested = `${url.searchParams.get("from")}~${url.searchParams.get("to")}`;
    return route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY });
  });

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();

  await page.getByRole("button", { name: "7일" }).click();

  await expect.poll(() => requested).toMatch(/~/);
});

test("tells a branch employee the inventory view is headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/inventory");

  await expect(page.getByText("재고·가격 관리는 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("shows a recovery notice when inventory is unreachable", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) => route.fulfill({ status: 500 }));

  await page.goto("/dashboard/inventory");

  await expect(page.getByText("재고를 불러오지 못했습니다.")).toBeVisible();
});

test("lets headquarters adjust capacity and keeps the sold-out marker", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // GET과 PATCH를 같은 패턴으로 잡으므로 메서드로 나눈다.
  await page.route("**/api/staff/hotels/*/inventory**", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: INVENTORY_BODY,
      });
    }
    const payload = JSON.parse(route.request().postData() ?? "{}");
    const capacity = payload.adjustments?.[0]?.capacity;
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        roomTypeId: payload.roomTypeId,
        days: [
          { stayDate: "2026-11-01", capacity, held: 0, confirmed: 2, remaining: Math.max(0, capacity - 2) },
          { stayDate: "2026-11-02", capacity, held: 0, confirmed: 2, remaining: Math.max(0, capacity - 2) },
        ],
        created: true,
      }),
    });
  });

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();

  await page.getByRole("button", { name: "스탠다드 시티 총량 조정" }).click();
  await page.getByLabel("새 총량").fill("4");
  await page.getByRole("button", { name: "조정" }).click();

  await expect(page.getByTestId("adjust-inventory-notice")).toContainText("2일의 총량을 4실로");
  await expect(page.getByRole("button", { name: "스탠다드 시티 총량 조정" })).toBeVisible();
});

test("locks branch selection while an inventory write is pending", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let releaseAdjustment: (() => void) | undefined;
  const adjustmentPending = new Promise<void>((resolve) => {
    releaseAdjustment = resolve;
  });
  await page.route("**/api/staff/hotels/*/inventory**", async (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: INVENTORY_BODY,
      });
    }
    await adjustmentPending;
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        roomTypeId: "21000000-0000-0000-0000-000000000001",
        days: [],
        created: true,
      }),
    });
  });

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();
  await page.getByRole("button", { name: "스탠다드 시티 총량 조정" }).click();
  await page.getByLabel("새 총량").fill("4");
  await page.getByRole("button", { name: "조정" }).click();
  await page.keyboard.press("Escape");

  const gangneungSelector = page.getByRole("button", { name: "강릉 지점" });
  await expect(gangneungSelector).toBeDisabled();

  releaseAdjustment?.();
  await expect(gangneungSelector).toBeEnabled();
});

test("reports a capacity conflict without closing the adjust dialog", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: INVENTORY_BODY,
      });
    }
    // message를 주지 않아 클라이언트가 코드별 한국어 안내로 넘어가는지 확인한다.
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({ code: "INVENTORY_CAPACITY_CONFLICT" }),
    });
  });

  await page.goto("/dashboard/inventory");
  await page.getByRole("button", { name: "디럭스 오션 총량 조정" }).click();
  await page.getByLabel("새 총량").fill("1");
  await page.getByRole("button", { name: "조정" }).click();

  await expect(page.getByText("총량을 내릴 수 없습니다.")).toBeVisible();
  // 서버 검증 실패 시 대화상자를 닫지 않는다.
  await expect(page.getByRole("button", { name: "조정" })).toBeVisible();
});

test("keeps the adjust button away from branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/inventory");

  await expect(page.getByText("재고·가격 관리는 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByRole("button", { name: /총량 조정/ })).toHaveCount(0);
});

test("does not overflow horizontally at 390px with the adjust column", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) => {
    if (route.request().method() !== "GET") {
      return route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({ days: [], created: true }),
      });
    }
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: INVENTORY_BODY,
    });
  });

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/inventory");

  await expect(page.getByRole("button", { name: "스탠다드 시티 총량 조정" })).toBeVisible();

  const overflow = await page.evaluate(() => ({
    documentWidth: document.documentElement.scrollWidth,
    viewportWidth: window.innerWidth,
  }));
  expect(overflow.documentWidth).toBeLessThanOrEqual(overflow.viewportWidth);
});

const RATES_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  roomTypeId: "21000000-0000-0000-0000-000000000001",
  ratePlanId: "31000000-0000-0000-0000-000000000001",
  ratePlanName: "스탠다드 시티 기본",
  days: [
    { stayDate: "2026-11-01", amountKrw: 100000 },
    { stayDate: "2026-11-02", amountKrw: 120000 },
  ],
});

async function rateRequestHandler(route: import("@playwright/test").Route) {
  if (route.request().method() === "GET") {
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: RATES_BODY,
    });
    return;
  }
  const body = route.request().postDataJSON() as {
    adjustments: Array<{ stayDate: string; amountKrw: number }>;
  };
  const days = (body.adjustments ?? []).map((entry) => ({
    stayDate: entry.stayDate,
    amountKrw: entry.amountKrw,
  }));
  await route.fulfill({
    status: 201,
    contentType: "application/json",
    body: JSON.stringify({
      hotelId: "11000000-0000-0000-0000-000000000001",
      roomTypeId: "21000000-0000-0000-0000-000000000001",
      ratePlanId: "31000000-0000-0000-0000-000000000001",
      days,
      created: true,
    }),
  });
}

test("lets headquarters change a single day rate", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );
  await page.route(
    "**/api/staff/hotels/*/room-types/21000000-0000-0000-0000-000000000001/rates**",
    rateRequestHandler,
  );

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();

  await page.getByRole("button", { name: "스탠다드 시티 요금 조정" }).click();
  // 대화상자가 서버에서 읽은 현재 요금으로 미리 채운다.
  await expect(page.getByTestId("rate-day-2026-11-01")).toHaveValue("100000");

  await page.getByTestId("rate-day-2026-11-01").fill("150000");
  // 바꾼 날짜만 센다.
  await expect(page.getByTestId("adjust-rates-submit")).toHaveText("1일 변경");

  await page.getByTestId("adjust-rates-submit").click();

  await expect(page.getByTestId("adjust-rates-notice")).toContainText("1일 요금을 바꿨습니다");
  await expect(page.getByTestId("adjust-rates-notice")).toContainText("150,000원");
  // 대화상자가 닫힌다.
  await expect(page.getByTestId("adjust-rates-submit")).toHaveCount(0);
});

test("keeps the rate dialog open when the day has no price", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );
  await page.route(
    "**/api/staff/hotels/*/room-types/21000000-0000-0000-0000-000000000001/rates**",
    async (route) => {
      // PATCH 응답을 404 RATE_DAY_NOT_FOUND로 내려서
      // 대화상자가 닫히지 않고 이유를 보여주는지 확인한다.
      if (route.request().method() !== "GET") {
        await route.fulfill({
          status: 404,
          contentType: "application/json",
          body: JSON.stringify({ code: "RATE_DAY_NOT_FOUND" }),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: RATES_BODY,
      });
    },
  );

  await page.goto("/dashboard/inventory");
  await page.getByRole("button", { name: "스탠다드 시티 요금 조정" }).click();
  // 대화상자가 서버에서 읽은 현재 요금으로 미리 채운다.
  await expect(page.getByTestId("rate-day-2026-11-01")).toHaveValue("100000");

  await page.getByTestId("rate-day-2026-11-01").fill("150000");
  await page.getByTestId("adjust-rates-submit").click();

  await expect(page.getByTestId("rate-error")).toContainText("해당 날짜의 요금이 없습니다");
  // 서버 검증 실패 시 대화상자를 닫지 않는다.
  await expect(page.getByTestId("adjust-rates-submit")).toBeVisible();
});

test("explains when the room type has no rate plan", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );
  await page.route("**/api/staff/hotels/*/room-types/*/rates**", async (route) => {
    if (route.request().method() === "GET") {
      await route.fulfill({
        status: 404,
        // message를 주지 않아 클라이언트가 코드별 한국어 안내로 넘어가는지 확인한다.
        body: JSON.stringify({ code: "ROOM_TYPE_RATE_PLAN_NOT_FOUND" }),
      });
      return;
    }
    await route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  });

  await page.goto("/dashboard/inventory");
  await page.getByRole("button", { name: "스탠다드 시티 요금 조정" }).click();

  await expect(page.getByTestId("rate-error")).toContainText("이 객실 유형에는 요금제가 없습니다");
  // 요금을 읽지 못했으면 입력 폼을 숨긴다.
  await expect(page.getByTestId("adjust-rates-submit")).toHaveCount(0);
});

test("keeps the rate button away from branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/inventory");

  await expect(page.getByText("재고·가격 관리는 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByRole("button", { name: /요금 조정/ })).toHaveCount(0);
});

test("marks stopped days differently from sold-out days", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: STOPPED_INVENTORY_BODY }),
  );

  await page.goto("/dashboard/inventory");

  // 중지 셀과 매진 셀이 같은 그리드에 함께 표시된다.
  await expect(stoppedCell(page)).toBeVisible();
  await expect(soldOutCell(page)).toBeVisible();
  await expect(page.getByText("중지 = 본사가 판매 중지")).toBeVisible();
});

test("lets headquarters stop sales for a date range", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let stopped = false;
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 sales-status 경로를
  // 먼저 등록하고 inventory GET은 마지막에 등록해야 본문 route가
  // 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*/sales-status", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: STOPPED_STATUS_BODY,
      });
    }
    const payload = route.request().postDataJSON() as {
      fromDate: string;
      toDate: string;
      status: string;
    };
    stopped = payload.status === "STOPPED";
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        roomTypeId: "21000000-0000-0000-0000-000000000001",
        fromDate: payload.fromDate,
        toDate: payload.toDate,
        status: payload.status,
        stoppedDays: 1,
        created: true,
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: stopped ? STOPPED_INVENTORY_BODY : INVENTORY_BODY,
    }),
  );

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();

  await page.getByRole("button", { name: "스탠다드 시티 판매 중지" }).click();
  // 대화상자가 현재 보이는 범위(오늘 ~ 오늘 + 13일)로 날짜를 미리 채운다.
  await expect(page.getByTestId("sales-status-from")).toHaveValue(today());
  await expect(page.getByTestId("sales-status-to")).toHaveValue(todayPlus(13));
  // 재고가 남아 있어도 중지할 수 있다. 총량은 건드리지 않기 때문이다.
  await page.getByTestId("sales-status-to").fill("2026-11-01");
  await page.getByTestId("sales-status-submit").click();

  await expect(page.getByTestId("sales-status-notice")).toContainText("판매를 중지했습니다");
  await expect(page.getByTestId("sales-status-notice")).toContainText(
    "총량은 그대로 두고 신규 판매만 막았습니다",
  );
  // 중지 셀로 바뀐다.
  await expect(stoppedCell(page)).toBeVisible();
});

test("keeps the sales stop dialog open with a validation message", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types/*/sales-status", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: STOPPED_STATUS_BODY,
      });
    }
    // 93일을 넘는 구간은 서버가 400으로 거부한다.
    return route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({
        code: "INVALID_REQUEST",
        message: "판매 상태를 변경할 수 있는 기간은 최대 92일입니다.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();
  await expect(soldOutCell(page)).toBeVisible();
  await page.getByRole("button", { name: "스탠다드 시티 판매 중지" }).click();
  // 클라이언트가 날짜 순서를 먼저 검사하므로, 역순이 되지 않는
  // 유효한 시작일에서 종료일을 93일 뒤로 잡는다.
  await page.getByTestId("sales-status-from").fill(today());
  await page.getByTestId("sales-status-to").fill(todayPlus(93));
  await page.getByTestId("sales-status-submit").click();

  await expect(page.getByText("판매 상태를 변경할 수 있는 기간은 최대 92일입니다.")).toBeVisible();
  // 서버 검증 실패 시 대화상자를 닫지 않는다.
  await expect(page.getByTestId("sales-status-submit")).toBeVisible();
  // 재고는 바뀌지 않는다.
  await expect(soldOutCell(page)).toBeVisible();
});

test("lets headquarters resume sales from the stopped range", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let resumed = false;
  await page.route("**/api/staff/hotels/*/room-types/*/sales-status", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: resumed ? EMPTY_STATUS_BODY : STOPPED_STATUS_BODY,
      });
    }
    const payload = route.request().postDataJSON() as { status: string };
    resumed = payload.status === "OPEN";
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        roomTypeId: "21000000-0000-0000-0000-000000000001",
        fromDate: "2026-11-01",
        toDate: "2026-11-01",
        status: payload.status,
        stoppedDays: 0,
        created: true,
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: resumed ? INVENTORY_BODY : STOPPED_INVENTORY_BODY,
    }),
  );

  await page.goto("/dashboard/inventory");
  await page.getByRole("button", { name: "스탠다드 시티 판매 중지" }).click();

  // 중지 구간이 대화상자에 나타난다.
  await expect(
    page.getByTestId("sales-status-stopped-21000000-0000-0000-0000-000000000001"),
  ).toContainText("2026-11-01 ~ 2026-11-01");

  await page
    .getByTestId("sales-status-resume-21000000-0000-0000-0000-000000000001")
    .click();

  await expect(page.getByTestId("sales-status-notice")).toContainText("판매를 재개했습니다");
  // 재개하면 재고 응답이 중지 본문에서 재개 본문으로 바뀐다. 재개 본문에는
  // 중지 셀이 없으므로, 그리드가 새 응답을 반영할 때까지 기다린다.
  await expect(stoppedCell(page)).toHaveCount(0);
  // 매진 셀이 돌아와서 잔여가 중지 전과 같게 복원됐다.
  await expect(soldOutCell(page)).toBeVisible();
});

test("keeps the sales stop button away from branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/inventory");

  await expect(page.getByRole("button", { name: /판매 중지/ })).toHaveCount(0);
});

// 내보낸 파일과 다시 올리는 파일은 같은 열 순서를 쓴다.
const EXPORT_CSV =
  "객실 유형 ID,객실 유형,숙박일,총량,보류,확정,잔여,판매 상태,최대 인원\r\n" +
  "21000000-0000-0000-0000-000000000001,스탠다드 시티,2026-11-01,6,2,7,-3,OPEN,2\r\n" +
  "21000000-0000-0000-0000-000000000001,스탠다드 시티,2026-11-02,5,0,5,0,OPEN,2\r\n";

test("lets headquarters export the visible inventory as csv", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // GET inventory와 GET export를 같은 패턴으로 잡으므로 URL로 나눈다.
  await page.route("**/api/staff/hotels/*/inventory**", (route) => {
    if (route.request().url().includes("/inventory/export")) {
      return route.fulfill({
        status: 200,
        contentType: "text/csv",
        body: EXPORT_CSV,
      });
    }
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: INVENTORY_BODY,
    });
  });

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();

  const download = page.waitForEvent("download");
  await page.getByTestId("export-inventory-csv").click();
  const received = await download;
  const stream = await received.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));
  const text = Buffer.concat(chunks).toString("utf8");

  // 헤더와 객실 유형·총량이 들어 있다.
  expect(text).toContain("객실 유형 ID");
  expect(text).toContain("스탠다드 시티");
  expect(text).toContain("2026-11-01");
});

test("lets headquarters upload a csv and reports how many rows applied", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 import 경로를
  // 나중에 등록해야 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );
  await page.route("**/api/staff/hotels/*/inventory/import", (route) => {
    // 올려진 본문은 UTF-8 BOM으로 시작한다. 본문에서 의미 있는 행 수를
    // 서버가 그대로 돌려주는 것을 흉내 낸다.
    const body = (route.request().postData() ?? "").replace(/^\ufeff/, "");
    const lines = body.split(/\r\n|\r|\n/).filter((line) => line.trim() !== "");
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        // 헤더 1행을 뺀 나머지가 의미 있는 행이다.
        totalRows: Math.max(0, lines.length - 1),
        appliedRows: 2,
        skippedRows: 1,
        days: [
          { stayDate: "2026-11-01", capacity: 6, held: 2, confirmed: 7, remaining: 0 },
          { stayDate: "2026-11-02", capacity: 5, held: 0, confirmed: 5, remaining: 0 },
        ],
        created: true,
      }),
    });
  });

  await page.goto("/dashboard/inventory");
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();

  await page.getByTestId("import-inventory-csv").click();

  // 헤더 1행·의미 있는 행 2개인 파일을 올린다.
  await page.getByTestId("import-inventory-file").setInputFiles({
    name: "inventory.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(EXPORT_CSV, "utf8"),
  });

  // 본문을 미리 보여주고 올릴 행 수를 알려준다.
  await expect(page.getByTestId("import-inventory-preview-count")).toContainText("2행");
  await expect(page.getByTestId("import-inventory-preview")).toBeVisible();

  await page.getByTestId("import-inventory-submit").click();

  await expect(page.getByTestId("import-inventory-notice")).toContainText("2행을 올렸습니다");
  await expect(page.getByTestId("import-inventory-notice")).toContainText("2행의 재고를 바꿨습니다");
  // 서버 검증을 통과하면 대화상자를 닫는다.
  await expect(page.getByTestId("import-inventory-submit")).toHaveCount(0);
});

test("keeps the upload dialog open when the server rejects the file", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );
  // 한 행이라도 충돌하면 전체를 거부한다.
  await page.route("**/api/staff/hotels/*/inventory/import", (route) =>
    route.fulfill({
      status: 409,
      contentType: "application/json",
      // message를 주지 않아 클라이언트가 코드별 한국어 안내로 넘어가는지 확인한다.
      body: JSON.stringify({ code: "INVENTORY_CAPACITY_CONFLICT" }),
    }),
  );

  await page.goto("/dashboard/inventory");
  await page.getByTestId("import-inventory-csv").click();
  await page.getByTestId("import-inventory-file").setInputFiles({
    name: "inventory.csv",
    mimeType: "text/csv",
    buffer: Buffer.from(EXPORT_CSV, "utf8"),
  });
  await page.getByTestId("import-inventory-submit").click();

  await expect(page.getByTestId("import-inventory-form-error")).toContainText(
    "총량을 내릴 수 없는 행이 있습니다",
  );
  // 서버 검증 실패 시 대화상자를 닫지 않는다.
  await expect(page.getByTestId("import-inventory-submit")).toBeVisible();
});

test("explains when the uploaded csv has a bad format", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );
  await page.route("**/api/staff/hotels/*/inventory/import", (route) =>
    route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({ code: "INVENTORY_IMPORT_FORMAT" }),
    }),
  );

  await page.goto("/dashboard/inventory");
  await page.getByTestId("import-inventory-csv").click();
  await page.getByTestId("import-inventory-file").setInputFiles({
    name: "inventory.csv",
    mimeType: "text/csv",
    buffer: Buffer.from("객실 유형 ID,객실 유형\r\n", "utf8"),
  });
  await page.getByTestId("import-inventory-submit").click();

  await expect(page.getByTestId("import-inventory-form-error")).toContainText(
    "CSV 형식이 올바르지 않습니다",
  );
});

test("keeps the csv buttons away from branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/inventory");

  await expect(page.getByRole("button", { name: /CSV 내보내기/ })).toHaveCount(0);
  await expect(page.getByRole("button", { name: /CSV 업로드/ })).toHaveCount(0);
});
