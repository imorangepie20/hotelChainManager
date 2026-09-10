import { test, expect } from "@playwright/test";

test.describe("Components — Table detail", () => {
  test("renders breadcrumb, header, variant titles, and seeded data", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const res = await page.goto("/components/table");
    expect(res?.status(), "status").toBeLessThan(400);
    await expect(page.getByRole("heading", { name: "Table", exact: true })).toBeVisible();
    // Variant section titles
    await expect(page.getByText("Basic", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("With footer (totals)", { exact: true }).first()).toBeVisible();
    await expect(page.getByText("Selectable rows", { exact: true }).first()).toBeVisible();
    // Seeded data values visible in the table
    await expect(page.getByText("Olivia Martin").first()).toBeVisible();
    await expect(page.getByText("INV001").first()).toBeVisible();
    expect(errors, "pageerrors").toEqual([]);
  });

  test("Selectable rows — select-all checkbox selects all 5 rows", async ({ page }) => {
    await page.goto("/components/table");

    // Scope to the selectable variant via data-testid
    const section = page.locator('[data-testid="table-selectable"]');
    await expect(section).toBeVisible();

    // Initially 0 selected
    await expect(section.getByText("0 of 5 row(s) selected.")).toBeVisible();

    // Click the header select-all checkbox
    const selectAllCheckbox = section
      .getByRole("checkbox", { name: "Select all rows" })
      .first();
    await selectAllCheckbox.click();

    // All 5 rows should now be selected
    await expect(section.getByText("5 of 5 row(s) selected.")).toBeVisible();
  });

  test("catalog Table card links to the detail page", async ({ page }) => {
    await page.goto("/components");
    await page.locator('a[href="/components/table"]').first().click();
    await expect(page).toHaveURL(/\/components\/table$/);
    await expect(page.getByRole("heading", { name: "Table", exact: true })).toBeVisible();
  });
});
