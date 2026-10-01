const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

test('three-minute sales demo is public, safe and ends with one next step', async ({ page }) => {
  const apiCalls = [];
  page.on('request', request => {
    if (request.url().includes('/api/v1/')) apiCalls.push(request.url());
  });

  await page.goto('/demo.html?rubro=sushi');

  await expect(page.getByRole('heading', { level: 1 })).toContainText('3 minutos');
  await expect(page.locator('.demo-safety')).toContainText('Demo segura');
  await expect(page.locator('.demo-safety')).toContainText('No realiza llamadas reales');
  await expect(page.locator('#presetTitle')).toContainText('Sushi');

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


test('three-minute sales demo emits exact-head visual evidence', async ({ page }) => {
  const head = String(process.env.VISUAL_EVIDENCE_SHA || 'local').slice(0, 12);
  const dir = path.join('test-results', 'visual-evidence', head);
  fs.mkdirSync(dir, { recursive: true });

  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 390, height: 844 }
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/demo.html?rubro=sushi');
    await page.screenshot({
      path: path.join(dir, `demo-3min-${viewport.width}x${viewport.height}.png`),
      fullPage: false,
      animations: 'disabled'
    });
  }
});
