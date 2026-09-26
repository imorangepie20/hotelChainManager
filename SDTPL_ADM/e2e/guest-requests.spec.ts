import { expect, test } from "@playwright/test";

const HOTEL_ID = "11000000-0000-0000-0000-000000000001";

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
          hotelId: HOTEL_ID,
        };
  return `
    window.localStorage.setItem("hotel-chain-staff", ${JSON.stringify(JSON.stringify(staff))});
    window.localStorage.setItem("hotel-chain-staff-session", "test-session-token");
  `;
}

const REQUEST = {
  id: "66000000-0000-0000-0000-000000000001",
  hotelId: HOTEL_ID,
  hotelName: "속초 지점",
  reservationId: null,
  requestType: "ROOM_REQUEST",
  subject: "객실 층수 요청",
  guestName: "request-guest",
  status: "OPEN",
  priority: "NORMAL",
  assignedDisplayName: null,
  createdAt: "2026-09-22T10:00:00Z",
  updatedAt: "2026-09-22T10:00:00Z",
};

const RESOLVED_REQUEST = {
  ...REQUEST,
  id: "66000000-0000-0000-0000-000000000002",
  subject: "조식 시간 문의",
  requestType: "AMENITY_REQUEST",
  status: "RESOLVED",
  assignedDisplayName: "본사 관리자",
};

function listBody(status: string, requests: object[]) {
  return JSON.stringify({
    requests,
    totalCount: requests.length,
    status,
    hotelId: HOTEL_ID,
    limit: 20,
    offset: 0,
  });
}

test.beforeEach(async ({ page }) => {
  await page.route("**/api/staff/me", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: "{}" }),
  );
});

test("exposes the guest request menu to staff roles", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "고객 요청" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "고객 요청" })).toBeVisible();
});

test("lists open requests with type and status labels", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/guest-requests*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: listBody("OPEN", [REQUEST]),
    }),
  );

  await page.goto("/dashboard/guest-requests");

  await expect(page.getByRole("cell", { name: "객실 요청" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "접수" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "request-guest" })).toBeVisible();
  await expect(page.getByText("접수 1건")).toBeVisible();
});

test("switches the status filter", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const requested: string[] = [];
  await page.route("**/api/staff/guest-requests*", async (route) => {
    const url = new URL(route.request().url());
    requested.push(url.searchParams.get("status") ?? "");
    const status = url.searchParams.get("status") ?? "OPEN";
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: listBody(
        status,
        status === "RESOLVED" ? [RESOLVED_REQUEST] : [REQUEST],
      ),
    });
  });

  await page.goto("/dashboard/guest-requests");
  await expect(page.getByRole("cell", { name: "객실 요청" })).toBeVisible();

  await page.getByTestId("guest-request-status-RESOLVED").click();

  await expect.poll(() => requested.at(-1)).toBe("RESOLVED");
  await expect(page.getByRole("cell", { name: "해결" })).toBeVisible();
});

test("shows an empty state for the selected status", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/guest-requests*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: listBody("OPEN", []),
    }),
  );

  await page.goto("/dashboard/guest-requests");

  await expect(page.getByTestId("guest-request-empty")).toBeVisible();
});

test("tells non-operational staff the view is restricted", async ({
  page,
}) => {
  const editor = {
    id: "test",
    email: "editor@example.com",
    displayName: "영문 편집자",
    role: "HQ_EDITOR",
    hotelId: null,
  };
  await page.addInitScript(`
    window.localStorage.setItem("hotel-chain-staff", ${JSON.stringify(JSON.stringify(editor))});
    window.localStorage.setItem("hotel-chain-staff-session", "test-session-token");
  `);
  await page.goto("/dashboard/guest-requests");

  await expect(
    page.getByText("고객 요청은 지점 직원과 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("shows a recovery notice when the list is unreachable", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/guest-requests*", (route) =>
    route.fulfill({ status: 500 }),
  );

  await page.goto("/dashboard/guest-requests");

  await expect(
    page.getByText("고객 요청을 불러오지 못했습니다."),
  ).toBeVisible();
});

test("opens a request and transitions its status", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/guest-requests?*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: listBody("OPEN", [REQUEST]),
    }),
  );
  await page.route("**/api/staff/guest-requests/*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        ...REQUEST,
        body: "가능하면 높은 층의 객실을 부탁드립니다.",
        guestEmail: "guest@example.com",
        guestPhone: "01012345678",
        assignedTo: null,
        events: [
          {
            id: "77000000-0000-0000-0000-000000000001",
            eventType: "CREATED",
            fromStatus: null,
            toStatus: "OPEN",
            actorDisplayName: "시스템",
            note: null,
            createdAt: "2026-09-22T10:00:00Z",
          },
        ],
      }),
    }),
  );

  await page.goto("/dashboard/guest-requests");
  await page.getByTestId(`guest-request-open-${REQUEST.id}`).click();

  await expect(page.getByText("높은 층의 객실")).toBeVisible();
  await expect(page.getByText("guest@example.com")).toBeVisible();
  await expect(page.getByText("01012345678")).toBeVisible();

  await page.getByTestId("guest-request-transition-IN_PROGRESS").click();
  await page
    .getByRole("textbox", { name: "처리 안내" })
    .fill("프런트데스크에서 확인 중입니다.");
  await page.getByRole("button", { name: "상태 변경" }).click();

  await expect(
    page.getByText("프런트데스크에서 확인 중입니다."),
  ).toBeVisible();
});
