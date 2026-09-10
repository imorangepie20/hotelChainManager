import { expect, test } from "@playwright/test";

test("shows the staff sign-in screen before a session is available", async ({ page }) => {
  await page.goto("/");

  await expect(page.getByText("운영 관리자 로그인")).toBeVisible();
  await expect(page.getByLabel("이메일")).toBeVisible();
  await expect(page.getByLabel("비밀번호")).toBeVisible();
  await expect(page.getByRole("button", { name: "로그인" })).toBeVisible();
});
