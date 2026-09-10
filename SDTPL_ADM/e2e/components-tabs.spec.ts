import { test, expect } from "@playwright/test";

test.describe("Components — Tabs detail", () => {
  test("renders breadcrumb, header, and variant cards", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const res = await page.goto("/components/tabs");
    expect(res?.status(), "status").toBeLessThan(400);
    await expect(
      page.getByRole("heading", { name: "Tabs", exact: true })
    ).toBeVisible();
    // Check a selection of variant card titles
    await expect(page.getByText("Basic", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Vertical", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Settings", { exact: true }).first()).toBeVisible();
    expect(errors, "pageerrors").toEqual([]);
  });

  test("Basic variant — clicking Analytics tab shows analytics panel", async ({ page }) => {
    await page.goto("/components/tabs");

    // Scope to the Basic variant via data-testid
    const basic = page.locator('[data-testid="tabs-basic"]');
    await expect(basic).toBeVisible();

    // Overview panel content is visible by default
    await expect(
      basic.getByText(/summary of your project/i)
    ).toBeVisible();

    // Analytics panel content is NOT visible initially
    await expect(
      basic.getByText(/traffic trends/i)
    ).not.toBeVisible();

    // Click the Analytics tab within scope
    await basic.getByRole("tab", { name: "Analytics" }).click();

    // Analytics panel content is now visible
    await expect(
      basic.getByText(/traffic trends/i)
    ).toBeVisible();

    // Overview panel content is now hidden
    await expect(
      basic.getByText(/summary of your project/i)
    ).not.toBeVisible();
  });

  test("catalog Tabs card links to the detail page", async ({ page }) => {
    await page.goto("/components");
    await page.locator('a[href="/components/tabs"]').first().click();
    await expect(page).toHaveURL(/\/components\/tabs$/);
    await expect(
      page.getByRole("heading", { name: "Tabs", exact: true })
    ).toBeVisible();
  });
});
