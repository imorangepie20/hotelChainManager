import { expect, test } from "@playwright/test";

const POLICY_BODY = JSON.stringify({
  cancellation: {
    refundCutoffDaysBefore: 1,
    refundCutoffLocalTime: "18:00",
    timezone: "Asia/Seoul",
  },
  changeApprovalDirectLimitKrw: 100000,
  changeApprovalTtlSeconds: 86400,
  changeSettlementEnabled: false,
  revision: 1,
});

const EMPTY_REVISIONS = JSON.stringify({
  revisions: [],
  totalCount: 0,
  limit: 10,
  offset: 0,
});

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
});

test("exposes the policies menu to headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "공통 정책" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "공통 정책" })).toHaveCount(0);
});

test("reports the current cancellation policy and change limits", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: POLICY_BODY,
    }),
  );

  await page.goto("/dashboard/policies");

  await expect(page.getByTestId("cutoff-days")).toHaveText("체크인 1일 전");
  await expect(page.getByTestId("cutoff-time")).toHaveText("18:00");
  await expect(page.getByTestId("change-limit")).toHaveText("100,000원");
  await expect(page.getByTestId("approval-ttl")).toHaveText("24시간");
  await expect(page.getByText("비활성")).toBeVisible();
  await expect(page.getByTestId("edit-cancellation")).toBeVisible();
  await expect(page.getByTestId("edit-limit")).toBeVisible();
  await expect(page.getByTestId("edit-approval-ttl")).toBeVisible();
});

test("updates the cancellation policy and reflects it", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let updated = false;
  // 먼저 등록한 route부터 평가하므로 더 좁은 경로를 나중에 붙인다.
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: updated
        ? JSON.stringify({
            cancellation: {
              refundCutoffDaysBefore: 3,
              refundCutoffLocalTime: "20:30",
              timezone: "Asia/Seoul",
            },
            changeApprovalDirectLimitKrw: 100000,
            changeSettlementEnabled: false,
            revision: 2,
          })
        : POLICY_BODY,
    }),
  );
  await page.route("**/api/staff/policies/cancellation", (route) => {
    updated = true;
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        refundCutoffDaysBefore: 3,
        refundCutoffLocalTime: "20:30",
        timezone: "Asia/Seoul",
        revision: 2,
        created: true,
      }),
    });
  });

  await page.goto("/dashboard/policies");
  await expect(page.getByTestId("cutoff-days")).toHaveText("체크인 1일 전");

  await page.getByTestId("edit-cancellation").click();
  await page.getByTestId("edit-cutoff-days").fill("3");
  await page.getByTestId("edit-cutoff-time").fill("20:30");
  await page.getByTestId("edit-cancellation-submit").click();

  await expect(page.getByTestId("cutoff-days")).toHaveText("체크인 3일 전");
  await expect(page.getByTestId("cutoff-time")).toHaveText("20:30");
});

test("keeps the dialog open with a server validation message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // 먼저 등록한 route부터 평가하므로 더 좁은 경로를 나중에 붙인다.
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: POLICY_BODY,
    }),
  );
  await page.route("**/api/staff/policies/cancellation", (route) =>
    route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({
        code: "INVALID_REQUEST",
        message: "취소 마감 시각은 HH:MM 형식이어야 합니다.",
      }),
    }),
  );

  await page.goto("/dashboard/policies");
  await page.getByTestId("edit-cancellation").click();
  await page.getByTestId("edit-cutoff-days").fill("3");
  await page.getByTestId("edit-cancellation-submit").click();

  await expect(
    page.getByText("취소 마감 시각은 HH:MM 형식이어야 합니다."),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("edit-cancellation-submit")).toBeVisible();
});

test("tells a branch employee the view is headquarters only", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/policies");

  await expect(
    page.getByText("공통 정책은 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(page.getByTestId("edit-cancellation")).toHaveCount(0);
  await expect(page.getByTestId("edit-limit")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("shows a recovery notice when the policy is unreachable", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({ status: 500 }),
  );

  await page.goto("/dashboard/policies");

  await expect(
    page.getByText("공통 정책을 불러오지 못했습니다."),
  ).toBeVisible();
});

test("updates the change approval limit and reflects it", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let updated = false;
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: updated
        ? JSON.stringify({
            cancellation: {
              refundCutoffDaysBefore: 1,
              refundCutoffLocalTime: "18:00",
              timezone: "Asia/Seoul",
            },
            changeApprovalDirectLimitKrw: 300000,
            changeSettlementEnabled: false,
            revision: 1,
          })
        : POLICY_BODY,
    }),
  );
  await page.route("**/api/staff/policies/change-limit", (route) => {
    updated = true;
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        directLimitKrw: 300000,
        revision: 1,
        created: true,
      }),
    });
  });

  await page.goto("/dashboard/policies");
  await expect(page.getByTestId("change-limit")).toHaveText("100,000원");

  await page.getByTestId("edit-limit").click();
  await page.getByTestId("edit-limit-value").fill("300000");
  await page.getByTestId("edit-limit-submit").click();

  await expect(page.getByTestId("change-limit")).toHaveText("300,000원");
});

test("keeps the limit dialog open with a server validation message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // Playwright는 나중에 등록한 route부터 평가한다. 더 좁은 경로를 나중에 붙인다.
  await page.route(/.*\/api\/staff\/policies\/change-limit(\?.*)?$/, (route) =>
    route.fulfill({
      status: 400,
      contentType: "application/json",
      body: JSON.stringify({
        code: "INVALID_REQUEST",
        message: "예약 변경 승인 한도는 0원 이상 10,000,000원 이하여야 합니다.",
      }),
    }),
  );
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: POLICY_BODY,
    }),
  );

  await page.goto("/dashboard/policies");
  await page.getByTestId("edit-limit").click();
  await page.getByTestId("edit-limit-value").fill("100000000");
  await page.getByTestId("edit-limit-submit").click();

  await expect(
    page.getByText(
      "예약 변경 승인 한도는 0원 이상 10,000,000원 이하여야 합니다.",
    ),
  ).toBeVisible();
  await expect(page.getByTestId("edit-limit-submit")).toBeVisible();
});

test("reports policy revisions with staff context", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // Playwright는 나중에 등록한 route부터 평가한다. 빈 이력 응답으로 채우는
  // beforeEach의 route보다 본문 route가 먼저 검사되게 하려면 본문을 가장
  // 나중에 등록해야 한다. 순서가 바뀌면 빈 이력이 본문 요청을 덮어쓴다.
  await page.route(/.*\/api\/staff\/policies\/revisions(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        revisions: [
          {
            key: "change-approval",
            summary: "예약 변경 승인 한도 300,000원",
            staffEmail: "hq@example.com",
            staffDisplayName: "본사 관리자",
            staffRole: "HQ_ADMIN",
            createdAt: "2026-09-20T11:30:00+09:00",
          },
          {
            key: "cancellation",
            summary: "취소 정책 체크인 3일 전 20:30 마감",
            staffEmail: "hq@example.com",
            staffDisplayName: "본사 관리자",
            staffRole: "HQ_ADMIN",
            createdAt: "2026-09-20T10:00:00+09:00",
          },
        ],
        totalCount: 2,
        limit: 10,
        offset: 0,
      }),
    }),
  );
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: POLICY_BODY,
    }),
  );

  await page.goto("/dashboard/policies");

  await expect(page.getByText("정책 변경 이력 2건")).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "예약 변경 승인 한도 300,000원" }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "취소 정책 체크인 3일 전 20:30 마감" }),
  ).toBeVisible();
  await expect(
    page.getByRole("cell", { name: "본사 관리자" }).first(),
  ).toBeVisible();
});

test("moves to the next revision page", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let offset = 0;
  await page.route(
    /.*\/api\/staff\/policies\/revisions(\?.*)?$/,
    async (route) => {
      const url = new URL(route.request().url());
      offset = Number(url.searchParams.get("offset") ?? 0);
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          revisions:
            offset === 0
              ? [
                  {
                    key: "cancellation",
                    summary: "취소 정책 체크인 3일 전 20:30 마감",
                    staffEmail: "hq@example.com",
                    staffDisplayName: "본사 관리자",
                    staffRole: "HQ_ADMIN",
                    createdAt: "2026-09-20T10:00:00+09:00",
                  },
                ]
              : [
                  {
                    key: "change-approval",
                    summary: "예약 변경 승인 한도 200,000원",
                    staffEmail: "hq@example.com",
                    staffDisplayName: "본사 관리자",
                    staffRole: "HQ_ADMIN",
                    createdAt: "2026-09-19T10:00:00+09:00",
                  },
                ],
          totalCount: 12,
          limit: 10,
          offset,
        }),
      });
    },
  );
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: POLICY_BODY,
    }),
  );

  await page.goto("/dashboard/policies");
  await expect(page.getByText("정책 변경 이력 12건")).toBeVisible();

  await page.getByRole("button", { name: "다음 페이지" }).click();

  await expect(page.getByTestId("revisions-page")).toHaveText("2 / 2");
  await expect(
    page.getByRole("cell", { name: "예약 변경 승인 한도 200,000원" }),
  ).toBeVisible();
  await expect(
    page.getByRole("button", { name: "다음 페이지" }),
  ).toBeDisabled();
});

test("tells a branch employee the revisions are headquarters only", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/policies");

  await expect(
    page.getByText("공통 정책은 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(page.getByTestId("edit-limit")).toHaveCount(0);
  await expect(page.getByTestId("edit-approval-ttl")).toHaveCount(0);
});

test("updates the change approval ttl and reflects it", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let updated = false;
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: updated
        ? JSON.stringify({
            cancellation: {
              refundCutoffDaysBefore: 1,
              refundCutoffLocalTime: "18:00",
              timezone: "Asia/Seoul",
            },
            changeApprovalDirectLimitKrw: 100000,
            changeApprovalTtlSeconds: 7200,
            changeSettlementEnabled: false,
            revision: 1,
          })
        : POLICY_BODY,
    }),
  );
  await page.route("**/api/staff/policies/change-approval-ttl", (route) => {
    updated = true;
    return route.fulfill({
      status: 201,
      contentType: "application/json",
      body: JSON.stringify({
        approvalTtlSeconds: 7200,
        revision: 1,
        created: true,
      }),
    });
  });

  await page.goto("/dashboard/policies");
  await expect(page.getByTestId("approval-ttl")).toHaveText("24시간");

  await page.getByTestId("edit-approval-ttl").click();
  await page.getByTestId("edit-approval-ttl-value").fill("2");
  await page.getByTestId("edit-approval-ttl-submit").click();

  await expect(page.getByTestId("approval-ttl")).toHaveText("2시간");
});

test("keeps the ttl dialog open with a server validation message", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  // Playwright는 나중에 등록한 route부터 평가한다. 더 좁은 경로를 나중에 붙인다.
  await page.route(
    /.*\/api\/staff\/policies\/change-approval-ttl(\?.*)?$/,
    (route) =>
      route.fulfill({
        status: 400,
        contentType: "application/json",
        body: JSON.stringify({
          code: "INVALID_REQUEST",
          message: "예약 변경 승인 TTL은 60초 이상 604,800초 이하여야 합니다.",
        }),
      }),
  );
  await page.route(/.*\/api\/staff\/policies(\?.*)?$/, (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: POLICY_BODY,
    }),
  );

  await page.goto("/dashboard/policies");
  await page.getByTestId("edit-approval-ttl").click();
  await page.getByTestId("edit-approval-ttl-value").fill("200");
  await page.getByTestId("edit-approval-ttl-submit").click();

  await expect(
    page.getByText("예약 변경 승인 TTL은 60초 이상 604,800초 이하여야 합니다."),
  ).toBeVisible();
  // 대화상자가 열려 있어 사용자가 입력을 고칠 수 있다.
  await expect(page.getByTestId("edit-approval-ttl-submit")).toBeVisible();
});
