const { test, expect } = require('@playwright/test');

const CALL_ID = '11111111-1111-1111-1111-111111111111';
const CALL_OLD_ID = '22222222-2222-2222-2222-222222222222';
const WA_ID = '33333333-3333-3333-3333-333333333333';

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

const calls = [
  {
    id: CALL_ID,
    customerId: 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
    phoneNumberId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    telephonyProvider: 'TWILIO',
    aiProvider: 'OPENAI',
    aiModel: 'gpt-realtime',
    providerCallId: 'CA-new',
    callerNumber: '+56911112222',
    destinationNumber: '+56220001111',
    direction: 'INBOUND',
    status: 'COMPLETED',
    startedAt: '2026-10-02T15:30:00Z',
    answeredAt: '2026-10-02T15:30:03Z',
    endedAt: '2026-10-02T15:32:00Z',
    durationSeconds: 117,
    resolution: 'BOOKING_CREATED'
  },
  {
    id: CALL_OLD_ID,
    customerId: null,
    phoneNumberId: 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    telephonyProvider: 'TWILIO',
    aiProvider: 'OPENAI',
    aiModel: 'gpt-realtime',
    providerCallId: 'CA-old',
    callerNumber: '+56977778888',
    destinationNumber: '+56220001111',
    direction: 'INBOUND',
    status: 'FAILED',
    startedAt: '2026-10-02T12:00:00Z',
    answeredAt: '2026-10-02T12:00:04Z',
    endedAt: '2026-10-02T12:00:40Z',
    durationSeconds: 36,
    resolution: 'HUMAN_TRANSFER'
  }
];

const whatsapp = [{
  id: WA_ID,
  customerId: 'cccccccc-cccc-cccc-cccc-cccccccccccc',
  channel: 'whatsapp',
  sender: '+56955556666',
  recipient: '+56220001111',
  openedAt: '2026-10-02T14:00:00Z',
  lastMessageAt: '2026-10-02T14:06:00Z'
}];

const callDetails = {
  [CALL_ID]: {
    call: calls[0],
    summary: 'El cliente pidió una hora para mañana y RecepVoz creó la reserva.',
    transcript: [
      {
        id: 'dddddddd-dddd-dddd-dddd-dddddddddddd',
        speaker: 'ASSISTANT',
        content: 'Listo, tu reserva quedó creada para mañana.',
        sequenceNumber: 2,
        createdAt: '2026-10-02T15:31:20Z'
      },
      {
        id: 'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee',
        speaker: 'USER',
        content: 'Necesito una hora mañana.',
        sequenceNumber: 1,
        createdAt: '2026-10-02T15:30:20Z'
      }
    ],
    actions: [{
      id: 'ffffffff-ffff-ffff-ffff-ffffffffffff',
      actionType: 'CREATE_BOOKING',
      success: true,
      entityType: 'BOOKING',
      entityId: 'abababab-abab-abab-abab-abababababab',
      detail: 'Reserva confirmada para mañana.',
      errorCode: null,
      durationMs: 83,
      createdAt: '2026-10-02T15:31:10Z'
    }]
  },
  [CALL_OLD_ID]: {
    call: calls[1],
    summary: 'La llamada necesitó atención humana.',
    transcript: [{
      id: '12121212-1212-1212-1212-121212121212',
      speaker: 'USER',
      content: 'Necesito hablar con una persona.',
      sequenceNumber: 1,
      createdAt: '2026-10-02T12:00:12Z'
    }],
    actions: [{
      id: '13131313-1313-1313-1313-131313131313',
      actionType: 'TRANSFER_TO_HUMAN',
      success: true,
      entityType: null,
      entityId: null,
      detail: 'Se solicitó derivación humana.',
      errorCode: null,
      durationMs: 22,
      createdAt: '2026-10-02T12:00:20Z'
    }]
  }
};

const whatsappDetail = {
  conversation: whatsapp[0],
  messages: [
    {
      id: '14141414-1414-1414-1414-141414141414',
      direction: 'OUTBOUND',
      role: 'ASSISTANT',
      content: 'Sí. Tengo disponibilidad a las 11:30.',
      createdAt: '2026-10-02T14:05:00Z'
    },
    {
      id: '15151515-1515-1515-1515-151515151515',
      direction: 'INBOUND',
      role: 'USER',
      content: '¿Tienen hora mañana?',
      createdAt: '2026-10-02T14:01:00Z'
    },
    {
      id: '16161616-1616-1616-1616-161616161616',
      direction: 'INBOUND',
      role: 'USER',
      content: 'Perfecto, gracias.',
      createdAt: '2026-10-02T14:06:00Z'
    }
  ]
};

async function boot(page, options = {}) {
  await page.addInitScript(
    token => sessionStorage.setItem('helvoca_access_token', token),
    options.token || 'conversations-react-e2e'
  );

  await page.route('**/api/v1/auth/me', route => {
    if (options.expired) return route.fulfill(json({ message: 'expired' }, 401));
    if (options.forbidden) return route.fulfill(json({ message: 'forbidden' }, 403));
    return route.fulfill(json({
      email: 'owner@negocio.cl',
      roles: ['BUSINESS_OWNER'],
      permissions: ['CONVERSATIONS_READ']
    }));
  });

  await page.route('**/api/v1/calls?**', route => {
    if (options.expired) return route.fulfill(json({ message: 'expired' }, 401));
    if (options.forbidden) return route.fulfill(json({ message: 'forbidden' }, 403));
    if (options.callsError) return route.fulfill(json({ message: 'calls unavailable' }, 503));
    return route.fulfill(json({
      content: calls,
      number: 0,
      size: 100,
      totalElements: calls.length,
      totalPages: 1
    }));
  });

  for (const [id, detail] of Object.entries(callDetails)) {
    await page.route(`**/api/v1/calls/${id}`, route => {
      if (options.expired) return route.fulfill(json({ message: 'expired' }, 401));
      if (options.forbidden) return route.fulfill(json({ message: 'forbidden' }, 403));
      return route.fulfill(json(detail));
    });
  }

  await page.route('**/api/v1/messaging/conversations', route => {
    if (options.expired) return route.fulfill(json({ message: 'expired' }, 401));
    if (options.forbidden) return route.fulfill(json({ message: 'forbidden' }, 403));
    if (options.whatsappError) return route.fulfill(json({ message: 'whatsapp unavailable' }, 503));
    return route.fulfill(json(whatsapp));
  });

  await page.route(`**/api/v1/messaging/conversations/${WA_ID}`, route => {
    if (options.expired) return route.fulfill(json({ message: 'expired' }, 401));
    if (options.forbidden) return route.fulfill(json({ message: 'forbidden' }, 403));
    return route.fulfill(json(whatsappDetail));
  });
}

test.describe('React Conversations / Recepcionista IA migration', () => {
  test('serves the direct React route and preserves the existing call deep link', async ({ page }) => {
    await boot(page);

    const response = await page.goto(`/app/conversations?call=${CALL_ID}`);

    expect(response).not.toBeNull();
    expect(response.status()).toBe(200);
    await expect(page.getByRole('heading', { level: 1, name: /Recepcionista IA|Conversaciones/i })).toBeVisible();
    await expect(page.getByText('+56911112222', { exact: true })).toBeVisible();
    await expect(page.getByText('Necesito una hora mañana.', { exact: true })).toBeVisible();
  });

  test('shows the real mixed-channel list with search, channel filters and visible selection', async ({ page }) => {
    await boot(page);
    await page.goto('/app/conversations');

    const callRow = page.getByRole('button', { name: /\+56911112222/ });
    const waRow = page.getByRole('button', { name: /\+56955556666/ });

    await expect(callRow).toBeVisible();
    await expect(waRow).toBeVisible();
    await expect(callRow).toContainText(/Llamada/i);
    await expect(callRow).toContainText(/Reserva creada/i);
    await expect(waRow).toContainText(/WhatsApp/i);

    await page.getByRole('searchbox', { name: /Buscar conversaciones/i }).fill('55556666');
    await expect(waRow).toBeVisible();
    await expect(callRow).toHaveCount(0);

    await page.getByRole('searchbox', { name: /Buscar conversaciones/i }).fill('');
    await page.getByRole('button', { name: 'Llamadas', exact: true }).click();
    await expect(page.getByRole('button', { name: /\+56911112222/ })).toBeVisible();
    await expect(page.getByRole('button', { name: /\+56955556666/ })).toHaveCount(0);

    await page.getByRole('button', { name: /\+56977778888/ }).click();
    await expect(page.getByRole('button', { name: /\+56977778888/ })).toHaveAttribute('aria-pressed', 'true');
  });

  test('opens a voice conversation with chronological transcript and human-readable AI events', async ({ page }) => {
    await boot(page);
    await page.goto(`/app/conversations?call=${CALL_ID}`);

    const detail = page.getByRole('region', { name: /Detalle de conversación/i });
    await expect(detail).toContainText('El cliente pidió una hora para mañana');
    await expect(detail).toContainText('Reserva creada');
    await expect(detail).not.toContainText('CREATE_BOOKING');

    const transcript = page.getByTestId('conversation-transcript');
    await expect(transcript).toBeVisible();
    const text = await transcript.textContent();
    expect(text.indexOf('Necesito una hora mañana.')).toBeLessThan(
      text.indexOf('Listo, tu reserva quedó creada para mañana.')
    );
  });

  test('opens WhatsApp, keeps its messages chronological and preserves the WhatsApp deep link', async ({ page }) => {
    await boot(page);
    await page.goto(`/app/conversations?whatsapp=${WA_ID}`);

    await expect(page.getByText('+56955556666', { exact: true })).toBeVisible();
    await expect(page.getByText('WhatsApp', { exact: true }).first()).toBeVisible();

    const transcript = page.getByTestId('conversation-transcript');
    const text = await transcript.textContent();
    expect(text.indexOf('¿Tienen hora mañana?')).toBeLessThan(
      text.indexOf('Sí. Tengo disponibilidad a las 11:30.')
    );
    expect(text.indexOf('Sí. Tengo disponibilidad a las 11:30.')).toBeLessThan(
      text.indexOf('Perfecto, gracias.')
    );
  });

  test('keeps available conversations usable when one source fails', async ({ page }) => {
    await boot(page, { callsError: true });
    await page.goto('/app/conversations');

    await expect(page.getByRole('status')).toContainText(/No pudimos cargar.*llamadas/i);
    await expect(page.getByRole('button', { name: /\+56955556666/ })).toBeVisible();
    await page.getByRole('button', { name: /\+56955556666/ }).click();
    await expect(page.getByText('¿Tienen hora mañana?', { exact: true })).toBeVisible();
  });

  test('redirects to authentication when the session expires', async ({ page }) => {
    await boot(page, { expired: true });
    await page.goto('/app/conversations');

    await expect(page).toHaveURL('http://127.0.0.1:4173/');
  });

  test('renders a forbidden state instead of leaking conversation data', async ({ page }) => {
    await boot(page, { forbidden: true });
    await page.goto('/app/conversations');

    await expect(page.getByRole('heading', { name: /Sin acceso|No tienes acceso/i })).toBeVisible();
    await expect(page.getByText('+56911112222', { exact: true })).toHaveCount(0);
    await expect(page.getByText('+56955556666', { exact: true })).toHaveCount(0);
  });

  test('is read-only on open: no businessId and no implicit message/call/transfer/mark mutations', async ({ page }) => {
    const forbiddenTenantHints = [];
    const mutations = [];

    page.on('request', request => {
      const url = new URL(request.url());
      if (!url.pathname.startsWith('/api/v1/')) return;

      const body = request.postData() || '';
      if (url.searchParams.has('businessId') || /["']?businessId["']?\s*[:=]/i.test(body)) {
        forbiddenTenantHints.push({ method: request.method(), url: request.url(), body });
      }

      if (!['GET', 'HEAD', 'OPTIONS'].includes(request.method())) {
        mutations.push({ method: request.method(), url: request.url(), body });
      }
    });

    await boot(page);
    await page.goto(`/app/conversations?call=${CALL_ID}`);
    await expect(page.getByText('Necesito una hora mañana.', { exact: true })).toBeVisible();

    expect(forbiddenTenantHints).toEqual([]);
    expect(mutations).toEqual([]);
    await expect(page.getByRole('button', { name: /Enviar|Llamar|Transferir|Marcar/i })).toHaveCount(0);
  });

  test('uses list -> detail -> back on mobile instead of compressing desktop panes', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await boot(page);
    await page.goto('/app/conversations');

    const list = page.getByRole('region', { name: /Lista de conversaciones/i });
    const detail = page.getByRole('region', { name: /Detalle de conversación/i });

    await expect(list).toBeVisible();
    await expect(detail).toBeHidden();

    await page.getByRole('button', { name: /\+56911112222/ }).click();
    await expect(detail).toBeVisible();
    await expect(list).toBeHidden();
    await expect(page.getByRole('button', { name: /Volver a conversaciones/i })).toBeVisible();

    await page.getByRole('button', { name: /Volver a conversaciones/i }).click();
    await expect(list).toBeVisible();
    await expect(detail).toBeHidden();

    expect(await page.evaluate(() =>
      document.documentElement.scrollWidth <= document.documentElement.clientWidth
    )).toBe(true);
  });

  test('stays contained across desktop, tablet and mobile widths and respects reduced motion', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await boot(page);

    for (const viewport of [
      { width: 1440, height: 900 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.goto('/app/conversations');
      await expect(page.getByRole('heading', { level: 1, name: /Recepcionista IA|Conversaciones/i })).toBeVisible();
      expect(await page.evaluate(() =>
        document.documentElement.scrollWidth <= document.documentElement.clientWidth
      )).toBe(true);
    }
  });
});
