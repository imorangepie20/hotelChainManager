import { expect, test } from "@playwright/test";

const HOTELS = [
  {
    hotelId: "11000000-0000-0000-0000-000000000001",
    hotelName: "속초 지점",
    region: "sokcho",
    reservations: 6,
    cancelled: 1,
    noShow: 1,
    expired: 0,
    revenueKrw: 400000,
    changeRequestsPending: 1,
    changeRequestsCompleted: 1,
    occupancyRate: 0.2,
  },
  {
    hotelId: "11000000-0000-0000-0000-000000000002",
    hotelName: "제주 지점",
    region: "jeju",
    reservations: 2,
    cancelled: 0,
    noShow: 0,
    expired: 0,
    revenueKrw: 400000,
    changeRequestsPending: 0,
    changeRequestsCompleted: 0,
    occupancyRate: 0,
  },
];

function reportBody() {
  return JSON.stringify({
    from: "2026-09-14",
    to: "2026-09-20",
    days: 7,
    hotels: HOTELS,
    totals: {
      reservations: 8,
      cancelled: 1,
      noShow: 1,
      expired: 0,
      revenueKrw: 800000,
      changeRequestsPending: 1,
      changeRequestsCompleted: 1,
    },
    previousPeriod: {
      from: "2026-09-07",
      to: "2026-09-13",
      days: 7,
      totals: {
        reservations: 4,
        cancelled: 2,
        noShow: 0,
        expired: 1,
        revenueKrw: 1000000,
        changeRequestsPending: 2,
        changeRequestsCompleted: 0,
      },
    },
  });
}

function kstToday() {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: "Asia/Seoul",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).formatToParts(new Date());
  const value = Object.fromEntries(parts.map((part) => [part.type, part.value]));
  return `${value.year}-${value.month}-${value.day}`;
}

const ROOM_TYPE_BODY = JSON.stringify({
  from: "2026-09-14",
  to: "2026-09-20",
  days: 7,
  hotelId: "11000000-0000-0000-0000-000000000001",
  hotelName: "속초 지점",
  roomTypes: [
    {
      roomTypeId: "26000000-0000-0000-0000-000000000001",
      roomTypeName: "스탠다드 트윈",
      reservations: 5,
      cancelled: 1,
      noShow: 0,
      revenueKrw: 300000,
      revenueShare: 0.75,
    },
    {
      roomTypeId: "26000000-0000-0000-0000-000000000002",
      roomTypeName: "디럭스 더블",
      reservations: 1,
      cancelled: 0,
      noShow: 0,
      revenueKrw: 100000,
      revenueShare: 0.25,
    },
  ],
  totals: { reservations: 6, cancelled: 1, noShow: 0, revenueKrw: 400000 },
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

test("exposes the report menu to headquarters only", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "운영 통계" })).toBeVisible();

  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/default");

  await expect(page.getByRole("link", { name: "운영 통계" })).toHaveCount(0);
});

test("reports per-hotel metrics and totals", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: reportBody(),
    }),
  );
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: ROOM_TYPE_BODY,
    }),
  );

  await page.goto("/dashboard/reports");

  await expect(page.getByTestId("report-range")).toHaveText(
    "9월 14일 ~ 9월 20일 · 7일",
  );
  await expect(page.getByTestId("report-total-reservations")).toHaveText("8");
  await expect(page.getByTestId("report-total-revenue")).toHaveText(
    "800,000원",
  );
  await expect(page.getByTestId("report-total-pending")).toHaveText("1");
  await expect(page.getByTestId("report-total-completed")).toHaveText("1");

  await expect(page.getByRole("cell", { name: "속초 지점" })).toBeVisible();
  await expect(page.getByTestId("report-reservations").first()).toHaveText("6");
  await expect(page.getByText("20.0%").first()).toBeVisible();
  await expect(page.getByRole("cell", { name: "제주 지점" })).toBeVisible();
});

test("shows previous-period deltas and exposes chart values to assistive technology", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations?*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: reportBody(),
    }),
  );
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: ROOM_TYPE_BODY,
    }),
  );

  await page.goto("/dashboard/reports");

  await expect(page.getByTestId("report-previous-range")).toHaveText(
    /9월 7일 ~ 9월 13일/,
  );
  await expect(page.getByTestId("report-reservations-change")).toContainText(
    "+4",
  );
  await expect(page.getByTestId("report-reservations-change")).toContainText(
    "+100.0%",
  );
  await expect(page.getByTestId("report-revenue-change")).toContainText(
    "-20.0%",
  );
  // 이전 값이 0인 경우 Infinity/NaN 대신 사람이 읽을 수 있는 상태를 제공한다.
  await expect(page.getByTestId("report-completed-change")).toContainText(
    "신규",
  );

  const chart = page.getByRole("img", {
    name: /지점별 매출 및 점유율 차트.*속초 지점.*400,000원.*20\.0%.*제주 지점.*400,000원.*0\.0%/,
  });
  await expect(chart).toBeVisible();
  await expect(chart).toHaveAccessibleDescription(
    /막대는 지점별 매출을 나타내며.*동일한 값은 다음 지점별 통계 표에서 확인할 수 있습니다/,
  );
  const dataTable = page.getByRole("table", { name: "지점별 통계" });
  await expect(dataTable.getByRole("columnheader", { name: "매출" })).toBeVisible();
  await expect(dataTable.getByRole("cell", { name: "400,000원" }).first()).toBeVisible();
});

test("shows zero percent when both compared values are zero", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const body = JSON.parse(reportBody()) as {
    totals: { changeRequestsPending: number };
    previousPeriod: { totals: { changeRequestsPending: number } };
  };
  body.totals.changeRequestsPending = 0;
  body.previousPeriod.totals.changeRequestsPending = 0;
  await page.route("**/api/staff/reports/operations?*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(body),
    }),
  );
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ROOM_TYPE_BODY }),
  );

  await page.goto("/dashboard/reports");

  await expect(page.getByTestId("report-pending-change")).toHaveText("0 · 0.0%");
  await expect(page.getByText(/Infinity|NaN/)).toHaveCount(0);
});

test("switches the report between Korean and English with the keyboard", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let reportRequests = 0;
  await page.route("**/api/staff/reports/operations?*", (route) => {
    reportRequests += 1;
    return route.fulfill({ status: 200, contentType: "application/json", body: reportBody() });
  });
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ROOM_TYPE_BODY }),
  );

  await page.goto("/dashboard/reports");
  await expect(page.getByTestId("report-range")).toBeVisible();
  const reservationsBefore = await page.getByTestId("report-total-reservations").textContent();
  const revenueBefore = await page.getByTestId("report-total-revenue").textContent();
  const requestsBeforeLanguageSwitch = reportRequests;
  const english = page.getByRole("button", { name: "English" });
  await english.focus();
  await expect(english).toBeFocused();
  await page.keyboard.press("Enter");

  await expect(english).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("heading", { name: "Operations report" })).toBeVisible();
  await expect(page.getByText("Previous period", { exact: true })).toBeVisible();
  await expect(page.getByRole("columnheader", { name: "Revenue" })).toBeVisible();
  await expect(
    page.getByRole("img", { name: "Revenue and occupancy by hotel chart" }),
  ).toBeVisible();
  await expect(page.getByTestId("report-total-reservations")).toHaveText(
    reservationsBefore ?? "",
  );
  const revenueAfter = await page.getByTestId("report-total-revenue").textContent();
  expect(revenueAfter?.replace(/\D/g, "")).toBe(
    revenueBefore?.replace(/\D/g, ""),
  );
  await expect(page.getByRole("cell", { name: "속초 지점" })).toBeVisible();

  const korean = page.getByRole("button", { name: "한국어" });
  await korean.focus();
  await page.keyboard.press("Space");
  await expect(korean).toHaveAttribute("aria-pressed", "true");
  await expect(page.getByRole("heading", { name: "운영 통계" })).toBeVisible();
  expect(reportRequests).toBe(requestsBeforeLanguageSwitch);
});

test("keeps the report usable at 390px without page-level horizontal overflow", async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations?*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: reportBody() }),
  );
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ROOM_TYPE_BODY }),
  );
  await page.route("**/api/staff/reports/operations/export.xlsx*", (route) =>
    route.fulfill({
      status: 200,
      contentType:
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      headers: {
        "Content-Disposition":
          'attachment; filename="operations-report-2026-09-14-2026-09-20.xlsx"',
      },
      body: Buffer.from("PK-mobile-xlsx", "utf8"),
    }),
  );

  await page.goto("/dashboard/reports");

  await expect(page.getByRole("heading", { name: "운영 통계" })).toBeVisible();
  await expect(
    page.getByRole("img", { name: "지점별 매출 및 점유율 차트" }),
  ).toBeVisible();
  await page.getByRole("button", { name: "English" }).click();
  await expect(page.getByRole("heading", { name: "Operations report" })).toBeVisible();
  const download = page.waitForEvent("download");
  await page.getByTestId("report-xlsx").click();
  await expect((await download).suggestedFilename()).toBe(
    "operations-report-2026-09-14-2026-09-20.xlsx",
  );
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= window.innerWidth,
    ),
  ).toBe(true);
});

test("switches the range with the 30 day preset", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const ranges: string[] = [];
  // /operations/room-types 보다 먼저 등록해 더 좁은 경로가 이긴다.
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: ROOM_TYPE_BODY,
    }),
  );
  await page.route("**/api/staff/reports/operations?*", async (route) => {
    const url = new URL(route.request().url());
    ranges.push(
      `${url.searchParams.get("from")}~${url.searchParams.get("to")}`,
    );
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: reportBody(),
    });
  });

  await page.goto("/dashboard/reports");
  await expect(page.getByTestId("report-range")).toBeVisible();

  await page.getByTestId("report-preset-30").click();

  await expect.poll(() => ranges.length).toBeGreaterThan(1);
  const last = ranges[ranges.length - 1];
  const [from, to] = last.split("~");
  expect(from).not.toBe("");
  expect(to).not.toBe("");
  expect(to).toBe(kstToday());
  expect(Date.parse(from)).toBeLessThan(Date.parse(to));
  const days = (Date.parse(to) - Date.parse(from)) / 86400000;
  expect(days).toBe(29);
});

test("disables stale exports while a changed range is loading", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let reportRequests = 0;
  let releaseChangedRange: (() => void) | undefined;
  const changedRangeRelease = new Promise<void>((resolve) => {
    releaseChangedRange = resolve;
  });
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ROOM_TYPE_BODY }),
  );
  await page.route("**/api/staff/reports/operations?*", async (route) => {
    reportRequests += 1;
    if (reportRequests > 1) await changedRangeRelease;
    await route.fulfill({ status: 200, contentType: "application/json", body: reportBody() });
  });

  await page.goto("/dashboard/reports");
  await expect(page.getByTestId("report-xlsx")).toBeEnabled();

  const csvDisabledOnNextFrame = await page
    .getByTestId("report-preset-30")
    .evaluate(
      (button) =>
        new Promise<boolean>((resolve) => {
          (button as HTMLButtonElement).click();
          requestAnimationFrame(() => {
            resolve(
              (
                document.querySelector(
                  '[data-testid="report-csv"]',
                ) as HTMLButtonElement
              ).disabled,
            );
          });
        }),
    );
  expect(csvDisabledOnNextFrame).toBe(true);
  await expect(page.getByTestId("report-csv")).toBeDisabled();
  await expect(page.getByTestId("report-xlsx")).toBeDisabled();
  await expect.poll(() => reportRequests).toBe(2);

  releaseChangedRange?.();
  await expect(page.getByTestId("report-xlsx")).toBeEnabled();
});

test("tells a branch employee the view is headquarters only", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("BRANCH_STAFF"));
  await page.goto("/dashboard/reports");

  await expect(
    page.getByText("운영 통계는 본사 관리자만 확인할 수 있습니다."),
  ).toBeVisible();
  await expect(page.getByRole("button", { name: "새로고침" })).toBeDisabled();
});

test("shows a recovery notice when the report is unreachable", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations*", (route) =>
    route.fulfill({ status: 500 }),
  );

  await page.goto("/dashboard/reports");

  await expect(
    page.getByText("운영 통계를 불러오지 못했습니다."),
  ).toBeVisible();

  await page.getByRole("button", { name: "English" }).click();
  await expect(page.getByText("Could not load the operations report.")).toBeVisible();
});

test("refreshes the room-type breakdown together with the main report", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations?*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: reportBody() }),
  );

  let roomTypeRequests = 0;
  await page.route("**/api/staff/reports/operations/room-types*", (route) => {
    roomTypeRequests += 1;
    const body = JSON.parse(ROOM_TYPE_BODY) as {
      roomTypes: Array<{ roomTypeName: string }>;
    };
    body.roomTypes[0].roomTypeName =
      roomTypeRequests === 1 ? "새로고침 전 객실" : "새로고침 후 객실";
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(body),
    });
  });

  await page.goto("/dashboard/reports");
  await expect(page.getByRole("cell", { name: "새로고침 전 객실" })).toBeVisible();

  await page.getByRole("button", { name: "새로고침" }).click();

  await expect.poll(() => roomTypeRequests).toBe(2);
  await expect(page.getByRole("cell", { name: "새로고침 후 객실" })).toBeVisible();
});

test("discards a late room-type response after the main report fails", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  let reportRequests = 0;
  await page.route("**/api/staff/reports/operations?*", (route) => {
    reportRequests += 1;
    return route.fulfill(
      reportRequests === 1
        ? { status: 200, contentType: "application/json", body: reportBody() }
        : { status: 500 },
    );
  });

  let releaseRoomType: (() => void) | undefined;
  let markRoomTypeRequested: (() => void) | undefined;
  const roomTypeRequested = new Promise<void>((resolve) => {
    markRoomTypeRequested = resolve;
  });
  const roomTypeRelease = new Promise<void>((resolve) => {
    releaseRoomType = resolve;
  });
  await page.route("**/api/staff/reports/operations/room-types*", async (route) => {
    markRoomTypeRequested?.();
    await roomTypeRelease;
    await route.fulfill({
      status: 200,
      contentType: "application/json",
      body: ROOM_TYPE_BODY,
    });
  });

  await page.goto("/dashboard/reports");
  await expect(page.getByTestId("report-range")).toBeVisible();
  await roomTypeRequested;
  await page.getByRole("button", { name: "새로고침" }).click();
  await expect(page.getByText("운영 통계를 불러오지 못했습니다.")).toBeVisible();

  releaseRoomType?.();
  await expect(page.getByRole("cell", { name: "스탠다드 트윈" })).toHaveCount(0);
  await expect(page.getByText("운영 통계를 불러오지 못했습니다.")).toBeVisible();
});

test("reports an empty range without metrics", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        from: "2026-09-14",
        to: "2026-09-20",
        days: 7,
        hotels: [],
        totals: {
          reservations: 0,
          cancelled: 0,
          noShow: 0,
          expired: 0,
          revenueKrw: 0,
          changeRequestsPending: 0,
          changeRequestsCompleted: 0,
        },
      }),
    }),
  );
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        from: "2026-09-14",
        to: "2026-09-20",
        days: 7,
        hotelId: "11000000-0000-0000-0000-000000000001",
        hotelName: "속초 지점",
        roomTypes: [],
        totals: { reservations: 0, cancelled: 0, noShow: 0, revenueKrw: 0 },
      }),
    }),
  );

  await page.goto("/dashboard/reports");

  await expect(page.getByTestId("report-empty")).toBeVisible();
  await expect(page.getByTestId("room-type-empty")).toBeVisible();
});

test("reports the room type breakdown for the selected hotel", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  const requested: string[] = [];
  await page.route("**/api/staff/reports/operations*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: reportBody(),
    }),
  );
  await page.route(
    "**/api/staff/reports/operations/room-types*",
    async (route) => {
      const url = new URL(route.request().url());
      requested.push(url.searchParams.get("hotelId") ?? "");
      await route.fulfill({
        status: 200,
        contentType: "application/json",
        body: ROOM_TYPE_BODY,
      });
    },
  );

  await page.goto("/dashboard/reports");
  await expect(page.getByTestId("report-range")).toBeVisible();

  // 객실 유형별 매출이 지점 선택기를 따라간다.
  await page
    .getByTestId("report-hotel")
    .selectOption("11000000-0000-0000-0000-000000000002");
  await expect
    .poll(() => requested.at(-1))
    .toBe("11000000-0000-0000-0000-000000000002");

  await expect(page.getByRole("cell", { name: "스탠다드 트윈" })).toBeVisible();
  await expect(page.getByRole("cell", { name: "디럭스 더블" })).toBeVisible();
  await expect(page.getByText("75.0%").first()).toBeVisible();
});

test("offers a csv download of the current report", async ({ page }) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: reportBody(),
    }),
  );
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: ROOM_TYPE_BODY,
    }),
  );

  await page.goto("/dashboard/reports");
  await expect(page.getByTestId("report-range")).toBeVisible();

  const download = page.waitForEvent("download");
  await page.getByTestId("report-csv").click();
  const received = await download;
  const stream = await received.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));
  const text = Buffer.concat(chunks).toString("utf8");

  // 기존 CSV 계약(BOM, CRLF, 따옴표 열, 숫자 원문)을 그대로 유지한다.
  expect(text.charCodeAt(0)).toBe(0xfeff);
  expect(text).toContain(
    '"지점","지역","예약","취소","노쇼","만료","매출","점유율","변경 대기","변경 완료"\r\n',
  );
  expect(text).toContain(
    '"속초 지점","sokcho","6","1","1","0","400000","20.0","1","1"',
  );
  expect(text).toContain(
    '"제주 지점","jeju","2","0","0","0","400000","0.0","0","0"',
  );
  expect(received.suggestedFilename()).toBe(
    "operations-report-2026-09-14-2026-09-20.csv",
  );
});

test("downloads the localized XLSX from the read-only report endpoint", async ({
  page,
}) => {
  await page.addInitScript(seedStaffScript("HQ_ADMIN"));
  await page.route("**/api/staff/reports/operations?*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: reportBody() }),
  );
  await page.route("**/api/staff/reports/operations/room-types*", (route) =>
    route.fulfill({ status: 200, contentType: "application/json", body: ROOM_TYPE_BODY }),
  );

  let exportUrl = "";
  await page.route("**/api/staff/reports/operations/export.xlsx*", async (route) => {
    exportUrl = route.request().url();
    await route.fulfill({
      status: 200,
      contentType:
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
      headers: {
        "Content-Disposition":
          'attachment; filename="operations-report-2026-09-14-2026-09-20.xlsx"',
      },
      body: Buffer.from("PK-xlsx-report-fixture", "utf8"),
    });
  });

  await page.goto("/dashboard/reports");
  await page.getByRole("button", { name: "English" }).click();

  const download = page.waitForEvent("download");
  await page.getByTestId("report-xlsx").click();
  const received = await download;
  const stream = await received.createReadStream();
  const chunks: Buffer[] = [];
  for await (const chunk of stream) chunks.push(Buffer.from(chunk));

  const url = new URL(exportUrl);
  expect(url.pathname).toBe("/api/staff/reports/operations/export.xlsx");
  expect(url.searchParams.get("from")).toBe("2026-09-14");
  expect(url.searchParams.get("to")).toBe("2026-09-20");
  // 화면·CSV와 같은 전 지점 보고서를 내보낸다. 객실 상세 선택기는 범위를 좁히지 않는다.
  expect(url.searchParams.has("hotelId")).toBe(false);
  expect(url.searchParams.get("locale")).toBe("en");
  expect(received.suggestedFilename()).toBe(
    "operations-report-2026-09-14-2026-09-20.xlsx",
  );
  expect(Buffer.concat(chunks).toString("utf8")).toBe("PK-xlsx-report-fixture");
});
