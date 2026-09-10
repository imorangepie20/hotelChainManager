import { test, expect } from "@playwright/test";

test.describe("Components — Textarea detail", () => {
  test("renders breadcrumb, header, and variant cards", async ({ page }) => {
    const errors: string[] = [];
    page.on("pageerror", (e) => errors.push(e.message));
    const res = await page.goto("/components/textarea");
    expect(res?.status(), "status").toBeLessThan(400);
    await expect(
      page.getByRole("heading", { name: "Textarea", exact: true })
    ).toBeVisible();
    // Variant card titles
    await expect(
      page.getByText("Basic", { exact: true }).first()
    ).toBeVisible();
    await expect(
      page.getByText("Character counter", { exact: true }).first()
    ).toBeVisible();
    await expect(
      page.getByText("Avatar comment", { exact: true }).first()
    ).toBeVisible();
    expect(errors, "pageerrors").toEqual([]);
  });

  test("Character counter variant — updates count on typing", async ({
    page,
  }) => {
    await page.goto("/components/textarea");

    // Scope to the character counter variant
    const wrapper = page.locator('[data-testid="textarea-counter"]');
    await expect(wrapper).toBeVisible();

    // Type into the textarea within the counter wrapper
    const textarea = wrapper.getByRole("textbox");
    await textarea.fill("Hello");

    // The count output should show "5"
    const count = wrapper.locator('[data-testid="textarea-count"]');
    await expect(count).toHaveText("5");
  });

  test("catalog Textarea card links to the detail page", async ({ page }) => {
    await page.goto("/components");
    await page.locator('a[href="/components/textarea"]').first().click();
    await expect(page).toHaveURL(/\/components\/textarea$/);
    await expect(
      page.getByRole("heading", { name: "Textarea", exact: true })
    ).toBeVisible();
  });
});
