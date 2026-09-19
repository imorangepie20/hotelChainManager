import { expect, test } from "@playwright/test";

const INVENTORY_BODY = JSON.stringify({
  hotelId: "11000000-0000-0000-0000-000000000001",
  roomTypes: [
    {
      roomTypeId: "21000000-0000-0000-0000-000000000001",
      name: "스탠다드 시티",
      maxOccupancy: 2,
      days: [
        { stayDate: "2026-11-01", capacity: 10, held: 2, confirmed: 7, remaining: 1 },
        { stayDate: "2026-11-02", capacity: 5, held: 0, confirmed: 5, remaining: 0 },
      ],
    },
    {
      roomTypeId: "21000000-0000-0000-0000-000000000002",
      name: "디럭스 오션",
      maxOccupancy: 3,
      days: [{ stayDate: "2026-11-01", capacity: 8, held: 1, confirmed: 2, remaining: 5 }],
    },
  ],
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
