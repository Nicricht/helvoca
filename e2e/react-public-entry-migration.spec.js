const { test, expect } = require('@playwright/test');
const fs = require('node:fs');
const path = require('node:path');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test.describe('React public entry and authentication migration', () => {
  test('root is compatibility-only and opens the canonical React auth route', async ({ page }) => {
    const requests = [];
    page.on('request', request => requests.push(new URL(request.url()).pathname));

    await page.goto('/');

    await expect(page).toHaveURL(/\/app\/auth\/?$/);
    await expect(page.locator('[data-auth-page="recepvoz"]')).toBeVisible();
    await expect(page.getByRole('heading', { level: 1, name: 'No pierdas otra llamada.' })).toBeVisible();

    expect(requests).not.toContain('/app.js');
    expect(requests).not.toContain('/commercial-status.js');
    expect(requests).not.toContain('/business-activation-guide.js');
    expect(requests).not.toContain('/ux-simplification.js');
    expect(requests).not.toContain('/first-user-ux-v2.js');
    expect(requests).not.toContain('/phone-provisioning.js');
  });

  test('registration preserves the backend payload and enters React Home', async ({ page }) => {
    let registerPayload = null;
    let registerCalls = 0;

    await page.route('**/api/v1/auth/register', async route => {
      registerCalls += 1;
      registerPayload = route.request().postDataJSON();
      await route.fulfill(json({
        accessToken: 'react-register-token',
        user: { email: 'owner@nuevo.cl', roles: ['BUSINESS_OWNER', 'BUSINESS_ADMIN'] }
      }));
    });

    await page.goto('/app/auth');
    await page.getByLabel('Nombre del negocio').fill('Negocio Nuevo');
    await page.getByLabel('Correo').fill('owner@nuevo.cl');
    await page.getByLabel('Contraseña').fill('corta');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    expect(registerCalls).toBe(0);

    await page.getByLabel('Contraseña').fill('clave-segura-123');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    await expect.poll(() => registerCalls).toBe(1);
    expect(registerPayload).toMatchObject({
      adminName: 'Negocio Nuevo',
      businessName: 'Negocio Nuevo',
      email: 'owner@nuevo.cl',
      password: 'clave-segura-123',
      humanTransferPhone: null
    });
    expect(typeof registerPayload.timezone).toBe('string');
    expect(registerPayload.timezone.length).toBeGreaterThan(0);
    expect(registerPayload.language).toMatch(/^[a-z]{2,3}$/);

    await expect(page).toHaveURL(/\/app\/?$/);
    await expect.poll(() =>
      page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))
    ).toBe('react-register-token');
  });

  test('login keeps an error visible and enters React Home after valid credentials', async ({ page }) => {
    let loginCalls = 0;

    await page.route('**/api/v1/auth/login', async route => {
      loginCalls += 1;
      if (loginCalls === 1) {
        await route.fulfill(json({ message: 'Credenciales inválidas.' }, 401));
        return;
      }
      await route.fulfill(json({
        accessToken: 'react-login-token',
        user: { email: 'owner@negocio.cl', roles: ['BUSINESS_ADMIN'] }
      }));
    });

    await page.goto('/app/auth');
    await page.getByRole('tab', { name: 'Ingresar' }).click();
    await page.getByLabel('Correo').fill('owner@negocio.cl');
    await page.getByLabel('Contraseña').fill('incorrecta');
    await page.getByRole('button', { name: 'Ingresar a RecepVoz' }).click();

    await expect(page.getByRole('alert')).toContainText('Credenciales inválidas');
    await expect(page.getByRole('tab', { name: 'Ingresar' })).toHaveAttribute('aria-selected', 'true');

    await page.getByLabel('Contraseña').fill('clave-segura-123');
    await page.getByRole('button', { name: 'Ingresar a RecepVoz' }).click();

    await expect.poll(() => loginCalls).toBe(2);
    await expect(page).toHaveURL(/\/app\/?$/);
    await expect.poll(() =>
      page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))
    ).toBe('react-login-token');
  });

  test('platform admin login preserves the platform destination', async ({ page }) => {
    await page.route('**/api/v1/auth/login', route => route.fulfill(json({
      accessToken: 'platform-login-token',
      user: { email: 'platform@recepvoz.cl', roles: ['PLATFORM_ADMIN'] }
    })));

    await page.goto('/app/auth');
    await page.getByRole('tab', { name: 'Ingresar' }).click();
    await page.getByLabel('Correo').fill('platform@recepvoz.cl');
    await page.getByLabel('Contraseña').fill('clave-segura-123');
    await page.getByRole('button', { name: 'Ingresar a RecepVoz' }).click();

    await expect(page).toHaveURL(/\/app\/platform\/?$/);
    await expect.poll(() =>
      page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))
    ).toBe('platform-login-token');
  });

  test('existing authenticated session leaves the public auth surface', async ({ page }) => {
    await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'existing-auth-token'));
    await page.route('**/api/v1/auth/me', route => route.fulfill(json({
      email: 'owner@negocio.cl',
      roles: ['BUSINESS_ADMIN']
    })));

    await page.goto('/app/auth');

    await expect(page).toHaveURL(/\/app\/?$/);
  });

  for (const viewport of [
    { width: 1440, height: 900 },
    { width: 768, height: 1024 },
    { width: 390, height: 844 }
  ]) {
    test('public React auth remains contained at ' + viewport.width + 'px', async ({ page }) => {
      await page.setViewportSize(viewport);
      await page.goto('/app/auth');

      await expect(page.locator('[data-auth-page="recepvoz"]')).toBeVisible();
      const layout = await page.evaluate(() => ({
        clientWidth: document.documentElement.clientWidth,
        scrollWidth: document.documentElement.scrollWidth
      }));
      expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1);
    });
  }

  test('reduced motion keeps authentication fully usable', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/app/auth');

    await expect(page.getByRole('button', { name: 'Crear cuenta' })).toBeVisible();
    await page.getByRole('tab', { name: 'Ingresar' }).click();
    await expect(page.getByRole('button', { name: 'Ingresar a RecepVoz' })).toBeVisible();
  });

  test('source contract declares public Auth outside protection and retires the legacy root console', async () => {
    const root = path.resolve(__dirname, '..');
    const app = fs.readFileSync(path.join(root, 'frontend/src/app/App.tsx'), 'utf8');
    const boundary = fs.readFileSync(path.join(root, 'frontend/src/app/AuthBoundary.tsx'), 'utf8');
    const client = fs.readFileSync(path.join(root, 'frontend/src/api/client.ts'), 'utf8');
    const vite = fs.readFileSync(path.join(root, 'frontend/vite.config.ts'), 'utf8');
    const controller = fs.readFileSync(
      path.join(root, 'src/main/java/cl/helvoca/frontend/ReactFrontendController.java'),
      'utf8'
    );
    const legacyRoot = fs.readFileSync(path.join(root, 'src/main/resources/static/index.html'), 'utf8');

    const authRoute = app.indexOf('path="/auth"');
    const protectedOutlet = app.indexOf('<Route element={<ProtectedOutlet />}>');

    expect(app).toContain('AuthPage');
    expect(authRoute).toBeGreaterThan(-1);
    expect(protectedOutlet).toBeGreaterThan(-1);
    expect(authRoute).toBeLessThan(protectedOutlet);

    expect(boundary).toContain('window.location.replace("/app/auth")');
    expect(client).toContain('window.location.replace("/app/auth")');
    expect(vite).toContain('authDir');
    expect(vite).toContain('static/app/auth/');
    expect(controller).toContain('"/app/auth"');

    expect(legacyRoot).toContain('/app/auth');
    expect(legacyRoot).not.toContain('/app.js');
    expect(legacyRoot).not.toContain('/commercial-status.js');
    expect(legacyRoot).not.toContain('id="dashboardView"');
  });
});
