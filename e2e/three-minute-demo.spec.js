const { test, expect } = require('@playwright/test');

test('three-minute sales demo is public, safe and ends with one next step', async ({ page }) => {
  const apiCalls = [];
  page.on('request', request => {
    if (request.url().includes('/api/v1/')) apiCalls.push(request.url());
  });

  await page.goto('/demo.html?rubro=sushi');

  await expect(page.getByRole('heading', { level: 1 })).toContainText('3 minutos');
  await expect(page.getByText(/demo segura/i)).toBeVisible();
  await expect(page.getByText(/no realiza llamadas reales/i)).toBeVisible();
  await expect(page.getByText(/Sushi/i)).toBeVisible();

  await page.getByRole('button', { name: /Empezar demo/i }).click();
  await expect(page.locator('[data-demo-step="1"]')).toBeVisible();

  await page.getByRole('button', { name: /Siguiente/i }).click();
  await expect(page.locator('[data-demo-step="2"]')).toBeVisible();

  await page.getByRole('button', { name: /Siguiente/i }).click();
  await expect(page.locator('[data-demo-step="3"]')).toBeVisible();

  await page.getByRole('button', { name: /Ver resultado/i }).click();
  await expect(page.locator('#demoOutcome')).toBeVisible();
  await expect(page.getByText(/¿Quieres que lo probemos con los datos de tu negocio\?/i)).toBeVisible();
  await expect(page.getByRole('link', { name: /Ver demo completa/i })).toHaveAttribute('href', '/simulator.html');

  expect(apiCalls).toEqual([]);
});

test('three-minute sales demo supports the core business presets', async ({ page }) => {
  await page.goto('/demo.html');

  for (const label of ['Sushi', 'Pizzería', 'Carnicería', 'Peluquería', 'Veterinaria', 'Clínica', 'Taller']) {
    await expect(page.getByRole('button', { name: label, exact: true })).toBeVisible();
  }
});

test('three-minute sales demo stays usable on mobile', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/demo.html?rubro=peluqueria');

  await expect(page.getByRole('button', { name: /Empezar demo/i })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
});
