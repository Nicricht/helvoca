const { test, expect } = require('@playwright/test');

const json = body => ({
  status: 200,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

test('platform admin provisions a tenant and hands onboarding to an invited business admin', async ({ page }) => {
  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'platform-token'));

  let submitted = null;
  await page.route('**/api/v1/auth/me', route => route.fulfill(json({
    userId: 'platform-user',
    email: 'platform@recepvoz.cl',
    roles: ['PLATFORM_ADMIN'],
    businessId: ''
  })));

  await page.route('**/api/v1/platform/businesses', async route => {
    submitted = route.request().postDataJSON();
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        businessId: '11111111-2222-3333-4444-555555555555',
        businessName: 'Clínica Norte',
        timezone: 'America/Santiago',
        language: 'es',
        adminName: 'Ana Pérez',
        adminEmail: 'ana@clinica.cl',
        invitationId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
        invitationStatus: 'PENDING',
        invitationExpiresAt: '2026-09-29T12:00:00Z',
        invitePath: '/invite.html?businessId=11111111-2222-3333-4444-555555555555&token=one-time-e2e',
        onboardingPath: '/'
      })
    });
  });

  await page.goto('/platform.html');

  await page.locator('input[name="businessName"]').fill('Clínica Norte');
  await page.locator('input[name="timezone"]').fill('America/Santiago');
  await page.locator('select[name="language"]').selectOption('es');
  await page.locator('input[name="humanTransferPhone"]').fill('+56911112222');
  await page.locator('input[name="adminName"]').fill('Ana Pérez');
  await page.locator('input[name="adminEmail"]').fill('ana@clinica.cl');
  await page.locator('#platformProvisionSubmit').click();

  await expect.poll(() => submitted).not.toBeNull();
  expect(submitted).toEqual({
    businessName: 'Clínica Norte',
    timezone: 'America/Santiago',
    language: 'es',
    humanTransferPhone: '+56911112222',
    adminName: 'Ana Pérez',
    adminEmail: 'ana@clinica.cl'
  });
  expect(submitted.password).toBeUndefined();

  await expect(page.locator('#platformProvisionResult')).toBeVisible();
  await expect(page.locator('#platformResultBusiness')).toContainText('Clínica Norte');
  await expect(page.locator('#platformResultAdmin')).toContainText('ana@clinica.cl');
  await expect(page.locator('#platformResultStatus')).toContainText('invitación pendiente');
  await expect(page.locator('#platformInviteUrl')).toHaveValue(
    /\/invite\.html\?businessId=11111111-2222-3333-4444-555555555555&token=one-time-e2e/
  );
  await expect(page.locator('#platformProvisionMessage')).toContainText('Negocio creado');
});
