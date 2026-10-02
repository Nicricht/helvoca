const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function mockCurrentUser(page, roles = ['BUSINESS_ADMIN']) {
  await page.route('**/api/v1/auth/me', route =>
    route.fulfill(json({
      email: 'owner@negocio.cl',
      roles
    }))
  );
}

test.describe('legacy Home retirement cutover', () => {
  test('authenticated root session moves to canonical React Home', async ({ page }) => {
    await page.addInitScript(() => {
      if (window.location.pathname === '/') {
        sessionStorage.setItem('helvoca_access_token', 'legacy-cutover-e2e');
      }
    });
    await mockCurrentUser(page);

    await page.goto('/');

    await expect(page).toHaveURL(/\/app\/?$/);
  });

  test('successful business login enters React Home instead of the legacy dashboard', async ({ page }) => {
    await mockCurrentUser(page);
    await page.route('**/api/v1/auth/login', route =>
      route.fulfill(json({
        accessToken: 'login-cutover-e2e',
        user: {
          email: 'owner@negocio.cl',
          roles: ['BUSINESS_ADMIN']
        }
      }))
    );

    await page.goto('/');
    await page.locator('#loginTab').click();
    await page.locator('#loginForm [name="email"]').fill('owner@negocio.cl');
    await page.locator('#loginForm [name="password"]').fill('clave-segura-123');
    await page.locator('#loginForm button[type="submit"]').click();

    await expect(page).toHaveURL(/\/app\/?$/);
    await expect.poll(() =>
      page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))
    ).toBe('login-cutover-e2e');
  });

  test('public root remains the unauthenticated entry surface', async ({ page }) => {
    await page.goto('/');

    await expect(page).toHaveURL(/\/$/);
    await expect(page.locator('#authView')).toBeVisible();
    await expect(page.locator('#loginForm')).toBeVisible();
    await expect(page.locator('#registerForm')).toBeVisible();
  });

  test('public root no longer loads retired Home workspace assets', async ({ page }) => {
    await page.goto('/');

    await expect(page.locator('script[src*="home-business.js"]')).toHaveCount(0);
    await expect(page.locator('link[href*="home-business.css"]')).toHaveCount(0);
  });
});
