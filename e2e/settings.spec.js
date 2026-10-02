const { test, expect } = require('@playwright/test');
const fs = require('node:fs');

test.describe('settings legacy compatibility', () => {
  test('settings.html redirects to the canonical React Settings route', async ({ page }) => {
    await page.goto('/settings.html');
    await expect(page).toHaveURL(/\/app\/settings\/?$/);
  });

  test('legacy section deep links preserve the requested React section', async ({ page }) => {
    await page.goto('/settings.html?section=hours');
    await expect(page).toHaveURL(/\/app\/settings\/?\?section=hours$/);

    await page.goto('/settings.html?section=team');
    await expect(page).toHaveURL(/\/app\/settings\/?\?section=team$/);
  });

  test('legacy configuration hash falls back to the React business section', async ({ page }) => {
    await page.goto('/settings.html#configuration');
    await expect(page).toHaveURL(/\/app\/settings\/?\?section=business$/);
  });

  test('compatibility shell does not load retired Settings page scripts', async () => {
    const html = fs.readFileSync('src/main/resources/static/settings.html', 'utf8');

    expect(html).toContain('/app/settings');
    expect(html).not.toContain('/app.js');
    expect(html).not.toContain('/settings-page.js');
    expect(html).not.toContain('/phone-provisioning.js');
    expect(html).not.toContain('/payment-sandbox-onboarding.js');
    expect(html).not.toContain('/team-invitations.js');
    expect(html).not.toContain('/schedule-exceptions.js');
  });
});
