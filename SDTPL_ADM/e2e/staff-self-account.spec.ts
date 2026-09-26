import { expect, test } from "@playwright/test";

function seedStaffScript(role: "HQ_ADMIN" | "BRANCH_STAFF") {
  const staff =
    role === "HQ_ADMIN"
      ? {
          id: "staff-10000000-0000-0000-0000-000000000001",
          email: "hq@example.com",
          displayName: "본사 관리자",
          role,
          hotelId: null,
        }
      : {
          id: "staff-20000000-0000-0000-0000-000000000002",
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

test("exposes the my account menu to every role", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "내 계정" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "내 계정" })).toBeVisible();
});

test("shows the read-only identity and the editable name", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/me");

  await expect(page.getByTestId("self-email")).toHaveValue("hq@example.com");
  await expect(page.getByTestId("self-role")).toHaveValue("본사 관리자");
  await expect(page.getByTestId("self-hotel")).toHaveValue("본사");
  await expect(page.getByTestId("self-name")).toHaveValue("본사 관리자");
});

test("updates the display name and keeps the local identity in sync", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff/me", async (route) => {
    if (route.request().method() !== "PATCH") {
      await route.fulfill({ status: 405 });
      return;
    }
    await route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        staffId: "staff-10000000-0000-0000-0000-000000000001",
        email: "hq@example.com",
        displayName: "바뀐 본사",
        role: "HQ_ADMIN",
        changed: true,
      }),
    });
  });

  await page.goto("/dashboard/me");
  await expect(page.getByTestId("self-name")).toHaveValue("본사 관리자");

  await page.getByTestId("open-self-update").click();
  await expect(page.getByTestId("self-update-submit")).toBeDisabled();
  await page.getByTestId("self-current-password").fill("hq-password");
  await page.getByTestId("self-new-password").fill("new-hq-password");
  await page.getByTestId("self-confirm-password").fill("new-hq-password");

  // 비밀번호를 채우면 제출 단추가 켜진다.
  await expect(page.getByTestId("self-update-submit")).toBeEnabled();
  await expect(page.getByTestId("self-update-submit")).toHaveText(
    "비밀번호 변경",
  );

  await page.getByTestId("self-update-submit").click();

  await expect(page.getByTestId("self-update-notice")).toContainText(
    "비밀번호를 바꿨습니다",
  );
  await expect(page.getByTestId("self-update-notice")).toContainText(
    "다른 기기의 세션은 끊겼습니다",
  );
  // 대화상자가 닫힌다.
  await expect(page.getByTestId("self-update-submit")).toHaveCount(0);
});

test("changes both the name and the password in one request", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let received: unknown = null;
  await page.route("**/api/staff/staff/me", async (route) => {
    if (route.request().method() !== "PATCH") {
      await route.fulfill({ status: 405 });
      return;
    }
    received = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        staffId: "staff-10000000-0000-0000-0000-000000000001",
        email: "hq@example.com",
        displayName: "바뀐 본사",
        role: "HQ_ADMIN",
        changed: true,
      }),
    });
  });

  await page.goto("/dashboard/me");
  await page.getByTestId("self-name").fill("바뀐 본사");
  await page.getByTestId("open-self-update").click();
  await page.getByTestId("self-current-password").fill("hq-password");
  await page.getByTestId("self-new-password").fill("new-hq-password");
  await page.getByTestId("self-confirm-password").fill("new-hq-password");
  await page.getByTestId("self-update-submit").click();

  await expect(page.getByTestId("self-update-notice")).toContainText(
    "이름을 바뀐 본사(으)로 바꾸고 비밀번호를 바꿨습니다",
  );
  // 본문에는 바꾸려는 값만 들어간다.
  expect(received).toMatchObject({
    displayName: "바뀐 본사",
    currentPassword: "hq-password",
    newPassword: "new-hq-password",
  });
});

test("keeps the dialog open when the current password is wrong", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/staff/me", async (route) => {
    if (route.request().method() !== "PATCH") {
      await route.fulfill({ status: 405 });
      return;
    }
    // message를 주지 않아 클라이언트가 코드별 한국어 안내로 넘어가는지 확인한다.
    await route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({ code: "STAFF_PASSWORD_MISMATCH" }),
    });
  });

  await page.goto("/dashboard/me");
  await page.getByTestId("open-self-update").click();
  await page.getByTestId("self-current-password").fill("wrong-password");
  await page.getByTestId("self-new-password").fill("new-hq-password");
  await page.getByTestId("self-confirm-password").fill("new-hq-password");
  await page.getByTestId("self-update-submit").click();

  await expect(page.getByTestId("self-update-error")).toContainText(
    "현재 비밀번호가 올바르지 않습니다",
  );
  // 서버 검증 실패 시 대화상자를 닫지 않는다.
  await expect(page.getByTestId("self-update-submit")).toBeVisible();
});

test("blocks a mismatched confirmation before calling the server", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let called = false;
  await page.route("**/api/staff/staff/me", async (route) => {
    if (route.request().method() !== "PATCH") {
      await route.fulfill({ status: 405 });
      return;
    }
    called = true;
    await route.fulfill({ status: 201 });
  });

  await page.goto("/dashboard/me");
  await page.getByTestId("open-self-update").click();
  await page.getByTestId("self-current-password").fill("hq-password");
  await page.getByTestId("self-new-password").fill("new-hq-password");
  await page.getByTestId("self-confirm-password").fill("different-password");
  await page.getByTestId("self-update-submit").click();

  await expect(page.getByTestId("self-update-error")).toContainText(
    "새 비밀번호와 확인 입력이 같지 않습니다",
  );
  // 서버를 부르지 않는다.
  expect(called).toBe(false);
});

test("lets a branch employee update their own account", async ({ page }) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.route("**/api/staff/staff/me", async (route) => {
    if (route.request().method() !== "PATCH") {
      await route.fulfill({ status: 405 });
      return;
    }
    await route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        staffId: "staff-20000000-0000-0000-0000-000000000002",
        email: "sokcho@example.com",
        displayName: "바뀐 속초",
        role: "BRANCH_STAFF",
        changed: true,
      }),
    });
  });

  await page.goto("/dashboard/me");
  await expect(page.getByTestId("self-role")).toHaveValue("지점 직원");
  await expect(page.getByTestId("self-hotel")).toHaveValue(
    "11000000-0000-0000-0000-000000000001",
  );

  await page.getByTestId("self-name").fill("바뀐 속초");
  await page.getByTestId("open-self-update").click();
  // 이름만 바꿀 때는 제출 단추가 켜진다.
  await expect(page.getByTestId("self-update-submit")).toHaveText("이름 변경");
  await page.getByTestId("self-update-submit").click();

  await expect(page.getByTestId("self-update-notice")).toContainText(
    "이름을 바뀐 속초(으)로 바꿨습니다",
  );
});
