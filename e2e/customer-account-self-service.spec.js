const { test, expect } = require('@playwright/test');

const TOKEN = 'A'.repeat(43);
const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test.describe('business account self-service React journey', () => {
  test('unknown-account request confirms generically without revealing existence', async ({ page }) => {
    let requests = 0;
    await page.route('**/api/v1/auth/password-reset/request', route => {
      requests += 1;
      expect(route.request().postDataJSON()).toEqual({ email: 'unknown@example.test' });
      return route.fulfill(json({}, 202));
    });

    await page.goto('/app/password-reset');
    await expect(page.getByRole('heading', { name: 'Recupera tu acceso' })).toBeVisible();
    await page.getByLabel('Correo de la cuenta').fill('unknown@example.test');
    await page.getByRole('button', { name: 'Enviar enlace' }).click();
    await expect(page.getByRole('status')).toContainText(/si existe una cuenta/i);
    expect(requests).toBe(1);
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
  });

  test('one-use secret is removed from browser URL before password confirmation', async ({ page }) => {
    let confirmations = 0;
    await page.route('**/api/v1/auth/password-reset/confirm', route => {
      confirmations += 1;
      expect(route.request().url()).not.toContain(TOKEN);
      expect(route.request().postDataJSON()).toEqual({ token: TOKEN, newPassword: 'updated-password-123' });
      return route.fulfill({ status: 204 });
    });

    await page.goto('/app/password-reset#token=' + TOKEN);
    await expect(page).toHaveURL(/\/app\/password-reset\/?$/);
    await expect(page.getByRole('heading', { name: 'Crea una nueva contraseña' })).toBeVisible();
    await page.getByLabel('Nueva contraseña').fill('updated-password-123');
    await page.getByLabel('Repite la contraseña').fill('updated-password-123');
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page.getByRole('status')).toContainText(/contraseña.*actualizada/i);
    expect(confirmations).toBe(1);
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
  });

  test('wrong confirmation and expired link do not submit an extra request', async ({ page }) => {
    let confirmations = 0;
    await page.route('**/api/v1/auth/password-reset/confirm', route => {
      confirmations += 1;
      return route.fulfill(json({ message: 'Invalid or expired token' }, 400));
    });
    await page.goto('/app/password-reset#token=' + TOKEN);
    await page.getByLabel('Nueva contraseña').fill('updated-password-123');
    await page.getByLabel('Repite la contraseña').fill('different-password-123');
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page.getByRole('alert')).toContainText(/no coinciden/i);
    expect(confirmations).toBe(0);

    await page.getByLabel('Repite la contraseña').fill('updated-password-123');
    await page.getByRole('button', { name: 'Guardar contraseña' }).click();
    await expect(page.getByRole('alert')).toContainText(/expirado|inválido/i);
    expect(confirmations).toBe(1);
  });

  test('opt-in registration produces pending verification, never session storage', async ({ page }) => {
    await page.route('**/api/v1/auth/register', route => {
      expect(route.request().postDataJSON()).toMatchObject({
        email: 'owner@example.test', businessName: 'Negocio E2E'
      });
      return route.fulfill(json({ status: 'EMAIL_VERIFICATION_REQUIRED' }, 202));
    });
    await page.goto('/app/auth');
    await page.locator('#registerBusinessName').fill('Negocio E2E');
    await page.locator('#registerEmail').fill('owner@example.test');
    await page.locator('#registerPassword').fill('owner-password-123');
    await page.locator('#registerForm button[type="submit"]').click();
    await expect(page.getByText(/revisa tu correo/i)).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem('helvoca_access_token'))).toBeNull();
    await expect(page).toHaveURL(/\/app\/auth\/?$/);
  });

  test('verification secret is scrubbed and account can request resend without enumeration', async ({ page }) => {
    let confirmations = 0;
    await page.route('**/api/v1/auth/email/confirm', route => {
      confirmations += 1;
      expect(route.request().url()).not.toContain(TOKEN);
      expect(route.request().postDataJSON()).toEqual({ token: TOKEN });
      return route.fulfill({ status: 204 });
    });
    await page.goto('/app/verify-email#token=' + TOKEN);
    await expect(page).toHaveURL(/\/app\/verify-email\/?$/);
    await page.getByRole('button', { name: 'Confirmar correo' }).click();
    await expect(page.getByRole('status')).toContainText(/correo confirmado/i);
    expect(confirmations).toBe(1);

    await page.route('**/api/v1/auth/email/resend', route => route.fulfill(json({}, 202)));
    await page.goto('/app/verify-email');
    await page.getByLabel('Correo de la cuenta').fill('unknown@example.test');
    await page.getByRole('button', { name: 'Reenviar confirmación' }).click();
    await expect(page.getByRole('status')).toContainText(/si existe una cuenta/i);
  });

  test('self-service forms stay within viewport on mobile', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    for (const path of ['/app/password-reset', '/app/verify-email']) {
      await page.goto(path);
      await expect(page.getByLabel('Correo de la cuenta')).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth + 1)).toBe(true);
    }
  });
});
