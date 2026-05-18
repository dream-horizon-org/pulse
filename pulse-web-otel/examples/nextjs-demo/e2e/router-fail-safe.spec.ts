/**
 * Router integration fail-safe — React `PulseRouterEvents` without `BrowserRouter`
 * must not crash the Next.js demo shell.
 */
import { test, expect } from "./fixture";

test.describe("router integration fail-safe", () => {
  test("React PulseRouterEvents without BrowserRouter keeps page visible", async ({
    page,
  }) => {
    await page.goto("/e2e-react-router-misconfig");
    await expect(
      page.getByTestId("e2e-react-router-misconfig-root"),
    ).toBeVisible();
    await expect(
      page.getByRole("heading", { name: "React-router adapter probe" }),
    ).toBeVisible();
  });
});
