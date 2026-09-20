import { expect, test } from "@playwright/test";

const CATALOG_BODY_ROOM_TYPES = [
  {
    roomTypeId: "23000000-0000-0000-0000-000000000001",
    name: "스탠다드",
    maxOccupancy: 2,
    ratePlans: [
      {
        ratePlanId: "33000000-0000-0000-0000-000000000001",
        name: "객실만",
        breakfastIncluded: false,
        policyVersion: "FLEX-2026-01",
        pricedDays: 1,
        minAmountKrw: 90000,
        maxAmountKrw: 90000,
        avgAmountKrw: 90000,
      },
      {
        ratePlanId: "33000000-0000-0000-0000-000000000002",
        name: "조식 포함",
        breakfastIncluded: true,
        policyVersion: "FLEX-2026-01",
        pricedDays: 2,
        minAmountKrw: 100000,
        maxAmountKrw: 150000,
        avgAmountKrw: 125000,
      },
    ],
  },
  {
    roomTypeId: "23000000-0000-0000-0000-000000000002",
    name: "스위트",
    maxOccupancy: 4,
    ratePlans: [],
  },
];

const CATALOG_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  totalCount: 2,
  roomTypes: CATALOG_BODY_ROOM_TYPES,
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

test("exposes the hotel catalog menu to headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "호텔 및 객실" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "호텔 및 객실" })).toHaveCount(0);
});

test("reports room types with rate plans and price ranges", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: CATALOG_BODY }),
  );

  await page.goto("/dashboard/hotels");

  await expect(page.getByText("객실 유형 2종")).toBeVisible();
  await expect(page.getByRole("cell", { name: "스탠다드", exact: true })).toBeVisible();
  await expect(page.locator("td").filter({ hasText: "2명" })).toBeVisible();
  await expect(page.getByText("90,000원 ~ 150,000원")).toBeVisible();
  await expect(page.getByText("등록된 요금제 없음")).toBeVisible();
  await expect(page.getByTestId("rate-plan-name").filter({ hasText: "객실만" })).toBeVisible();
  await expect(page.getByTestId("rate-plan-name").filter({ hasText: "조식 포함" })).toBeVisible();
});

test("tells a branch employee the catalog is headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("switches hotels and reloads the catalog", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: CATALOG_BODY }),
  );

  await page.goto("/dashboard/hotels");
  await expect(page.getByText("객실 유형 2종")).toBeVisible();

  await page.getByRole("button", { name: "제주 지점" }).click();

  await expect(page.getByRole("button", { name: "제주 지점" })).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByText("객실 유형 2종")).toBeVisible();
});

test("shows a recovery notice when the catalog is unreachable", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", (route) => route.fulfill({ status: 500 }));

  await page.goto("/dashboard/hotels");

  await expect(page.getByText("객실 유형 목록을 불러오지 못했습니다.")).toBeVisible();
});

test("creates a room type and refreshes the catalog", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let created = false;
  await page.route("**/api/staff/hotels/*/room-types", async (route) => {
    const method = route.request().method();
    if (method === "POST") {
      created = true;
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          roomTypeId: "23000000-0000-0000-0000-000000000003",
          hotelId: "11000000-0000-0000-0000-000000000001",
          name: "디럭스",
          maxOccupancy: 3,
          created: true,
          seed: {
            ratePlanId: "33000000-0000-0000-0000-000000000003",
            ratePlanName: "기본 요금제",
            pricedDays: 90,
            defaultRateKrw: 100000,
            inventoryCapacity: 8,
            created: true,
          },
        }),
      });
      return;
    }
    const body = created
      ? JSON.stringify({
          hotelId: "11000000-0000-0000-0000-000000000001",
          totalCount: 3,
          roomTypes: [
            ...CATALOG_BODY_ROOM_TYPES,
            {
              roomTypeId: "23000000-0000-0000-0000-000000000003",
              name: "디럭스",
              maxOccupancy: 3,
              ratePlans: [],
            },
          ],
        })
      : CATALOG_BODY;
    await route.fulfill({ status: 200, contentType: "application/json", body });
  });

  await page.goto("/dashboard/hotels");
  await expect(page.getByText("객실 유형 2종")).toBeVisible();

  await page.getByTestId("create-room-type").click();
  await page.getByTestId("create-room-type-name").fill("디럭스");
  await page.getByTestId("create-room-type-occupancy").fill("3");
  await page.getByTestId("create-room-type-submit").click();

  await expect(page.getByText("객실 유형 3종")).toBeVisible();
  await expect(page.getByRole("cell", { name: "디럭스", exact: true })).toBeVisible();
});

test("tells headquarters the seeded price and inventory", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          roomTypeId: "23000000-0000-0000-0000-000000000003",
          hotelId: "11000000-0000-0000-0000-000000000001",
          name: "디럭스",
          maxOccupancy: 3,
          created: true,
          seed: {
            ratePlanId: "33000000-0000-0000-0000-000000000003",
            ratePlanName: "기본 요금제",
            pricedDays: 90,
            defaultRateKrw: 100000,
            inventoryCapacity: 8,
            created: true,
          },
        }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: CATALOG_BODY });
  });

  await page.goto("/dashboard/hotels");
  await page.getByTestId("create-room-type").click();
  await page.getByTestId("create-room-type-name").fill("디럭스");
  await page.getByTestId("create-room-type-occupancy").fill("3");
  await page.getByTestId("create-room-type-submit").click();

  // 시드 안내가 없으면 본사는 객실 유형이 바로 예약 가능한지 알 수 없다.
  await expect(page.getByTestId("create-room-type-notice")).toContainText("90일분");
  await expect(page.getByTestId("create-room-type-notice")).toContainText("100,000원");
  await expect(page.getByTestId("create-room-type-notice")).toContainText("8실");
});

test("keeps the dialog open with a server validation message", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({ code: "INVALID_REQUEST", message: "객실 유형 이름은 1자 이상 100자 이하여야 합니다." }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: CATALOG_BODY });
  });

  await page.goto("/dashboard/hotels");
  await page.getByTestId("create-room-type").click();
  await page.getByTestId("create-room-type-name").fill("디럭스");
  await page.getByTestId("create-room-type-occupancy").fill("3");
  await page.getByTestId("create-room-type-submit").click();

  await expect(page.getByText("객실 유형 이름은 1자 이상 100자 이하여야 합니다.")).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("create-room-type-submit")).toBeVisible();
});

test("hides the create button from a branch employee", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByTestId("create-room-type")).toHaveCount(0);
});

test("updates a room type and reflects it in the catalog", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let updated = false;
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 PATCH 경로를 먼저 등록하고
  // 목록 GET은 마지막에 등록해야 본문 route가 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*", async (route) => {
    if (route.request().method() !== "PATCH") {
      return route.fallback();
    }
    updated = true;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        roomTypeId: "23000000-0000-0000-0000-000000000001",
        hotelId: "11000000-0000-0000-0000-000000000001",
        name: "스탠다드 디럭스",
        maxOccupancy: 4,
        created: true,
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", async (route) => {
    const body = updated
      ? JSON.stringify({
          hotelId: "11000000-0000-0000-0000-000000000001",
          totalCount: 2,
          roomTypes: [
            {
              roomTypeId: "23000000-0000-0000-0000-000000000001",
              name: "스탠다드 디럭스",
              maxOccupancy: 4,
              ratePlans: CATALOG_BODY_ROOM_TYPES[0].ratePlans,
            },
            CATALOG_BODY_ROOM_TYPES[1],
          ],
        })
      : CATALOG_BODY;
    await route.fulfill({ status: 200, contentType: "application/json", body });
  });

  await page.goto("/dashboard/hotels");
  await expect(page.getByRole("cell", { name: "스탠다드", exact: true })).toBeVisible();

  await page.getByTestId("edit-room-type-23000000-0000-0000-0000-000000000001").click();
  await page.getByTestId("edit-room-type-name").fill("스탠다드 디럭스");
  await page.getByTestId("edit-room-type-occupancy").fill("4");
  await page.getByTestId("edit-room-type-submit").click();

  await expect(page.getByRole("cell", { name: "스탠다드 디럭스", exact: true })).toBeVisible();
  // 스위트도 4명이므로 행을 특정해서 확인한다.
  await expect(
    page.locator("[data-room-type-id='23000000-0000-0000-0000-000000000001']").getByRole("cell", { name: "4명" }),
  ).toBeVisible();
});

test("keeps the edit dialog open with an occupancy conflict message", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 PATCH 경로를 먼저 등록하고
  // 목록 GET은 마지막에 등록해야 본문 route가 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*", (route) => {
    if (route.request().method() !== "PATCH") {
      return route.fallback();
    }
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "ROOM_TYPE_OCCUPANCY_CONFLICT",
        message: "최대 인원을 내릴 수 없습니다. 진행 중인 예약 1건이 새 인원을 초과합니다.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: CATALOG_BODY }),
  );

  await page.goto("/dashboard/hotels");
  await page.getByTestId("edit-room-type-23000000-0000-0000-0000-000000000001").click();
  await page.getByTestId("edit-room-type-occupancy").fill("1");
  await page.getByTestId("edit-room-type-submit").click();

  await expect(page.getByText("최대 인원을 내릴 수 없습니다. 진행 중인 예약 1건이 새 인원을 초과합니다.")).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("edit-room-type-submit")).toBeVisible();
});

test("hides the edit buttons from a branch employee", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다.")).toBeVisible();
  await expect(page.getByTestId("edit-room-type-23000000-0000-0000-0000-000000000001")).toHaveCount(0);
});
