import { expect, test } from "@playwright/test";

const CATALOG_BODY_ROOM_TYPES = [
  {
    roomTypeId: "23000000-0000-0000-0000-000000000001",
    name: "스탠다드",
    maxOccupancy: 2,
    breakfastIncluded: false,
    defaultRateKrw: 90000,
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
    breakfastIncluded: true,
    defaultRateKrw: 200000,
    ratePlans: [],
  },
];

const CATALOG_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  totalCount: 2,
  roomTypes: CATALOG_BODY_ROOM_TYPES,
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
    id: "11000000-0000-0000-0000-000000000002",
    name: "제주 지점",
    region: "제주도",
    timezone: "Asia/Seoul",
    active: true,
  },
];

const HOTELS_BODY = JSON.stringify(HOTELS);

function seedStaffScript(role: "HQ_ADMIN" | "BRANCH_STAFF") {
  const staff =
    role === "HQ_ADMIN"
      ? {
          id: "test",
          email: "hq@example.com",
          displayName: "본사 관리자",
          role,
          hotelId: null,
        }
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
  // 지점 선택기가 서버에서 목록을 읽으므로 기본 목록을 제공한다.
  await page.route("**/api/staff/hotels", (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: HOTELS_BODY,
    });
  });
});

test("exposes the hotel catalog menu to headquarters only", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "호텔 및 객실" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "호텔 및 객실" })).toHaveCount(0);
});

test("reports room types with rate plans and price ranges", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");

  await expect(page.getByText("객실 유형 2종")).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "스탠다드", exact: true }),
  ).toBeVisible();
  await expect(page.locator("td").filter({ hasText: "2명" })).toBeVisible();
  await expect(page.getByText("90,000원 ~ 150,000원")).toBeVisible();
  await expect(page.getByText("등록된 요금제 없음")).toBeVisible();
  await expect(
    page.getByTestId("rate-plan-name").filter({ hasText: "객실만" }),
  ).toBeVisible();
  await expect(
    page.getByTestId("rate-plan-name").filter({ hasText: "조식 포함" }),
  ).toBeVisible();
});

test("tells a branch employee the catalog is headquarters only", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(
    page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("switches hotels and reloads the catalog", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", (route) => {
    const hotelId = new URL(route.request().url()).pathname.split("/").at(-2);
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ...JSON.parse(CATALOG_BODY),
        hotelId,
      }),
    });
  });

  await page.goto("/dashboard/hotels");
  const sokchoSelector = page.getByTestId(
    "hotel-selector-11000000-0000-0000-0000-000000000001",
  );
  const jejuSelector = page.getByTestId(
    "hotel-selector-11000000-0000-0000-0000-000000000002",
  );
  const sokchoRow = sokchoSelector.locator("xpath=ancestor::tr");
  const jejuRow = jejuSelector.locator("xpath=ancestor::tr");

  await expect(sokchoSelector).toHaveAttribute("aria-pressed", "true");
  await expect(jejuSelector).toHaveAttribute("aria-pressed", "false");
  await expect(sokchoRow).toHaveAttribute("data-state", "selected");
  await expect(sokchoRow.getByText("선택됨", { exact: true })).toBeVisible();
  await expect(
    page.getByText("속초 지점 · 객실 유형 2종", { exact: true }),
  ).toBeVisible();

  // 키보드로도 두 번째 지점을 선택하면 카탈로그를 다시 부른다.
  await jejuSelector.focus();
  await page.keyboard.press("Enter");

  await expect(jejuSelector).toHaveAttribute("aria-pressed", "true");
  await expect(sokchoSelector).toHaveAttribute("aria-pressed", "false");
  await expect(jejuRow).toHaveAttribute("data-state", "selected");
  await expect(sokchoRow).not.toHaveAttribute("data-state", "selected");
  await expect(jejuRow.getByText("선택됨", { exact: true })).toBeVisible();
  await expect(
    page.getByText("제주 지점 · 객실 유형 2종", { exact: true }),
  ).toBeVisible();
});

test("hides the previous hotel catalog while the next branch loads", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let releaseJeju: (() => void) | undefined;
  const jejuPending = new Promise<void>((resolve) => {
    releaseJeju = resolve;
  });
  await page.route("**/api/staff/hotels/*/room-types", async (route) => {
    const hotelId = new URL(route.request().url()).pathname.split("/").at(-2);
    if (hotelId === "11000000-0000-0000-0000-000000000002") {
      await jejuPending;
    }
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({ ...JSON.parse(CATALOG_BODY), hotelId }),
    });
  });

  await page.goto("/dashboard/hotels");
  await expect(
    page.getByText("속초 지점 · 객실 유형 2종", { exact: true }),
  ).toBeVisible();

  await page
    .getByTestId("hotel-selector-11000000-0000-0000-0000-000000000002")
    .click();

  await expect(
    page.getByText("속초 지점 · 객실 유형 2종", { exact: true }),
  ).toHaveCount(0);
  await expect(page.getByTestId("room-type-list-loading")).toBeVisible();

  releaseJeju?.();
  await expect(
    page.getByText("제주 지점 · 객실 유형 2종", { exact: true }),
  ).toBeVisible();
});

test("ignores late room defaults from the previously selected branch", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const firstRoomTypeId = "23000000-0000-0000-0000-000000000001";
  const secondRoomTypeId = "23000000-0000-0000-0000-000000000099";
  let releaseFirstDefaults: (() => void) | undefined;
  const firstDefaultsPending = new Promise<void>((resolve) => {
    releaseFirstDefaults = resolve;
  });

  await page.route("**/api/staff/hotels/*/room-types/*/defaults", async (route) => {
    const url = new URL(route.request().url());
    const roomTypeId = url.pathname.split("/").at(-2);
    if (roomTypeId === firstRoomTypeId) await firstDefaultsPending;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ratePlanId: "33000000-0000-0000-0000-000000000099",
        ratePlanName: "current plan",
        breakfastIncluded: roomTypeId === secondRoomTypeId,
        defaultRateKrw: roomTypeId === secondRoomTypeId ? 222000 : 111000,
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) => {
    const hotelId = new URL(route.request().url()).pathname.split("/").at(-2);
    const isSecond = hotelId === "11000000-0000-0000-0000-000000000002";
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId,
        totalCount: 1,
        roomTypes: [
          {
            roomTypeId: isSecond ? secondRoomTypeId : firstRoomTypeId,
            name: isSecond ? "Jeju room" : "Sokcho room",
            maxOccupancy: 2,
            breakfastIncluded: false,
            defaultRateKrw: isSecond ? 200000 : 90000,
            ratePlans: [],
          },
        ],
      }),
    });
  });

  await page.goto("/dashboard/hotels");
  await page.getByTestId(`edit-room-type-${firstRoomTypeId}`).click();
  await page.keyboard.press("Escape");
  await page
    .getByTestId("hotel-selector-11000000-0000-0000-0000-000000000002")
    .click();
  await page.getByTestId(`edit-room-type-${secondRoomTypeId}`).click();

  await expect(page.getByTestId("edit-room-type-rate")).toHaveValue("222000");
  await expect(page.getByTestId("edit-room-type-breakfast")).toBeChecked();

  releaseFirstDefaults?.();
  await expect(page.getByTestId("edit-room-type-rate")).toHaveValue("222000");
  await expect(page.getByTestId("edit-room-type-breakfast")).toBeChecked();
});

test("shows a recovery notice when the catalog is unreachable", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({ status: 500 }),
  );

  await page.goto("/dashboard/hotels");

  await expect(
    page.getByText("객실 유형 목록을 불러오지 못했습니다."),
  ).toBeVisible();
});

test("tells headquarters an empty hotel list has no catalog", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "[]" }),
  );

  await page.goto("/dashboard/hotels");

  // 지점이 없으면 객실 유형을 불러오지 않는다. 본사가 지점을 먼저 만들어야 한다.
  await expect(page.getByText("객실 유형 2종")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "객실 유형 추가" })).toBeVisible();
});

test("creates a hotel and adds it to the selector", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let created = false;
  await page.route("**/api/staff/hotels", async (route) => {
    if (route.request().method() === "POST") {
      created = true;
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          hotelId: "11000000-0000-0000-0000-000000000003",
          name: "춘천 지점",
          region: "강원",
          timezone: "Asia/Seoul",
          roomTypes: 0,
          created: true,
        }),
      });
      return;
    }
    if (route.request().method() !== "GET") return route.fallback();
    // 생성 뒤에 다시 읽으면 새 지점이 포함돼야 선택기에 나타난다.
    const body = created
      ? JSON.stringify([
          ...HOTELS,
          {
            id: "11000000-0000-0000-0000-000000000003",
            name: "춘천 지점",
            region: "강원",
            timezone: "Asia/Seoul",
          },
        ])
      : HOTELS_BODY;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body,
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await expect(page.getByTestId("hotel-selector-11000000-0000-0000-0000-000000000001")).toBeVisible();

  await page.getByTestId("create-hotel").click();
  await page.getByTestId("create-hotel-name").fill("춘천 지점");
  await page.getByTestId("create-hotel-region").fill("강원");
  await page.getByTestId("create-hotel-timezone").fill("Asia/Seoul");
  await page.getByTestId("create-hotel-submit").click();

  // 새 지점이 선택기에 나타난다.
  await expect(page.getByTestId("hotel-selector-11000000-0000-0000-0000-000000000003")).toBeVisible();
  // 빈 지점은 객실 유형을 추가해야 고객 검색에 나타난다.
  await expect(page.getByTestId("create-room-type-notice")).toContainText(
    "객실 유형이 없으므로",
  );
  expect(created).toBe(true);
});

test("keeps the hotel dialog open with a name conflict message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels", async (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "HOTEL_NAME_CONFLICT",
        message: "이미 같은 이름의 지점이 있습니다: 속초 지점",
      }),
    });
  });

  await page.goto("/dashboard/hotels");
  await page.getByTestId("create-hotel").click();
  await page.getByTestId("create-hotel-name").fill("속초 지점");
  await page.getByTestId("create-hotel-region").fill("속초");
  await page.getByTestId("create-hotel-timezone").fill("Asia/Seoul");
  await page.getByTestId("create-hotel-submit").click();

  await expect(
    page.getByText("이미 같은 이름의 지점이 있습니다: 속초 지점"),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("create-hotel-submit")).toBeVisible();
});

test("hides the create hotel button from a branch employee", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(
    page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(page.getByTestId("create-hotel")).toHaveCount(0);
});

test("updates a hotel and reflects it in the selector", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let patched = false;
  await page.route("**/api/staff/hotels/*", async (route) => {
    if (route.request().method() !== "PATCH") return route.fallback();
    patched = true;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        name: "속초 오션",
        region: "강원",
        timezone: "Asia/Seoul",
        roomTypes: 2,
        changed: true,
      }),
    });
  });
  await page.route("**/api/staff/hotels", async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    // 수정 뒤에 다시 읽으면 바뀐 이름이 포함돼야 선택기에 나타난다.
    const body = patched
      ? JSON.stringify([
          {
            id: "11000000-0000-0000-0000-000000000001",
            name: "속초 오션",
            region: "강원",
            timezone: "Asia/Seoul",
          },
          ...HOTELS.slice(1),
        ])
      : HOTELS_BODY;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body,
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await expect(
    page.getByTestId("hotel-selector-11000000-0000-0000-0000-000000000001"),
  ).toBeVisible();

  // 대화상자는 현재 이름·지역·시간대로 미리 채운다.
  await page.getByTestId("edit-hotel-11000000-0000-0000-0000-000000000001").click();
  await expect(page.getByTestId("edit-hotel-name")).toHaveValue("속초 지점");
  await expect(page.getByTestId("edit-hotel-region")).toHaveValue("속초");

  await page.getByTestId("edit-hotel-name").fill("속초 오션");
  await page.getByTestId("edit-hotel-region").fill("강원");
  await page.getByTestId("edit-hotel-submit").click();

  await expect(
    page.getByTestId("hotel-selector-11000000-0000-0000-0000-000000000001"),
  ).toHaveText("속초 오션");
  expect(patched).toBe(true);
});

test("keeps the edit hotel dialog open with a name conflict message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*", async (route) => {
    if (route.request().method() !== "PATCH") return route.fallback();
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "HOTEL_NAME_CONFLICT",
        message: "이미 같은 이름의 지점이 있습니다: 제주 지점",
      }),
    });
  });

  await page.goto("/dashboard/hotels");
  await page.getByTestId("edit-hotel-11000000-0000-0000-0000-000000000001").click();
  await page.getByTestId("edit-hotel-name").fill("제주 지점");
  await page.getByTestId("edit-hotel-submit").click();

  await expect(
    page.getByText("이미 같은 이름의 지점이 있습니다: 제주 지점"),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("edit-hotel-submit")).toBeVisible();
});

test("hides the edit hotel buttons from a branch employee", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(
    page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(
    page.getByTestId("edit-hotel-11000000-0000-0000-0000-000000000001"),
  ).toHaveCount(0);
});

test("stops sales and marks the hotel stopped", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let toggled = false;
  await page.route("**/api/staff/hotels/*/active", async (route) => {
    if (route.request().method() !== "PATCH") return route.fallback();
    toggled = true;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        name: "속초 지점",
        region: "속초",
        timezone: "Asia/Seoul",
        active: false,
        changed: true,
      }),
    });
  });
  await page.route("**/api/staff/hotels", async (route) => {
    if (route.request().method() !== "GET") return route.fallback();
    // 중지 뒤에 다시 읽으면 active=false가 내려와야 표시가 바뀐다.
    const body = toggled
      ? JSON.stringify([
          {
            id: "11000000-0000-0000-0000-000000000001",
            name: "속초 지점",
            region: "속초",
            timezone: "Asia/Seoul",
            active: false,
          },
          ...HOTELS.slice(1),
        ])
      : HOTELS_BODY;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body,
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await expect(
    page.getByTestId("hotel-active-badge-11000000-0000-0000-0000-000000000001"),
  ).toHaveText("판매 중");

  await page
    .getByTestId("toggle-hotel-active-11000000-0000-0000-0000-000000000001")
    .click();

  await expect(
    page.getByTestId("hotel-active-badge-11000000-0000-0000-0000-000000000001"),
  ).toHaveText("판매 중지");
  // 버튼이 재개로 바뀐다.
  await expect(
    page.getByTestId("toggle-hotel-active-11000000-0000-0000-0000-000000000001"),
  ).toHaveText("판매 재개");
  expect(toggled).toBe(true);
});

test("explains when stopping sales is rejected", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/active", (route) =>
    route.fulfill({
      status: 403,
      contentType: "application/json",
      body: JSON.stringify({
        code: "STAFF_HOTEL_ACCESS_DENIED",
        message: "판매 상태를 바꿀 권한이 없습니다.",
      }),
    }),
  );

  await page.goto("/dashboard/hotels");
  await page
    .getByTestId("toggle-hotel-active-11000000-0000-0000-0000-000000000001")
    .click();

  // 안내가 표에 남아 있고 상태는 바뀌지 않는다.
  await expect(
    page.getByText("판매 상태를 바꿀 권한이 없습니다."),
  ).toBeVisible();
  await expect(
    page.getByTestId("hotel-active-badge-11000000-0000-0000-0000-000000000001"),
  ).toHaveText("판매 중");
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
            breakfastIncluded: true,
            defaultRateKrw: 120000,
            pricedDays: 90,
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
              breakfastIncluded: true,
              defaultRateKrw: 120000,
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
  await page.getByTestId("create-room-type-breakfast").check();
  await page.getByTestId("create-room-type-rate").fill("120000");
  await page.getByTestId("create-room-type-submit").click();

  await expect(page.getByText("객실 유형 3종")).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "디럭스", exact: true }),
  ).toBeVisible();
});

test("locks branch selection while a room type write is pending", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let releaseCreate: (() => void) | undefined;
  const createPending = new Promise<void>((resolve) => {
    releaseCreate = resolve;
  });
  await page.route("**/api/staff/hotels/*/room-types", async (route) => {
    if (route.request().method() === "POST") {
      await createPending;
      return route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          roomTypeId: "23000000-0000-0000-0000-000000000003",
          hotelId: "11000000-0000-0000-0000-000000000001",
          name: "디럭스",
          maxOccupancy: 3,
          created: true,
          seed: null,
        }),
      });
    }
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    });
  });

  await page.goto("/dashboard/hotels");
  await expect(page.getByText("객실 유형 2종")).toBeVisible();
  await page.getByTestId("create-room-type").click();
  await page.getByTestId("create-room-type-name").fill("디럭스");
  await page.getByTestId("create-room-type-occupancy").fill("3");
  await page.getByTestId("create-room-type-submit").click();
  await page.keyboard.press("Escape");

  const jejuSelector = page.getByTestId(
    "hotel-selector-11000000-0000-0000-0000-000000000002",
  );
  await expect(jejuSelector).toBeDisabled();

  releaseCreate?.();
  await expect(jejuSelector).toBeEnabled();
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
            breakfastIncluded: true,
            defaultRateKrw: 120000,
            pricedDays: 90,
            inventoryCapacity: 8,
            created: true,
          },
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    });
  });

  await page.goto("/dashboard/hotels");
  await page.getByTestId("create-room-type").click();
  await page.getByTestId("create-room-type-name").fill("디럭스");
  await page.getByTestId("create-room-type-occupancy").fill("3");
  await page.getByTestId("create-room-type-breakfast").check();
  await page.getByTestId("create-room-type-rate").fill("120000");
  await page.getByTestId("create-room-type-submit").click();

  // 시드 안내가 없으면 본사는 객실 유형이 바로 예약 가능한지 알 수 없다.
  await expect(page.getByTestId("create-room-type-notice")).toContainText(
    "90일분",
  );
  await expect(page.getByTestId("create-room-type-notice")).toContainText(
    "120,000원",
  );
  await expect(page.getByTestId("create-room-type-notice")).toContainText(
    "조식 포함",
  );
  await expect(page.getByTestId("create-room-type-notice")).toContainText(
    "8실",
  );
});

test("keeps the dialog open with a server validation message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({
          code: "INVALID_REQUEST",
          message: "객실 유형 이름은 1자 이상 100자 이하여야 합니다.",
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    });
  });

  await page.goto("/dashboard/hotels");
  await page.getByTestId("create-room-type").click();
  await page.getByTestId("create-room-type-name").fill("디럭스");
  await page.getByTestId("create-room-type-occupancy").fill("3");
  await page.getByTestId("create-room-type-breakfast").check();
  await page.getByTestId("create-room-type-rate").fill("120000");
  await page.getByTestId("create-room-type-submit").click();

  await expect(
    page.getByText("객실 유형 이름은 1자 이상 100자 이하여야 합니다."),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("create-room-type-submit")).toBeVisible();
});

test("hides the create button from a branch employee", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(
    page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(page.getByTestId("create-room-type")).toHaveCount(0);
});

test("updates a room type and reflects it in the catalog", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let updated = false;
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 PATCH 경로를 먼저 등록하고
  // 목록 GET은 마지막에 등록해야 본문 route가 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*", async (route) => {
    const url = route.request().url();
    if (url.endsWith("/defaults")) {
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          ratePlanId: "33000000-0000-0000-0000-000000000001",
          ratePlanName: "객실만",
          breakfastIncluded: false,
          defaultRateKrw: 90000,
        }),
      });
      return;
    }
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
        ratePlan: {
          ratePlanId: "33000000-0000-0000-0000-000000000001",
          ratePlanName: "객실만",
          breakfastIncluded: true,
          defaultRateKrw: 130000,
          pricedDays: 1,
        },
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
              breakfastIncluded: true,
              defaultRateKrw: 130000,
              ratePlans: CATALOG_BODY_ROOM_TYPES[0].ratePlans,
            },
            CATALOG_BODY_ROOM_TYPES[1],
          ],
        })
      : CATALOG_BODY;
    await route.fulfill({ status: 200, contentType: "application/json", body });
  });

  await page.goto("/dashboard/hotels");
  await expect(
    page.getByRole("cell", { name: "스탠다드", exact: true }),
  ).toBeVisible();

  await page
    .getByTestId("edit-room-type-23000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("edit-room-type-name").fill("스탠다드 디럭스");
  await page.getByTestId("edit-room-type-occupancy").fill("4");
  await page.getByTestId("edit-room-type-breakfast").check();
  await page.getByTestId("edit-room-type-rate").fill("130000");
  await page.getByTestId("edit-room-type-submit").click();

  await expect(
    page.getByRole("cell", { name: "스탠다드 디럭스", exact: true }),
  ).toBeVisible();
  // 스위트도 4명이므로 행을 특정해서 확인한다.
  await expect(
    page
      .locator("[data-room-type-id='23000000-0000-0000-0000-000000000001']")
      .getByRole("cell", { name: "4명" }),
  ).toBeVisible();
});

test("keeps the edit dialog open with an occupancy conflict message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 PATCH 경로를 먼저 등록하고
  // 목록 GET은 마지막에 등록해야 본문 route가 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*", (route) => {
    const url = route.request().url();
    if (url.endsWith("/defaults")) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          ratePlanId: "33000000-0000-0000-0000-000000000001",
          ratePlanName: "객실만",
          breakfastIncluded: false,
          defaultRateKrw: 90000,
        }),
      });
    }
    if (route.request().method() !== "PATCH") {
      return route.fallback();
    }
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "ROOM_TYPE_OCCUPANCY_CONFLICT",
        message:
          "최대 인원을 내릴 수 없습니다. 진행 중인 예약 1건이 새 인원을 초과합니다.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await page
    .getByTestId("edit-room-type-23000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("edit-room-type-occupancy").fill("1");
  await page.getByTestId("edit-room-type-rate").fill("90000");
  await page.getByTestId("edit-room-type-submit").click();

  await expect(
    page.getByText(
      "최대 인원을 내릴 수 없습니다. 진행 중인 예약 1건이 새 인원을 초과합니다.",
    ),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("edit-room-type-submit")).toBeVisible();
});

test("keeps the edit dialog open with a breakfast conflict message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types/*", (route) => {
    const url = route.request().url();
    if (url.endsWith("/defaults")) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          ratePlanId: "33000000-0000-0000-0000-000000000001",
          ratePlanName: "객실만",
          breakfastIncluded: false,
          defaultRateKrw: 90000,
        }),
      });
    }
    if (route.request().method() !== "PATCH") {
      return route.fallback();
    }
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "ROOM_TYPE_BREAKFAST_CONFLICT",
        message:
          "조식 포함 여부를 바꿀 수 없습니다. 진행 중인 예약 1건이 현재 조식 조건으로 예약됐습니다.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await page
    .getByTestId("edit-room-type-23000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("edit-room-type-breakfast").check();
  await page.getByTestId("edit-room-type-rate").fill("90000");
  await page.getByTestId("edit-room-type-submit").click();

  await expect(
    page.getByText(
      "조식 포함 여부를 바꿀 수 없습니다. 진행 중인 예약 1건이 현재 조식 조건으로 예약됐습니다.",
    ),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("edit-room-type-submit")).toBeVisible();
});

test("hides the edit buttons from a branch employee", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/hotels");

  await expect(
    page.getByText("호텔 및 객실 관리는 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(
    page.getByTestId("edit-room-type-23000000-0000-0000-0000-000000000001"),
  ).toHaveCount(0);
});

test("deletes a room type and updates the catalog", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // 삭제한 유형이 카탈로그에서 사라지는지 확인하려면 응답이 바뀌어야 한다.
  let deleted = false;
  await page.route("**/api/staff/hotels/*/room-types/*", (route) => {
    if (route.request().method() !== "DELETE") return route.fallback();
    deleted = true;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        hotelId: "11000000-0000-0000-0000-000000000001",
        roomTypeId: "23000000-0000-0000-0000-000000000002",
        name: "스위트",
        deleted: true,
        remainingRoomTypes: 1,
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: deleted
        ? JSON.stringify({
            hotelId: "11000000-0000-0000-0000-000000000001",
            totalCount: 1,
            roomTypes: [CATALOG_BODY_ROOM_TYPES[0]],
          })
        : CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await expect(page.getByText("스위트")).toBeVisible();

  await page
    .getByTestId("delete-room-type-23000000-0000-0000-0000-000000000002")
    .click();

  // 확인 대화상자가 한 번 더 묻는다.
  await expect(
    page.getByText("스위트을(를) 지운다", { exact: false }),
  ).toBeVisible();
  await page.getByTestId("delete-room-type-submit").click();

  await expect(deleted).toBe(true);
  // 삭제한 유형이 표에서 사라진다. 대화상자 본문과 겹치지 않게 셀로 잡는다.
  await expect(
    page.getByRole("cell", { name: "스위트", exact: true }),
  ).toHaveCount(0);
  await expect(page.getByText("객실 유형 1종")).toBeVisible();
  await expect(
    page.getByText(
      "스위트을 지웠습니다. 남은 객실 유형은 1종입니다.",
    ),
  ).toBeVisible();
});

test("keeps the delete dialog open with a conflict message", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types/*", (route) => {
    if (route.request().method() !== "DELETE") return route.fallback();
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "ROOM_TYPE_DELETION_CONFLICT",
        message:
          "객실 유형을 지울 수 없습니다. 진행 중인 예약 2건이 있어야 지울 수 있습니다.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await page
    .getByTestId("delete-room-type-23000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("delete-room-type-submit").click();

  await expect(
    page.getByText(
      "객실 유형을 지울 수 없습니다. 진행 중인 예약 2건이 있어야 지울 수 있습니다.",
    ),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 다음에 할 일을 정할 수 있다.
  await expect(page.getByTestId("delete-room-type-submit")).toBeVisible();
  // 유형은 지워지지 않고 표에 그대로 있다.
  await expect(
    page.getByTestId("delete-room-type-23000000-0000-0000-0000-000000000001"),
  ).toBeVisible();
});

test("adds a second rate plan and lists it under the room type", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let created = false;
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 POST 경로를 먼저 등록하고
  // 목록 GET은 마지막에 등록해야 생성 route가 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*/rate-plans", (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    created = true;
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        ratePlanId: "33000000-0000-0000-0000-000000000003",
        hotelId: "11000000-0000-0000-0000-000000000001",
        roomTypeId: "23000000-0000-0000-0000-000000000001",
        name: "취소 불가",
        breakfastIncluded: false,
        policyVersion: "NONREFUNDABLE-2026-01",
        defaultRateKrw: 76000,
        fromDate: "2026-09-24",
        seededDays: 90,
        created: true,
        changed: true,
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) => {
    const body = created
      ? JSON.stringify({
          hotelId: "11000000-0000-0000-0000-000000000001",
          totalCount: 2,
          roomTypes: [
            {
              ...CATALOG_BODY_ROOM_TYPES[0],
              ratePlans: [
                ...CATALOG_BODY_ROOM_TYPES[0].ratePlans,
                {
                  ratePlanId: "33000000-0000-0000-0000-000000000003",
                  name: "취소 불가",
                  breakfastIncluded: false,
                  policyVersion: "NONREFUNDABLE-2026-01",
                  pricedDays: 1,
                  minAmountKrw: 76000,
                  maxAmountKrw: 76000,
                  avgAmountKrw: 76000,
                },
              ],
            },
            CATALOG_BODY_ROOM_TYPES[1],
          ],
        })
      : CATALOG_BODY;
    return route.fulfill({ status: 200, contentType: "application/json", body });
  });

  await page.goto("/dashboard/hotels");
  await expect(
    page.getByTestId("rate-plan-list-23000000-0000-0000-0000-000000000001"),
  ).toBeVisible();

  await page
    .getByTestId("add-rate-plan-23000000-0000-0000-0000-000000000001")
    .click();
  // 기본 요금제의 조건을 미리 채운 상태에서 이름만 바꾸면 추가된다.
  await page.getByTestId("create-rate-plan-name").fill("취소 불가");
  await page.getByTestId("create-rate-plan-policy").fill("NONREFUNDABLE-2026-01");
  await page.getByTestId("create-rate-plan-rate").fill("76000");
  await page.getByTestId("create-rate-plan-submit").click();

  // 시드 안내가 없으면 본사는 요금제가 고객 검색에 나타나는지 알 수 없다.
  await expect(page.getByTestId("create-room-type-notice")).toContainText(
    "취소 불가",
  );
  await expect(page.getByTestId("create-room-type-notice")).toContainText(
    "90일분",
  );
  // 같은 유형 아래에 세 요금제가 나란히 나타난다.
  await expect(
    page
      .getByTestId("rate-plan-list-23000000-0000-0000-0000-000000000001")
      .getByTestId("rename-rate-plan-33000000-0000-0000-0000-000000000003"),
  ).toBeVisible();
  await expect(
    page
      .getByTestId("rate-plan-list-23000000-0000-0000-0000-000000000001")
      .getByText("NONREFUNDABLE-2026-01"),
  ).toBeVisible();
});

test("keeps the rate plan dialog open with a name conflict message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 POST 경로를 먼저 등록하고
  // 목록 GET은 마지막에 등록해야 생성 route가 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*/rate-plans", (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "RATE_PLAN_NAME_CONFLICT",
        message:
          "같은 객실 유형에 이미 같은 이름의 요금제가 있습니다. 다른 이름을 사용해 주세요.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await page
    .getByTestId("add-rate-plan-23000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("create-rate-plan-name").fill("객실만");
  await page.getByTestId("create-rate-plan-submit").click();

  await expect(
    page.getByText(
      "같은 객실 유형에 이미 같은 이름의 요금제가 있습니다. 다른 이름을 사용해 주세요.",
    ),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("create-rate-plan-submit")).toBeVisible();
  // 요금제는 추가되지 않고 기존 2개만 남는다.
  await expect(
    page.getByTestId("rate-plan-list-23000000-0000-0000-0000-000000000001"),
  ).toContainText("객실만");
});

test("keeps the rate plan dialog open with an inventory day message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // 재고가 없는 날짜를 포함하면 409로 거부한다.
  await page.route("**/api/staff/hotels/*/room-types/*/rate-plans", (route) => {
    if (route.request().method() !== "POST") return route.fallback();
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "RATE_PLAN_INVENTORY_DAY_NOT_FOUND",
        message:
          "2026-12-25 일자에는 재고가 없어 요금을 심을 수 없습니다. 재고가 있는 날짜만 요금제에 포함할 수 있습니다.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await page
    .getByTestId("add-rate-plan-23000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("create-rate-plan-name").fill("연말 특가");
  await page.getByTestId("create-rate-plan-submit").click();

  await expect(
    page.getByText(
      "2026-12-25 일자에는 재고가 없어 요금을 심을 수 없습니다. 재고가 있는 날짜만 요금제에 포함할 수 있습니다.",
    ),
  ).toBeVisible();
  await expect(page.getByTestId("create-rate-plan-submit")).toBeVisible();
});

test("renames a rate plan and reflects it in the list", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let renamed = false;
  // Playwright는 나중에 등록한 route부터 평가한다. 좁은 PATCH 경로를 먼저 등록하고
  // 목록 GET은 마지막에 등록해야 변경 route가 목록 요청을 덮어쓰지 않는다.
  await page.route("**/api/staff/hotels/*/room-types/*/rate-plans/*", (route) => {
    if (route.request().method() !== "PATCH") return route.fallback();
    renamed = true;
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ratePlanId: "33000000-0000-0000-0000-000000000001",
        hotelId: "11000000-0000-0000-0000-000000000001",
        roomTypeId: "23000000-0000-0000-0000-000000000001",
        name: "객실만 (할인)",
        breakfastIncluded: false,
        policyVersion: "FLEX-2026-01",
        defaultRateKrw: 90000,
        fromDate: null,
        seededDays: 0,
        created: false,
        changed: true,
      }),
    });
  });
  const renamedCatalog = JSON.stringify({
    hotelId: "11000000-0000-0000-0000-000000000001",
    totalCount: 2,
    roomTypes: [
      {
        ...CATALOG_BODY_ROOM_TYPES[0],
        ratePlans: [
          {
            ...CATALOG_BODY_ROOM_TYPES[0].ratePlans[0],
            name: "객실만 (할인)",
          },
          CATALOG_BODY_ROOM_TYPES[0].ratePlans[1],
        ],
      },
      CATALOG_BODY_ROOM_TYPES[1],
    ],
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) => {
    const body = renamed ? renamedCatalog : CATALOG_BODY;
    return route.fulfill({ status: 200, contentType: "application/json", body });
  });

  await page.goto("/dashboard/hotels");
  await expect(
    page.getByTestId("rate-plan-list-23000000-0000-0000-0000-000000000001"),
  ).toContainText("객실만");

  await page
    .getByTestId("rename-rate-plan-33000000-0000-0000-0000-000000000001")
    .click();
  // 현재 이름으로 미리 채운 상태에서 이어서 치면 바뀐다.
  await page.getByTestId("rename-rate-plan-name").fill("객실만 (할인)");
  await page.getByTestId("rename-rate-plan-submit").click();

  // 이름이 바뀌면 같은 유형 아래 목록이 새 이름으로 바뀐다.
  await expect(
    page.getByTestId("rate-plan-list-23000000-0000-0000-0000-000000000001"),
  ).toContainText("객실만 (할인)");
  await expect(
    page.getByTestId("rename-rate-plan-33000000-0000-0000-0000-000000000001"),
  ).toBeVisible();
});

test("keeps the rename dialog open with a duplicate name message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/hotels/*/room-types/*/rate-plans/*", (route) => {
    if (route.request().method() !== "PATCH") return route.fallback();
    return route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "RATE_PLAN_NAME_CONFLICT",
        message:
          "같은 객실 유형에 이미 같은 이름의 요금제가 있습니다. 다른 이름을 사용해 주세요.",
      }),
    });
  });
  await page.route("**/api/staff/hotels/*/room-types", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: CATALOG_BODY,
    }),
  );

  await page.goto("/dashboard/hotels");
  await page
    .getByTestId("rename-rate-plan-33000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("rename-rate-plan-name").fill("조식 포함");
  await page.getByTestId("rename-rate-plan-submit").click();

  await expect(
    page.getByText(
      "같은 객실 유형에 이미 같은 이름의 요금제가 있습니다. 다른 이름을 사용해 주세요.",
    ),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("rename-rate-plan-submit")).toBeVisible();
  // 이름은 바뀌지 않고 그대로다.
  await expect(
    page.getByTestId("rate-plan-list-23000000-0000-0000-0000-000000000001"),
  ).toContainText("객실만");
});
