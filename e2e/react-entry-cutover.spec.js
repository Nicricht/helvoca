const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mockAuthenticatedUser(page) {
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    email: 'owner@negocio.cl',
    roles: ['BUSINESS_ADMIN'],
    permissions: []
  })));
}

test.describe('React authenticated entry cutover', () => {
  test('an existing authenticated session entering root is redirected to the React app', async ({ page }) => {
    await page.addInitScript(() => {
      sessionStorage.setItem('helvoca_access_token', 'existing-session');
    });
    await mockAuthenticatedUser(page);

    await page.goto('/');

    await expect(page).toHaveURL(/\/app$/);
  });

  test('a successful non-platform login enters the React app instead of the legacy dashboard', async ({ page }) => {
    await mockAuthenticatedUser(page);
    await page.route('**/api/v1/auth/login', route => route.fulfill(json({
      accessToken: 'fresh-session',
      user: {
        email: 'owner@negocio.cl',
        roles: ['BUSINESS_ADMIN']
      }
    })));

    await page.goto('/');
    await page.locator('#loginTab').click();
    await page.locator('#loginForm input[name="email"]').fill('owner@negocio.cl');
    await page.locator('#loginForm input[name="password"]').fill('super-secret-password');
    await page.locator('#loginForm button[type="submit"]').click();

    await expect(page).toHaveURL(/\/app$/);
  });
});
