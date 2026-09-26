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
        { stayDate: "2026-11-03", capacity: 10, held: 1, confirmed: 3, remaining: 6 },
        { stayDate: "2026-11-04", capacity: 10, held: 0, confirmed: 2, remaining: 8 },
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

const HOTELS_BODY = JSON.stringify([
  {
    id: "11000000-0000-0000-0000-000000000001",
    name: "속초 지점",
    region: "속초",
    timezone: "Asia/Seoul",
    active: true,
  },
]);

function seedStaffScript() {
  const staff = { id: "test", email: "hq@example.com", displayName: "본사 관리자", role: "HQ_ADMIN", hotelId: null };
  return `
    window.localStorage.setItem("hotel-chain-staff", ${JSON.stringify(JSON.stringify(staff))});
    window.localStorage.setItem("hotel-chain-staff-session", "test-session-token");
  `;
}

test("inventory grid stays inside a 390px viewport", async ({ page }) => {
  await page.addInitScript(seedStaffScript());
  await page.route("**/api/staff/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );
  await page.route("**/api/staff/hotels", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: HOTELS_BODY }),
  );
  await page.route("**/api/staff/hotels/*/inventory**", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: INVENTORY_BODY }),
  );

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/dashboard/inventory");

  await expect(page.getByRole("button", { name: "속초 지점" })).toHaveAttribute(
    "aria-pressed",
    "true",
  );
  await expect(page.getByText("선택됨", { exact: true })).toBeVisible();
  await expect(page.getByRole("rowheader", { name: "스탠다드 시티" })).toBeVisible();

  const overflow = await page.evaluate(() => {
    return {
      scrollWidth: document.documentElement.scrollWidth,
      clientWidth: document.documentElement.clientWidth,
    };
  });
  expect(overflow.scrollWidth).toBeLessThanOrEqual(overflow.clientWidth);
});
