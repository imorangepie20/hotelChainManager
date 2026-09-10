import { test, expect } from "@playwright/test";

test.describe("Components — Tooltip detail", () => {
  test("renders breadcrumb, header, and variant cards", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const res = await page.goto("/components/tooltip");
    expect(res?.status(), "status").toBeLessThan(400);
    await expect(
      page.getByRole("heading", { name: "Tooltip", exact: true })
    ).toBeVisible();
    // Check variant card titles
    await expect(page.getByText("Basic", { exact: true }).first()).toBeVisible();
    await expect(
      page.getByText("Positions", { exact: true }).first()
    ).toBeVisible();
    await expect(
      page.getByText("With shortcut", { exact: true }).first()
    ).toBeVisible();
    expect(errors, "pageerrors").toEqual([]);
  });

  test("Basic variant — hovering trigger reveals tooltip", async ({ page }) => {
    await page.goto("/components/tooltip");
    await page
      .locator('[data-testid="tooltip-basic"]')
      .getByRole("button")
      .hover();
    await expect(page.getByText("Add to library").first()).toBeVisible({
      timeout: 5000,
    });
  });

  test("catalog Tooltip card links to the detail page", async ({ page }) => {
    await page.goto("/components");
    await page.locator('a[href="/components/tooltip"]').first().click();
    await expect(page).toHaveURL(/\/components\/tooltip$/);
    await expect(
      page.getByRole("heading", { name: "Tooltip", exact: true })
    ).toBeVisible();
  });
});
