import { expect, test } from "@playwright/test";

const ACCOUNTS_BODY = JSON.stringify([
  {
    id: "staff-10000000-0000-0000-0000-000000000001",
    email: "hq@example.test",
    displayName: "본사 관리자",
    role: "HQ_ADMIN",
    hotelId: null,
    hotelName: null,
    active: true,
  },
  {
    id: "staff-20000000-0000-0000-0000-000000000002",
    email: "sokcho@example.test",
    displayName: "속초 직원",
    role: "BRANCH_STAFF",
    hotelId: "11000000-0000-0000-0000-000000000001",
    hotelName: "속초 지점",
    active: true,
  },
]);

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

test("reports staff accounts with roles and hotels", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ACCOUNTS_BODY }),
  );

  await page.goto("/dashboard/staff");

  await expect(page.getByText("직원 2명")).toBeVisible();
  await expect(page.getByRole("cell", { name: "hq@example.test" })).toBeVisible();
  await expect(page.locator("tbody tr").filter({ hasText: "hq@example.test" })).toContainText("본사 관리자");
  await expect(page.getByText("속초 지점")).toBeVisible();
  await expect(page.getByTestId("create-staff")).toBeVisible();
});

test("creates a staff account and shows the temporary password once", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let created = false;
  await page.route("**/api/staff/staff", async (route) => {
    if (route.request().method() === "POST") {
      created = true;
      await route.fulfill({
        status: 201,
        contentType: "application/json",
        body: JSON.stringify({
          staffId: "staff-30000000-0000-0000-0000-000000000003",
          email: "new-editor@example.test",
          displayName: "새 편집자",
          role: "HQ_EDITOR",
          hotelId: null,
          hotelName: null,
          temporaryPassword: "TempPass12345678",
          created: true,
        }),
      });
      return;
    }
    const body = created
      ? JSON.stringify([
          ...JSON.parse(ACCOUNTS_BODY),
          {
            id: "staff-30000000-0000-0000-0000-000000000003",
            email: "new-editor@example.test",
            displayName: "새 편집자",
            role: "HQ_EDITOR",
            hotelId: null,
            hotelName: null,
            active: true,
          },
        ])
      : ACCOUNTS_BODY;
    await route.fulfill({ status: 200, contentType: "application/json", body });
  });

  await page.goto("/dashboard/staff");
  await expect(page.getByText("직원 2명")).toBeVisible();

  await page.getByTestId("create-staff").click();
  await page.getByTestId("create-staff-email").fill("new-editor@example.test");
  await page.getByTestId("create-staff-name").fill("새 편집자");
  await page.getByTestId("create-staff-submit").click();

  // 임시 비밀번호를 한 번 보여준다.
  await expect(page.getByTestId("temporary-password")).toHaveText("TempPass12345678");
  await page.getByRole("button", { name: "확인" }).click();

  await expect(page.getByText("직원 3명")).toBeVisible();
  await expect(page.getByRole("cell", { name: "new-editor@example.test" })).toBeVisible();
});

test("keeps the dialog open with a duplicate email message", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff", async (route) => {
    if (route.request().method() === "POST") {
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({ code: "STAFF_EMAIL_DUPLICATE", message: "이미 등록된 이메일입니다." }),
      });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: ACCOUNTS_BODY });
  });

  await page.goto("/dashboard/staff");
  await page.getByTestId("create-staff").click();
  await page.getByTestId("create-staff-email").fill("hq@example.test");
  await page.getByTestId("create-staff-name").fill("중복");
  await page.getByTestId("create-staff-submit").click();

  await expect(page.getByText("이미 등록된 이메일입니다.")).toBeVisible();
  await expect(page.getByTestId("create-staff-submit")).toBeVisible();
});

test("requires a hotel when the role is branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let postSeen = false;
  await page.route("**/api/staff/staff", async (route) => {
    if (route.request().method() === "POST") {
      postSeen = true;
      await route.fulfill({ status: 201, contentType: "application/json", body: JSON.stringify({}) });
      return;
    }
    await route.fulfill({ status: 200, contentType: "application/json", body: ACCOUNTS_BODY });
  });

  await page.goto("/dashboard/staff");
  await page.getByTestId("create-staff").click();
  await page.getByTestId("create-staff-role").selectOption("BRANCH_STAFF");

  await expect(page.getByTestId("create-staff-hotel")).toBeVisible();
  await page.getByTestId("create-staff-email").fill("branch@example.test");
  await page.getByTestId("create-staff-name").fill("지점 직원");
  await page.getByTestId("create-staff-submit").click();

  // 브라우저가 빈 지점 선택을 차단해서 요청이 나가지 않는다.
  const message = await page.getByTestId("create-staff-hotel").evaluate(
    (element) => (element as HTMLSelectElement).validationMessage,
  );
  expect(message).not.toBe("");
  expect(postSeen).toBe(false);
  await expect(page.getByTestId("create-staff-submit")).toBeVisible();
});

test("tells a branch employee the view is headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/staff");

  await expect(page.getByText("직원 관리는 본사 관리자만 할 수 있습니다.")).toBeVisible();
  await expect(page.getByTestId("create-staff")).toHaveCount(0);
});

test("shows a recovery notice when the accounts are unreachable", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff", (route) => route.fulfill({ status: 500 }));

  await page.goto("/dashboard/staff");

  await expect(page.getByText("직원 목록을 불러오지 못했습니다.")).toBeVisible();
});

test("resets a password and shows the new temporary password once", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ACCOUNTS_BODY }),
  );
  await page.route("**/api/staff/staff/*/password", (route) =>
    route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        staffId: "staff-20000000-0000-0000-0000-000000000002",
        email: "sokcho@example.test",
        displayName: "속초 직원",
        role: "BRANCH_STAFF",
        hotelId: "11000000-0000-0000-0000-000000000001",
        hotelName: "속초 지점",
        temporaryPassword: "ResetPass9876",
        created: true,
        cooldownSeconds: 300,
      }),
    }),
  );

  await page.goto("/dashboard/staff");
  await expect(page.getByText("직원 2명")).toBeVisible();

  await page.getByTestId("reset-password-staff-20000000-0000-0000-0000-000000000002").click();

  await expect(page.getByTestId("temporary-password")).toHaveText("ResetPass9876");
  await page.getByRole("button", { name: "확인" }).click();
  await expect(page.getByTestId("temporary-password")).toHaveCount(0);
});

test("explains when a repeat reset is rate limited", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ACCOUNTS_BODY }),
  );
  await page.route("**/api/staff/staff/*/password", (route) =>
    route.fulfill({
      status: 409,
      contentType: "application/json",
      body: JSON.stringify({
        code: "STAFF_PASSWORD_RESET_COOLDOWN",
        message: "직원 임시 비밀번호 재발급은 300초가 지난 뒤에 다시 가능합니다.",
      }),
    }),
  );

  await page.goto("/dashboard/staff");
  await page.getByTestId("reset-password-staff-20000000-0000-0000-0000-000000000002").click();

  await expect(page.getByTestId("reset-error")).toHaveText(
    "직원 임시 비밀번호 재발급은 300초가 지난 뒤에 다시 가능합니다.",
  );
  // 비밀번호를 받지 못했으므로 발급 대화상자는 열지 않는다.
  await expect(page.getByTestId("temporary-password")).toHaveCount(0);
});

test("explains when the staff account is missing", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ACCOUNTS_BODY }),
  );
  await page.route("**/api/staff/staff/*/password", (route) =>
    route.fulfill({
      status: 404,
      contentType: "application/json",
      body: JSON.stringify({ code: "STAFF_ACCOUNT_NOT_FOUND", message: "직원을 찾을 수 없습니다." }),
    }),
  );

  await page.goto("/dashboard/staff");
  await page.getByTestId("reset-password-staff-10000000-0000-0000-0000-000000000001").click();

  // 서버가 내려준 메시지를 그대로 보여준다.
  await expect(page.getByTestId("reset-error")).toHaveText("직원을 찾을 수 없습니다.");
});

test("does not offer password reset to branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/staff");

  await expect(page.getByText("직원 관리는 본사 관리자만 할 수 있습니다.")).toBeVisible();
  await expect(page.getByTestId("reset-password-staff-10000000-0000-0000-0000-000000000001")).toHaveCount(0);
});
