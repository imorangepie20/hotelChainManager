import { test, expect } from "@playwright/test";

test.describe("Components — Switch detail", () => {
  test("renders breadcrumb, header, and variant cards", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const res = await page.goto("/components/switch");
    expect(res?.status(), "status").toBeLessThan(400);
    await expect(page.getByRole("heading", { name: "Switch" })).toBeVisible();
    // Check variant card titles
    await expect(page.getByText("Basic", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Colored", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Settings list", { exact: true }).first()).toBeVisible();
    expect(errors, "pageerrors").toEqual([]);
  });

  test("Airplane mode — clicking the switch toggles it on", async ({ page }) => {
    await page.goto("/components/switch");

    // Scope to the airplane mode variant
    const card = page.locator('[data-testid="switch-airplane"]');
    await expect(card).toBeVisible();

    const sw = card.getByRole("switch");
    await expect(sw).not.toBeChecked();

    await sw.click();

    await expect(sw).toBeChecked();
  });

  test("catalog Switch card links to the detail page", async ({ page }) => {
    await page.goto("/components");
    await page.locator('a[href="/components/switch"]').first().click();
    await expect(page).toHaveURL(/\/components\/switch$/);
    await expect(page.getByRole("heading", { name: "Switch" })).toBeVisible();
  });
});
