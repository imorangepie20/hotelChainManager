import { test, expect } from "@playwright/test";

test.describe("Components — Toggle detail", () => {
  test("renders breadcrumb, header, and variant cards", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const res = await page.goto("/components/toggle");
    expect(res?.status(), "status").toBeLessThan(400);
    await expect(
      page.getByRole("heading", { name: "Toggle", exact: true })
    ).toBeVisible();
    // Variant card titles
    await expect(page.getByText("Basic", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Outline", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Toggle group", { exact: true }).first()).toBeVisible();
    expect(errors, "pageerrors").toEqual([]);
  });

  test("Basic variant — toggle switches aria-pressed on click", async ({ page }) => {
    await page.goto("/components/toggle");

    const scope = page.locator('[data-testid="toggle-basic"]');
    await expect(scope).toBeVisible();

    const btn = scope.getByRole("button", { name: "Toggle bold" });
    await expect(btn).toHaveAttribute("aria-pressed", "false");
    await btn.click();
    await expect(btn).toHaveAttribute("aria-pressed", "true");
  });

  test("catalog Toggle card links to the detail page", async ({ page }) => {
    await page.goto("/components");
    await page.locator('a[href="/components/toggle"]').first().click();
    await expect(page).toHaveURL(/\/components\/toggle$/);
    await expect(
      page.getByRole("heading", { name: "Toggle", exact: true })
    ).toBeVisible();
  });
});
