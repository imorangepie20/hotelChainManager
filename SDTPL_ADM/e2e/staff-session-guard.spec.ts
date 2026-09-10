import { expect, test } from "@playwright/test";

const SOKCHO = "11000000-0000-0000-0000-000000000001";

test.beforeEach(async ({ page }) => {
  await page.route("**/api/staff/me", (route) => route.fulfill({ status: 200, contentType: "application/json", body: "{}" }));
});

test("redirects a dashboard visitor without a staff session to login", async ({ page }) => {
  await page.goto("/dashboard/default");

  await expect(page.getByLabel("\uC774\uBA54\uC77C")).toBeVisible();
});

test("redirects a visitor when the server rejects the stored staff session", async ({ page }) => {
  await page.addInitScript(() => {
    localStorage.setItem("hotel-chain-staff-session", "expired-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({ id: "test", role: "HQ_ADMIN", hotelId: null }));
  });
  await page.route("**/api/staff/me", (route) => route.fulfill({
    status: 401, contentType: "application/json", body: JSON.stringify({ code: "STAFF_AUTHENTICATION_REQUIRED" }),
  }));

  await page.goto("/dashboard/default");

  await expect(page.getByLabel("\uC774\uBA54\uC77C")).toBeVisible();
});

test("limits a branch staff member to their own hotel context", async ({ page }) => {
  await page.addInitScript((hotelId) => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({
      id: "test-staff", email: "sokcho@hotel-chain.local", displayName: "\uC18D\uCD08 \uC9C0\uC810 \uC9C1\uC6D0",
      role: "BRANCH_STAFF", hotelId,
    }));
  }, SOKCHO);

  await page.goto("/dashboard/default");

  await expect(page.getByRole("button", { name: "\uC18D\uCD08 \uC9C0\uC810" })).toBeVisible();
  await expect(page.getByRole("button", { name: "\uC81C\uC8FC \uC9C0\uC810" })).not.toBeVisible();
});

test("shows the daily operations view for a branch staff member", async ({ page }) => {
  await page.addInitScript((hotelId) => {
    localStorage.setItem("hotel-chain-staff-session", "test-session-token");
    localStorage.setItem("hotel-chain-staff", JSON.stringify({
      id: "test-staff", email: "sokcho@hotel-chain.local", displayName: "\uC18D\uCD08 \uC9C0\uC810 \uC9C1\uC6D0",
      role: "BRANCH_STAFF", hotelId,
    }));
  }, SOKCHO);
  await page.route("**/api/staff/hotels/*/operations?date=*", (route) => route.fulfill({
    contentType: "application/json",
    body: JSON.stringify({ hotelId: SOKCHO, date: "2026-09-10", arrivals: [], departures: [], roomsNeedingCleaning: [] }),
  }));

  await page.goto("/dashboard/operations");

  await expect(page.getByRole("heading", { name: "\uB2F9\uC77C \uC6B4\uC601" })).toBeVisible();
  await expect(page.getByRole("button", { name: "\uC18D\uCD08 \uC9C0\uC810" })).toBeVisible();
});
