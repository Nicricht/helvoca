const { test, expect } = require('@playwright/test');

test('sales landing exposes pricing and signup paths', async ({ page }) => {
  await page.goto('/sales.html');
  await expect(page).toHaveTitle(/RecepVoz/);
  await expect(page.getByRole('heading', { level: 1 })).toContainText('Que una llamada o un WhatsApp sin responder');
  await expect(page.getByRole('link', { name: 'Probar con mi negocio' })).toHaveAttribute('href', '/');
  await expect(page.getByRole('link', { name: 'Planes desde $24.990' })).toHaveAttribute('href', '/pricing.html');
});
