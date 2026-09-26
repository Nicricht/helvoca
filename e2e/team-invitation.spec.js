const { test, expect } = require('@playwright/test');

const json = body => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) });

test('invited teammate creates their own password and enters RecepVoz', async ({ page }) => {
  const businessId = '11111111-1111-1111-1111-111111111111';
  const token = 'test-token';
  let accepted = null;

  await page.route('**/api/v1/auth/invitations/**', async route => {
    const request = route.request();
    if (request.method() === 'POST') {
      accepted = request.postDataJSON();
      await route.fulfill(json({
        accessToken: 'invite-jwt',
        tokenType: 'Bearer',
        expiresInSeconds: 3600,
        user: {
          id: '22222222-2222-2222-2222-222222222222',
          businessId,
          name: 'Camila Soto',
          email: 'camila@negocio.cl',
          roles: ['OPERATOR']
        }
      }));
      return;
    }
    await route.fulfill(json({
      id: '33333333-3333-3333-3333-333333333333',
      businessId,
      businessName: 'Negocio E2E',
      name: 'Camila Soto',
      email: 'camila@negocio.cl',
      role: 'OPERATOR',
      expiresAt: '2026-09-29T12:00:00Z',
      status: 'PENDING',
      invitePath: null
    }));
  });

  await page.goto(`/invite.html?businessId=${businessId}&token=${token}`);

  await expect(page.locator('#inviteTitle')).toHaveText('Únete a Negocio E2E');
  await expect(page.locator('#inviteMeta')).toContainText('Camila Soto');
  await expect(page.locator('#inviteMeta')).toContainText('Operador');

  await page.locator('input[name="password"]').fill('UnaClaveMuySegura123');
  await page.locator('input[name="confirmPassword"]').fill('UnaClaveMuySegura123');
  await page.getByRole('button', { name: 'Aceptar invitación' }).click();

  await expect.poll(() => accepted).toEqual({ password: 'UnaClaveMuySegura123' });
  await expect.poll(() => page.evaluate(() => sessionStorage.getItem('helvoca_access_token')))
    .toBe('invite-jwt');
  await expect(page.locator('#inviteMessage')).toContainText('Invitación aceptada');
});
