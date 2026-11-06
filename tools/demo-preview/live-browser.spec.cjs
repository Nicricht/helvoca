const { test, expect } = require("@playwright/test");

const cases = [
  ["restaurant", "Agrega los servicios del negocio", "Ir a Servicios", "Servicios", "services"],
  ["veterinary", "Define los horarios de atención", "Ir a Horarios", "Horarios", "hours"],
  ["hardware_store", "Completa los datos del negocio", "Ir a Negocio", "Negocio", "business"],
  ["clinic", "Base configurada: revisa la recepcionista", "Ir a Recepcionista IA", "Recepcionista IA", "receptionist"]
];

test.describe("public read-only onboarding preview, synthetic accounts only", () => {
  for (const [sector, description, action, tab, section] of cases) {
    test(`${sector}: real React guided next step remains read-only`, async ({ page }) => {
      const apiWrites = [];
      page.on("request", req => {
        if (new URL(req.url()).pathname.startsWith("/api/")
          && !["GET", "HEAD", "OPTIONS"].includes(req.method())) {
          apiWrites.push(req.method() + " " + req.url());
        }
      });

      await page.goto(`/preview?sector=${sector}`);
      await expect(page).toHaveURL(/\/app\/settings$/);
      const next = page.getByTestId("settings-next-step");
      await expect(next).toContainText(description);
      await expect(page.getByRole("note", { name: "Vista previa de datos ficticios" })).toContainText("DATOS FICTICIOS");
      await expect(page.getByRole("note", { name: "Vista previa de datos ficticios" })).toContainText("SIN LLAMADAS");
      const button = next.getByRole("button", { name: action, exact: true });
      await expect(button).toBeVisible();
      await button.click();
      await expect(page.getByRole("tab", { name: tab, exact: true }))
        .toHaveAttribute("aria-selected", "true");
      await expect(page).toHaveURL(new RegExp("section=" + section));
      expect(apiWrites).toEqual([]);
      if (sector === "clinic") {
        await expect(next).toContainText("no certifica llamadas ni WhatsApp reales");
      }
    });
  }
});
