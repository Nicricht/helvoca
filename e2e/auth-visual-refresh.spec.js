const { test, expect } = require('@playwright/test');

test.describe('public authentication visual refresh', () => {
  test('presents the product before asking the visitor to register', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('#authView')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'No pierdas otra llamada.' })).toBeVisible();
    await expect(page.locator('.rv-hero-emphasis')).toHaveText('de tu negocio');
    await expect(page.locator('.rv-auth-visual img')).toHaveAttribute('src', '/recepvoz-auth-hero.svg');
    await expect(page.locator('.rv-benefit-row')).toContainText('Atiende llamadas 24/7');
    await expect(page.locator('.rv-benefit-row')).toContainText('Agenda citas');
    await expect(page.locator('.rv-benefit-row')).toContainText('WhatsApp Business');
    await expect(page.locator('.rv-auth-feature-strip')).toBeVisible();
    await expect(page.locator('#registerForm')).toBeVisible();
  });

  test('keeps the public entry surface contained on desktop, tablet and mobile', async ({ page }) => {
    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 900 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/');
      await expect(page.locator('#registerForm')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
    }
  });

  test('motion remains nonessential when reduced motion is requested', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/');

    const animationName = await page.locator('.rv-auth-visual').evaluate(element => getComputedStyle(element).animationName);
    expect(animationName).toBe('none');
  });
});
