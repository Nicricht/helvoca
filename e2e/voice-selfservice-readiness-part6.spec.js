const { test, expect } = require('@playwright/test');

const json = (body, status = 200) => ({
  status, contentType: 'application/json', body: JSON.stringify(body)
});

const PRIMARY = 'phone-business-1';
const PHONE = '+12025550111';

function baselinePhone(overrides = {}) {
  return {
    phoneId: PRIMARY, phoneNumber: PHONE,
    state: 'PREVIOUSLY_VERIFIED',
    canAttemptActivation: false,
    lastVerifiedAt: '2026-10-10T18:00:00Z',
    message: 'Activo con verificación anterior. No confirma por sí solo llamadas reales ni webhooks actuales.',
    ...overrides
  };
}

async function start(page, options = {}) {
  const state = { mutations: [], reads: [], activated: false, ...options };

  await page.addInitScript(() => sessionStorage.setItem('helvoca_access_token', 'part6-browser'));

  await page.route('**/api/v1/**', route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    const method = request.method();
    if (method === 'GET') state.reads.push(path);
    else state.mutations.push({ path, method, body: request.postDataJSON() });

    if (path === '/api/v1/auth/me') return route.fulfill(json({
      email: 'test@helvoca.cl',
      roles: options.role === 'OPERATOR' ? ['OPERATOR'] : ['BUSINESS_ADMIN'],
      permissions: options.role === 'OPERATOR'
        ? ['BUSINESS_READ'] : ['BUSINESS_READ', 'BUSINESS_CONFIGURE', 'CATALOG_READ', 'CATALOG_MANAGE']
    }));
    if (path === '/api/v1/business') return route.fulfill(json({
      name: 'Barbería Norte', timezone: 'America/Santiago',
      language: 'es', humanTransferPhone: '+56977777777'
    }));
    if (path === '/api/v1/business/profile') return route.fulfill(json({
      businessId: '11111111-1111-1111-1111-111111111111',
      publicDescription: 'Barbería segura', sellsServices: true, usesReservations: true
    }));
    if (path === '/api/v1/onboarding/status') return route.fulfill(json({
      businessProfileConfigured: true, servicesConfigured: true,
      scheduleConfigured: true, knowledgeConfigured: true,
      phoneConfigured: true, readyForCalls: false, nextStep: 'PHONE_VERIFICATION'
    }));
    if (path === '/api/v1/services') return route.fulfill(json([]));
    if (path === '/api/v1/business/hours') return route.fulfill(json([]));
    if (path === '/api/v1/knowledge') return route.fulfill(json([]));
    if (path === '/api/v1/ai-agent/voices') return route.fulfill(json([
      { code: 'natural', name: 'Natural', selection: 'marin' }
    ]));
    if (path === '/api/v1/ai-agent') return route.fulfill(json({
      configured: true, name: 'Helvoca', voice: 'marin',
      active: true, greeting: 'Hola', capabilities: []
    }));
    if (path === '/api/v1/phone-numbers') return route.fulfill(json(
      options.empty ? [] : [{
        id: PRIMARY, provider: 'TWILIO', phoneNumber: PHONE,
        active: state.activated || options.phoneActive !== false, whatsappEnabled: false
      }]
    ));
    if (path === '/api/v1/phone-numbers/provisioning/status') return route.fulfill(json({
      enabled: false, configured: false, purchaseAvailable: false, provider: 'TWILIO',
      message: 'El aprovisionamiento está deshabilitado.'
    }));
    if (path === '/api/v1/phone-numbers/provisioning/readiness') {
      if (options.readinessError) return route.fulfill(json({ message: 'Unavailable' }, 503));
      if (options.empty) return route.fulfill(json([]));
      if (state.activated) return route.fulfill(json([baselinePhone()]));
      return route.fulfill(json([options.readiness || baselinePhone()]));
    }
    if (path === '/api/v1/phone-numbers/' + PRIMARY + '/active' && method === 'PATCH') {
      state.activated = true;
      return route.fulfill(json({
        id: PRIMARY, provider: 'TWILIO', phoneNumber: PHONE, active: true
      }));
    }
    if (path === '/api/v1/channels/whatsapp/meta/config') return route.fulfill(json({
      configured: false, enabled: false
    }));
    if (path === '/api/v1/channels/whatsapp/meta/embedded-signup/bootstrap') {
      return route.fulfill(json({ available: false }));
    }
    return route.fulfill(json(method === 'GET' ? [] : { message: 'Mutation blocked' }, method === 'GET' ? 200 : 501));
  });

  await page.goto('/app/settings');
  await page.getByRole('tab', { name: 'Canales' }).click();
  await expect(page.getByRole('region', { name: 'Telefonía' })).toBeVisible();
  return state;
}

test.describe('Voice self-service setup Part 6', () => {
  test('shows historical evidence without inventing live call certification or making provider mutations', async ({ page }) => {
    const state = await start(page);

    const guide = page.getByLabel('Guía de configuración de llamadas');
    await expect(guide.getByText('1. Número del negocio')).toBeVisible();
    await expect(guide.getByText('2. Identidad y voz')).toBeVisible();
    await expect(guide.getByText('3. Prueba de funcionamiento')).toBeVisible();
    await expect(guide.getByText('Verificado anteriormente')).toBeVisible();
    await expect(guide.getByText('Certificación pendiente')).toBeVisible();
    await expect(guide.getByLabel('Número que quieres preparar')).toHaveValue(PRIMARY);
    await expect(guide.getByText(/No confirma por sí solo llamadas reales/i)).toBeVisible();
    await expect(guide.getByText(/ningún estado de esta pantalla equivale a una prueba real/i)).toBeVisible();
    await expect(guide.getByRole('button', { name: /Verificar con Twilio/i })).toHaveCount(0);
    expect(state.mutations).toEqual([]);
    expect(state.reads).toContain('/api/v1/phone-numbers/provisioning/readiness');
    expect(state.reads.every(url => !url.includes('/twilio') && !url.includes('/calls'))).toBe(true);
  });

  test('does not claim a ready number when there is no tenant assignment', async ({ page }) => {
    const state = await start(page, { empty: true });
    const guide = page.getByLabel('Guía de configuración de llamadas');
    await expect(guide.getByText('Número pendiente')).toBeVisible();
    await expect(guide.getByText(/aún no hay números asignados/i)).toBeVisible();
    await expect(guide.getByRole('button', { name: /Verificar con Twilio/i })).toHaveCount(0);
    expect(state.mutations).toEqual([]);
  });

  test('fails closed on readiness API failure with explicit read-only retry', async ({ page }) => {
    const state = await start(page, { readinessError: true });
    const guide = page.getByLabel('Guía de configuración de llamadas');
    await expect(guide.getByRole('alert')).toContainText('No se pudo verificar');
    await expect(guide.getByRole('button', { name: /Verificar con Twilio/i })).toHaveCount(0);
    await guide.getByRole('button', { name: 'Reintentar consulta' }).click();
    await expect(guide.getByRole('alert')).toBeVisible();
    expect(state.mutations).toEqual([]);
  });

  test('requires explicit confirmation before activation, never purchasing or calling', async ({ page }) => {
    const state = await start(page, {
      phoneActive: false,
      readiness: baselinePhone({
        state: 'ACTIVATION_REQUIRED', canAttemptActivation: true,
        message: 'Hay una verificación anterior. Activar requiere volver a validar Twilio.'
      })
    });
    const guide = page.getByLabel('Guía de configuración de llamadas');
    const verify = guide.getByRole('button', { name: 'Verificar con Twilio y activar' });
    await expect(verify).toBeEnabled();
    page.once('dialog', dialog => dialog.dismiss());
    await verify.click();
    expect(state.mutations).toEqual([]);

    page.once('dialog', dialog => dialog.accept());
    await verify.click();
    await expect(guide.getByText('Verificado anteriormente')).toBeVisible();
    await expect.poll(() => state.mutations.length).toBe(1);
    expect(state.mutations[0]).toMatchObject({
      path: '/api/v1/phone-numbers/' + PRIMARY + '/active',
      method: 'PATCH',
      body: { active: true }
    });
    expect(state.mutations.every(m => !/provisioning$|\/calls|\/messages/.test(m.path))).toBe(true);
  });

  test('legacy or foreign-account evidence offers no activation button', async ({ page }) => {
    const state = await start(page, {
      readiness: baselinePhone({
        state: 'ACCOUNT_MISMATCH', canAttemptActivation: false,
        lastVerifiedAt: null,
        message: 'La cuenta Twilio administrada no coincide con la asignación registrada.'
      })
    });
    const guide = page.getByLabel('Guía de configuración de llamadas');
    await expect(guide.getByText('No certificado')).toBeVisible();
    await expect(guide.getByText(/cuenta Twilio administrada no coincide/i)).toBeVisible();
    await expect(guide.getByRole('button', { name: /Verificar con Twilio/i })).toHaveCount(0);
    expect(state.mutations).toEqual([]);
  });

  test('operator only reads setup, no activation or purchase controls', async ({ page }) => {
    const state = await start(page, {
      role: 'OPERATOR',
      readiness: baselinePhone({
        state: 'NEEDS_VERIFICATION', canAttemptActivation: true, lastVerifiedAt: null
      })
    });
    const guide = page.getByLabel('Guía de configuración de llamadas');
    await expect(guide.getByText('Requiere verificación')).toBeVisible();
    await expect(guide.getByRole('button', { name: /Verificar con Twilio/i })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /Buscar números|Conectar número|Aprovisionar/i })).toHaveCount(0);
    expect(state.mutations).toEqual([]);
  });

  test('remains accessible and horizontally scroll-free at phone width with reduced motion', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    const state = await start(page);
    const guide = page.getByLabel('Guía de configuración de llamadas');
    await expect(guide.getByLabel('Número que quieres preparar')).toBeVisible();
    await guide.getByLabel('Número que quieres preparar').focus();
    await expect(guide.getByLabel('Número que quieres preparar')).toBeFocused();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= document.documentElement.clientWidth)).toBe(true);
    expect(state.mutations).toEqual([]);
  });
});
