const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function bootReactSettings(page) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    'settings-full-cutover'
  );

  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname;
    if (route.request().method() !== 'GET') {
      return route.fulfill(json({ message: 'mutation blocked by cutover RED' }, 501));
    }
    if (path === '/api/v1/auth/me') {
      return route.fulfill(json({
        email: 'admin@demo.cl',
        roles: ['BUSINESS_ADMIN'],
        permissions: ['BUSINESS_READ', 'BUSINESS_MANAGE']
      }));
    }
    if (path === '/api/v1/business') {
      return route.fulfill(json({
        name: 'Negocio Cutover',
        timezone: 'America/Santiago',
        language: 'es'
      }));
    }
    if (path === '/api/v1/business/profile') return route.fulfill(json({ defaultCurrency: 'CLP' }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({ readyForCalls: true }));
    if (path === '/api/v1/services') {
      return route.fulfill(json([{ id: 'svc-1', name: 'Servicio', durationMinutes: 30, price: 10000, active: true }]));
    }
    if (path === '/api/v1/business/hours') {
      return route.fulfill(json([{ dayOfWeek: 1, openTime: '09:00:00', closeTime: '18:00:00' }]));
    }
    if (path === '/api/v1/knowledge') {
      return route.fulfill(json([{ id: 'k-1', title: 'FAQ', content: 'Respuesta', active: true }]));
    }
    if (path === '/api/v1/ai-agent/voices') {
      return route.fulfill(json([{ code: 'natural', selection: 'marin', name: 'Natural' }]));
    }
    if (path === '/api/v1/ai-agent') {
      return route.fulfill(json({
        configured: true,
        name: 'Helvoca',
        language: 'es',
        voice: 'marin',
        greeting: 'Hola',
        instructions: 'Responde brevemente',
        active: true,
        capabilities: []
      }));
    }
    if (path === '/api/v1/phone-numbers') return route.fulfill(json([]));
    return route.fulfill(json({}));
  });
}

test.describe('Settings full cutover RED contract', () => {
  for (const surface of [
    { path: '/', selector: '.nav-config' },
    { path: '/inventory.html', selector: '.nav-config' },
    { path: '/account.html', selector: 'a[href*="settings"]' },
    { path: '/simulator.html', selector: 'a[href*="settings"]' },
    { path: '/conversations.html', selector: 'a[href*="settings"]' },
    { path: '/business-import.html', selector: 'a[href*="settings"]' }
  ]) {
    test(`${surface.path} sends Configuración to the canonical React route`, async ({ page }) => {
      await page.goto(surface.path);
      const links = page.locator(surface.selector);
      await expect(links.first()).toHaveAttribute('href', /^\/app\/settings(?:[?#].*)?$/);
    });
  }

  test('React Settings no longer sends users back to the legacy Settings screen', async ({ page }) => {
    await bootReactSettings(page);
    await page.goto('/app/settings?section=team');

    await expect(page.getByRole('heading', { level: 1, name: 'Configuración' })).toBeVisible();
    await expect(page.locator('a[href^="/settings.html"]')).toHaveCount(0);
  });
});


test('Team is managed inside React Settings without legacy fallback', async ({ page }) => {
  await bootReactSettings(page);

  const invitations = [{
    id: 'invite-1',
    name: 'Camila Soto',
    email: 'camila@negocio.cl',
    role: 'STAFF',
    status: 'PENDING',
    invitePath: '/invite.html?businessId=demo&token=existing'
  }];

  await page.route('**/api/v1/admin/users', route => route.fulfill(json([
    { id: 'user-1', name: 'Admin Demo', email: 'admin@demo.cl', active: true, roles: ['BUSINESS_ADMIN'] }
  ])));
  await page.route('**/api/v1/admin/invitations**', async route => {
    const url = new URL(route.request().url());
    if (route.request().method() === 'DELETE') {
      const id = url.pathname.split('/').pop();
      const item = invitations.find(value => value.id === id);
      if (item) item.status = 'REVOKED';
      return route.fulfill({ status: 204, body: '' });
    }
    if (route.request().method() === 'POST') {
      const body = route.request().postDataJSON();
      const created = {
        id: 'invite-2',
        name: body.name,
        email: body.email,
        role: body.role,
        status: 'PENDING',
        invitePath: '/invite.html?businessId=demo&token=new-token'
      };
      invitations.unshift(created);
      return route.fulfill(json(created, 201));
    }
    return route.fulfill(json(invitations));
  });

  await page.goto('/app/settings?section=team');

  await expect(page.getByRole('heading', { name: 'Equipo y permisos' })).toBeVisible();
  await expect(page.getByText('Admin Demo')).toBeVisible();
  await expect(page.getByText('camila@negocio.cl')).toBeVisible();

  await page.getByLabel('Nombre de la persona').fill('Diego Pérez');
  await page.getByLabel('Correo de la persona').fill('diego@negocio.cl');
  await page.getByLabel('Rol de la persona').selectOption('MANAGER');
  await page.getByRole('button', { name: 'Crear invitación' }).click();

  await expect(page.getByLabel('Enlace de invitación')).toHaveValue(/new-token/);
  await expect(page.locator('a[href^="/settings.html"]')).toHaveCount(0);
});

test('special business hours are managed inside React Settings', async ({ page }) => {
  await bootReactSettings(page);

  let exceptions = [{
    id: 'exc-1',
    exceptionDate: '2026-12-25',
    closed: true,
    openTime: null,
    closeTime: null,
    reason: 'Navidad'
  }];

  await page.route('**/api/v1/business/schedule-exceptions**', async route => {
    const url = new URL(route.request().url());
    if (route.request().method() === 'PUT') {
      const date = url.pathname.split('/').pop();
      const body = route.request().postDataJSON();
      const saved = { id: 'exc-2', exceptionDate: date, ...body };
      exceptions = exceptions.filter(value => value.exceptionDate !== date).concat(saved);
      return route.fulfill(json(saved));
    }
    if (route.request().method() === 'DELETE') {
      const date = url.pathname.split('/').pop();
      exceptions = exceptions.filter(value => value.exceptionDate !== date);
      return route.fulfill({ status: 204, body: '' });
    }
    return route.fulfill(json(exceptions));
  });

  await page.goto('/app/settings?section=hours');

  await expect(page.getByRole('heading', { name: 'Días especiales' })).toBeVisible();
  await expect(page.getByText('Navidad')).toBeVisible();

  await page.getByLabel('Fecha especial').fill('2026-12-31');
  await page.getByLabel('Tipo de día especial').selectOption('special');
  await page.getByLabel('Apertura especial').fill('10:00');
  await page.getByLabel('Cierre especial').fill('14:00');
  await page.getByLabel('Motivo del día especial').fill('Horario de fin de año');
  await page.getByRole('button', { name: 'Guardar día especial' }).click();

  await expect(page.getByText('Horario de fin de año')).toBeVisible();
});


test('channel provisioning and WhatsApp activation remain explicit in React Settings', async ({ page }) => {
  await bootReactSettings(page);

  let provisionCalls = 0;
  let activationCalls = 0;

  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: true,
    configured: true,
    purchaseAvailable: true,
    provider: 'TWILIO',
    message: 'Disponible'
  })));

  await page.route('**/api/v1/phone-numbers/provisioning/available?*', route => route.fulfill(json([
    {
      phoneNumber: '+56220001111',
      friendlyName: '+56220001111',
      locality: 'Santiago',
      region: 'RM',
      isoCountry: 'CL',
      addressRequirements: 'none',
      voiceCapable: true
    }
  ])));

  await page.route('**/api/v1/phone-numbers/provisioning', async route => {
    provisionCalls += 1;
    await route.fulfill(json({
      id: 'phone-1',
      provider: 'TWILIO',
      phoneNumber: '+56220001111',
      active: true,
      whatsappEnabled: false
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/config', route => route.fulfill(json({
    status: 'CONFIGURED_DISABLED',
    configured: true,
    enabled: false,
    provider: 'META',
    phoneRecordId: 'phone-1',
    phoneNumber: '+56220001111',
    credentialReferenceConfigured: true
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap', route => route.fulfill(json({
    enabled: true,
    available: false
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/certification/readiness', route => route.fulfill(json({
    state: 'READY_FOR_PILOT_CERTIFICATION',
    ready: true,
    alreadyCertified: false,
    blockers: []
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/deployment/readiness', route => route.fulfill(json({
    state: 'READY_FOR_TENANT_STAGING',
    readyForTenantStaging: true,
    blockers: []
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/config/activate', async route => {
    activationCalls += 1;
    await route.fulfill(json({
      status: 'CONFIGURED_ENABLED',
      configured: true,
      enabled: true,
      provider: 'META',
      phoneRecordId: 'phone-1',
      phoneNumber: '+56220001111',
      credentialReferenceConfigured: true
    }));
  });

  await page.goto('/app/settings?section=channels');

  expect(provisionCalls).toBe(0);
  expect(activationCalls).toBe(0);

  await page.getByRole('button', { name: 'Buscar números' }).click();
  await expect(page.getByRole('button', { name: 'Aprovisionar' })).toBeVisible();
  expect(provisionCalls).toBe(0);

  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Aprovisionar' }).click();
  await expect.poll(() => provisionCalls).toBe(1);

  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Activar WhatsApp' }).click();
  await expect.poll(() => activationCalls).toBe(1);
});

test('Meta embedded signup completes inside React and clears the registration PIN', async ({ page }) => {
  await page.addInitScript(() => {
    window.FB = {
      init: () => {},
      login: callback => callback({ authResponse: { code: 'temporary-e2e-code' } })
    };
  });
  await bootReactSettings(page);

  let authorizationPayload = null;
  let finalizationPayload = null;
  let configured = false;

  await page.route('**/api/v1/phone-numbers/provisioning/status', route => route.fulfill(json({
    enabled: true,
    configured: true,
    purchaseAvailable: false,
    provider: 'TWILIO',
    message: 'No disponible'
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/config', route => route.fulfill(json(
    configured
      ? {
          status: 'CONFIGURED_DISABLED',
          configured: true,
          enabled: false,
          provider: 'META',
          phoneRecordId: 'phone-meta',
          phoneNumber: '+56911111111',
          credentialReferenceConfigured: true
        }
      : {
          status: 'NOT_CONFIGURED',
          configured: false,
          enabled: false
        }
  )));

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap', route => route.fulfill(json({
    enabled: true,
    available: true,
    appId: '123456789',
    configId: '987654321',
    graphApiVersion: 'v26.0'
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/authorization-code', async route => {
    authorizationPayload = route.request().postDataJSON();
    await route.fulfill(json({
      state: 'AUTHORIZATION_CODE_EXCHANGED_AND_VALIDATED',
      accepted: true,
      retained: false,
      exchangePending: false,
      wabas: [{
        id: '1906385232743451',
        name: 'Negocio E2E WhatsApp',
        currency: 'CLP',
        timezoneId: 'America/Santiago',
        systemUserAssigned: true
      }],
      wabaAfterCursor: null
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-numbers', route => route.fulfill(json({
    state: 'PHONE_NUMBERS_DISCOVERED',
    appSubscribed: true,
    phoneNumbers: [{
      id: '112233445566',
      displayPhoneNumber: '+56 9 1111 1111',
      verifiedName: 'Negocio E2E',
      qualityRating: 'GREEN',
      codeVerificationStatus: 'VERIFIED'
    }],
    afterCursor: null
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/validate', route => route.fulfill(json({
    state: 'PHONE_NUMBER_VALIDATED',
    phoneNumberId: '112233445566',
    displayPhoneNumber: '+56 9 1111 1111',
    verifiedName: 'Negocio E2E',
    qualityRating: 'GREEN',
    codeVerificationStatus: 'VERIFIED'
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/embedded-signup/waba/phone-number/finalize', async route => {
    finalizationPayload = route.request().postDataJSON();
    configured = true;
    await route.fulfill(json({
      state: 'PHONE_NUMBER_REGISTERED_AND_STAGED',
      phoneRecordId: 'phone-meta',
      provider: 'META',
      phoneNumberId: '112233445566',
      wabaId: '1906385232743451',
      credentialRef: 'meta-credential-ref',
      enabled: false
    }));
  });

  await page.route('**/api/v1/channels/whatsapp/meta/certification/readiness', route => route.fulfill(json({
    state: 'BLOCKED',
    ready: false,
    alreadyCertified: false,
    blockers: [{ code: 'CERT_REQUIRED', message: 'Certification required' }]
  })));

  await page.route('**/api/v1/channels/whatsapp/meta/deployment/readiness', route => route.fulfill(json({
    state: 'BLOCKED',
    readyForTenantStaging: false,
    blockers: [{ code: 'STAGING_REQUIRED', message: 'Staging required' }]
  })));

  await page.goto('/app/settings?section=channels');

  await page.getByRole('button', { name: 'Conectar WhatsApp' }).click();
  await page.getByRole('button', { name: 'Continuar con Meta' }).click();

  await expect.poll(() => authorizationPayload).toEqual({ code: 'temporary-e2e-code' });
  await expect(page.getByText('Negocio E2E WhatsApp')).toBeVisible();

  await page.getByRole('button', { name: /Negocio E2E WhatsApp/ }).click();
  await page.getByRole('button', { name: 'Continuar con esta cuenta' }).click();

  await expect(page.getByText('+56 9 1111 1111')).toBeVisible();
  await page.getByRole('button', { name: /\+56 9 1111 1111/ }).click();
  await page.getByRole('button', { name: 'Validar número' }).click();

  const pinInput = page.getByLabel('PIN de registro de Meta');
  await pinInput.fill('123456');
  await page.getByRole('button', { name: 'Finalizar conexión' }).click();

  await expect.poll(() => finalizationPayload).toEqual({
    wabaId: '1906385232743451',
    phoneNumberId: '112233445566',
    pin: '123456'
  });
  await expect(pinInput).toHaveValue('');
  await expect(page.getByText('temporary-e2e-code')).toHaveCount(0);
  await expect(page.getByText('123456')).toHaveCount(0);
});
