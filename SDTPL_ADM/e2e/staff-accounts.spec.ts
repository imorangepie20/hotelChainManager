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

function seedStaffScript(
  role: "HQ_ADMIN" | "BRANCH_STAFF",
  id = "test",
) {
  const staff =
    role === "HQ_ADMIN"
      ? { id, email: "hq@example.com", displayName: "본사 관리자", role, hotelId: null }
      : {
          id,
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

// 삭제한 직원은 서버가 돌려주는 목록에서 빠진다.
function deletedAccountsBody() {
  const accounts = JSON.parse(ACCOUNTS_BODY) as Array<{
    [key: string]: unknown;
  }>;
  return JSON.stringify(accounts.slice(0, 1));
}

// /api/staff/staff(목록 GET)과 /api/staff/staff/{id}(DELETE)를 한
// 핸들러가 잡는다. glob의 *는 /를 넘지 못하므로 컬렉션과 하위 경로를
// 함께 쓸 때는 중괄호 확장을 쓴다.
function deletionListHandler(
  listBody: () => string,
  deleteResponse: (route: import("@playwright/test").Route) => Promise<void>,
) {
  return async (route: import("@playwright/test").Route) => {
    const url = route.request().url();
    // /api/staff/me는 url이 /api/staff/staff로 끝나지 않아 여기에 들지 않는다.
    if (url.endsWith("/api/staff/staff")) {
      if (route.request().method() !== "GET") {
        await route.fulfill({
          status: 405,
          contentType: "application/json",
          body: JSON.stringify({}),
        });
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: listBody(),
      });
      return;
    }
    // /api/staff/staff/{id}의 DELETE만 잡는다. 비밀번호 재발급 등
    // 다른 하위 경로 POST·PATCH는 이 핸들러가 응답하지 않는다.
    if (
      url.includes("/api/staff/staff/") &&
      route.request().method() === "DELETE"
    ) {
      await deleteResponse(route);
      return;
    }
    await route.fulfill({
      status: 405,
      contentType: "application/json",
      body: JSON.stringify({}),
    });
  };
}

// 삭제는 매번 새 멱원 키를 쓰는 본사 전용 동작이다.
test("deletes an account and removes it from the list", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let deleted = false;
  await page.route(
    "**/api/staff/staff{,/**}",
    deletionListHandler(
      () => (deleted ? deletedAccountsBody() : ACCOUNTS_BODY),
      async (route) => {
        deleted = true;
        await route.fulfill({
          status: 201,
          contentType: "application/json",
          body: JSON.stringify({
            staffId: "staff-20000000-0000-0000-0000-000000000002",
            email:
              "deleted-staff-20000000-0000-0000-0000-000000000002@deleted.local",
            displayName: "삭제된 직원",
            deleted: true,
            remainingStaff: 1,
          }),
        });
      },
    ),
  );

  await page.goto("/dashboard/staff");
  await expect(page.getByText("직원 2명")).toBeVisible();

  // 목록의 id 앞에 delete-staff- 접두어가 붙는다.
  await page
    .getByTestId("delete-staff-staff-20000000-0000-0000-0000-000000000002")
    .click();

  await expect(page.getByText("이 작업은 되돌릴 수 없다")).toBeVisible();
  await page.getByTestId("delete-staff-submit").click();

  await expect(deleted).toBe(true);
  // 삭제된 직원은 목록에서 빠진다.
  await expect(page.getByText("직원 1명")).toBeVisible();
  await expect(page.getByTestId("delete-staff-notice")).toContainText(
    "남은 직원은 1명입니다",
  );
});

test("keeps the delete dialog open when a conflict blocks deletion", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // message를 주지 않아 클라이언트가 코드별 한국어 안내로 넘어가는지 확인한다.
  await page.route(
    "**/api/staff/staff{,/**}",
    deletionListHandler(() => ACCOUNTS_BODY, async (route) => {
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({ code: "STAFF_DELETION_CONFLICT" }),
      });
    }),
  );

  await page.goto("/dashboard/staff");
  await expect(page.getByText("직원 2명")).toBeVisible();
  await page
    .getByTestId("delete-staff-staff-20000000-0000-0000-0000-000000000002")
    .click();
  await page.getByTestId("delete-staff-submit").click();

  await expect(page.getByTestId("delete-staff-form-error")).toContainText(
    "진행 중인 예약 변경 요청이나 정산 실행이 있어 삭제할 수 없습니다",
  );
  // 서버 검증 실패 시 대화상자를 닫지 않는다.
  await expect(page.getByTestId("delete-staff-submit")).toBeVisible();
  // 직원은 여전히 2명이다.
  await expect(page.getByText("직원 2명")).toBeVisible();
});

test("explains when deleting your own account is refused", async ({ page }) => {
  await page.addInitScript(
    seedStaffScript("HQ_ADMIN", "staff-10000000-0000-0000-0000-000000000001"),
  );
  await page.route(
    "**/api/staff/staff{,/**}",
    deletionListHandler(() => ACCOUNTS_BODY, async (route) => {
      await route.fulfill({
        status: 409,
        contentType: "application/json",
        body: JSON.stringify({ code: "STAFF_SELF_MODIFICATION_FORBIDDEN" }),
      });
    }),
  );

  await page.goto("/dashboard/staff");
  await expect(page.getByText("직원 2명")).toBeVisible();
  await page
    .getByTestId("delete-staff-staff-10000000-0000-0000-0000-000000000001")
    .click();
  await page.getByTestId("delete-staff-submit").click();

  await expect(page.getByTestId("delete-staff-form-error")).toContainText(
    "본인 계정은 삭제할 수 없습니다",
  );
  await expect(page.getByText("직원 2명")).toBeVisible();
});

test("keeps the delete button away from branch staff", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/staff");

  await expect(page.getByText("직원 관리는 본사 관리자만 할 수 있습니다.")).toBeVisible();
  await expect(page.getByTestId("delete-staff-staff-10000000-0000-0000-0000-000000000001")).toHaveCount(0);
});
