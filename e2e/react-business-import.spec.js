const { test, expect } = require('./import-coverage-fixture');
const fs = require('fs');
const path = require('path');

const json = (body, status = 200) => ({
  status,
  contentType: 'application/json',
  body: JSON.stringify(body)
});

async function boot(page, options = {}) {
  const roles = options.roles || ['BUSINESS_ADMIN'];
  const permissions = options.permissions || [
    'BUSINESS_READ',
    'BUSINESS_CONFIGURE',
    'CATALOG_READ',
    'CATALOG_MANAGE',
    'INVENTORY_READ',
    'INVENTORY_MANAGE'
  ];

  await page.addInitScript(() => {
    sessionStorage.setItem('helvoca_access_token', 'business-import-react-e2e');
  });

  let previewCalls = 0;
  let applyCalls = 0;
  let applyPayload = null;
  let savedProfile = null;
  let savedHours = null;
  let selectedPatch = null;
  const knowledgeWrites = [];

  await page.route('**/api/v1/**', async route => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;

    if (pathname === '/api/v1/auth/me' && request.method() === 'GET') {
      return route.fulfill(json({
        email: 'admin@demo.cl',
        roles,
        permissions
      }));
    }

    if (pathname === '/api/v1/subscription' && request.method() === 'GET') {
      return route.fulfill(json({
        plan: 'PRO',
        publicPlanCode: 'PRO',
        planName: 'Profesional',
        status: 'ACTIVE',
        includedMinutes: 500,
        usedMinutes: 42
      }));
    }

    if (pathname === '/api/v1/business' && request.method() === 'GET') {
      return route.fulfill(json({
        name: 'Veterinaria Norte',
        timezone: 'America/Santiago',
        language: 'es'
      }));
    }

    if (pathname === '/api/v1/business/profile' && request.method() === 'GET') {
      return route.fulfill(json(options.currentProfile || {}));
    }
    if (pathname === '/api/v1/business/profile' && request.method() === 'PUT') {
      savedProfile = request.postDataJSON();
      return route.fulfill(json(savedProfile));
    }
    if (pathname === '/api/v1/business/hours' && request.method() === 'GET') {
      if (options.failHoursRead) return route.fulfill(json({ message: 'No se pudo leer el horario.' }, 503));
      return route.fulfill(json(options.existingHours || []));
    }
    if (pathname === '/api/v1/business/hours' && request.method() === 'PUT') {
      savedHours = request.postDataJSON();
      return route.fulfill(json(savedHours.hours));
    }

    if (pathname === '/api/v1/business/hours/selected' && request.method() === 'PATCH') {
      selectedPatch = request.postDataJSON();
      const actual = options.concurrentHours ?? options.existingHours ?? [];
      const chosenDays = new Set(selectedPatch.hours.map(hour => hour.dayOfWeek));
      const selectedNow = actual.filter(hour => chosenDays.has(hour.dayOfWeek))
        .sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.openTime.localeCompare(b.openTime));
      const expected = [...selectedPatch.expectedSelectedDayHours]
        .sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.openTime.localeCompare(b.openTime));
      if (JSON.stringify(selectedNow) !== JSON.stringify(expected)) {
        return route.fulfill(json({ message: 'Los horarios seleccionados cambiaron.' }, 409));
      }
      savedHours = {
        hours: [...actual.filter(hour => !chosenDays.has(hour.dayOfWeek)), ...selectedPatch.hours]
          .sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.openTime.localeCompare(b.openTime))
      };
      return route.fulfill(json(savedHours.hours));
    }

    if (pathname === '/api/v1/knowledge' && request.method() === 'GET') {
      if (options.failKnowledgeRead) return route.fulfill(json({ message: 'No se pudo leer las FAQ.' }, 503));
      return route.fulfill(json(options.existingKnowledge || []));
    }
    if (pathname === '/api/v1/knowledge' && request.method() === 'POST') {
      if (options.failKnowledgeWriteAt === knowledgeWrites.length + 1) {
        return route.fulfill(json({ message: 'No se pudo crear la FAQ.' }, 503));
      }
      const item = request.postDataJSON();
      knowledgeWrites.push(item);
      return route.fulfill(json({ ...item, id: 'fake-knowledge-' + knowledgeWrites.length }));
    }

    if (pathname === '/api/v1/onboarding/import/preview' && request.method() === 'POST') {
      previewCalls += 1;
      return route.fulfill(json({
        businessName: 'Veterinaria Norte',
        products: options.products ?? [
          {
            name: 'Consulta veterinaria',
            description: 'Evaluación general',
            price: 20000,
            currency: 'CLP',
            sku: null,
            onHand: null,
            category: 'Consulta',
            kind: 'SERVICE',
            durationMinutes: 45,
            confidence: 0.96,
            sourceName: 'tarifario.csv'
          },
          {
            name: 'Alimento premium',
            description: null,
            price: 15990,
            currency: 'CLP',
            sku: 'ALI-01',
            onHand: null,
            category: 'Alimentos',
            kind: 'PRODUCT',
            durationMinutes: null,
            confidence: 0.91,
            sourceName: 'tarifario.csv'
          }
        ],
        sources: [{
          name: 'tarifario.csv',
          kind: 'MIXED',
          rowCount: 2,
          method: 'SPREADSHEET',
          recognized: true,
          warnings: []
        }],
        warnings: [],
        aiUsed: false,
        businessFacts: options.businessFacts || null
      }));
    }

    if (pathname === '/api/v1/onboarding/import/apply' && request.method() === 'POST') {
      applyCalls += 1;
      applyPayload = request.postDataJSON();
      return route.fulfill(json({
        created: 2,
        updated: 0,
        inventoryConfigured: 1,
        items: [
          {
            catalogItemId: '11111111-1111-1111-1111-111111111111',
            name: 'Consulta veterinaria',
            action: 'CREATED',
            inventoryConfigured: false
          },
          {
            catalogItemId: '22222222-2222-2222-2222-222222222222',
            name: 'Alimento premium',
            action: 'CREATED',
            inventoryConfigured: true
          }
        ]
      }));
    }

    return route.fulfill(json({}));
  });

  return {
    previewCalls: () => previewCalls,
    applyCalls: () => applyCalls,
    applyPayload: () => applyPayload,
    savedProfile: () => savedProfile,
    savedHours: () => savedHours,
    selectedPatch: () => selectedPatch,
    knowledgeWrites: () => knowledgeWrites
  };
}

test.describe('React business import migration', () => {
  test('free CSV FAQ preview never writes before explicit approval and leaves existing FAQ untouched', async ({ page }) => {
    const calls = await boot(page, {
      products: [],
      existingKnowledge: [{ id: 'existing', title: '¿Dónde están?', content: 'Respuesta anterior', active: true }]
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'preguntas.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('\uFEFFpregunta;respuesta\n"¿Dónde están?";"Santiago"\n"¿Envían; productos?";"Sí, previa coordinación"\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-review')).toBeVisible();
    await expect(page.getByTestId('free-setup-row-0')).toContainText('preguntas.csv');
    await expect(page.getByTestId('free-setup-row-1')).toContainText('fila 3');
    expect(calls.knowledgeWrites()).toHaveLength(0);
    expect(calls.savedHours()).toBeNull();
    expect(calls.applyCalls()).toBe(0);

    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByText(/FAQ existente.*sin sobrescribir/)).toBeVisible();
    expect(calls.knowledgeWrites()).toHaveLength(0);
    expect(calls.savedHours()).toBeNull();

    // A second explicit review can create only the newly selected answer.
    await page.getByLabel('Seleccionar sugerencia 1').check();
    await page.getByLabel('Respuesta sugerida 1').fill('Sí, solo retiro programado.');
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect.poll(() => calls.knowledgeWrites().length).toBe(1);
    expect(calls.knowledgeWrites()[0]).toMatchObject({
      title: '¿Envían; productos?', content: 'Sí, solo retiro programado.', category: 'FAQ'
    });
    expect(calls.applyCalls()).toBe(0);
  });

  test('free TSV hours are selective and preserve every unapproved existing day', async ({ page }) => {
    const calls = await boot(page, {
      products: [], existingHours: [{ dayOfWeek: 2, openTime: '10:00', closeTime: '19:00' }],
      concurrentHours: [{ dayOfWeek: 2, openTime: '12:00', closeTime: '20:00' }]
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'horarios.tsv',
      mimeType: 'text/tab-separated-values',
      buffer: Buffer.from('dia\tapertura\tcierre\nLunes\t09:00\t18:00\nMartes\t11:00\t20:00\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-review')).toBeVisible();
    await expect(page.getByLabel('Respuesta sugerida 0')).toHaveValue('09:00-18:00');
    expect(calls.savedHours()).toBeNull();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect.poll(() => calls.savedHours()).not.toBeNull();
    expect(calls.savedHours().hours).toEqual([
      { dayOfWeek: 1, openTime: '09:00', closeTime: '18:00' },
      { dayOfWeek: 2, openTime: '12:00', closeTime: '20:00' }
    ]);
    expect(calls.selectedPatch()).toEqual({
      hours: [{ dayOfWeek: 1, openTime: '09:00', closeTime: '18:00' }],
      expectedSelectedDayHours: []
    });
    expect(calls.knowledgeWrites()).toHaveLength(0);
    expect(calls.applyCalls()).toBe(0);
  });

  test('concurrent edit of the selected day fails closed with no schedule write', async ({ page }) => {
    const calls = await boot(page, {
      products: [],
      existingHours: [{ dayOfWeek: 1, openTime: '08:00', closeTime: '12:00' }],
      concurrentHours: [{ dayOfWeek: 1, openTime: '10:00', closeTime: '19:00' }]
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'horarios.csv', mimeType: 'text/csv',
      buffer: Buffer.from('dia,apertura,cierre\nLunes,09:00,18:00\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/guardar|cambiaron|reintentar/i);
    expect(calls.selectedPatch()).toMatchObject({
      hours: [{ dayOfWeek: 1, openTime: '09:00', closeTime: '18:00' }],
      expectedSelectedDayHours: [{ dayOfWeek: 1, openTime: '08:00', closeTime: '12:00' }]
    });
    expect(calls.savedHours()).toBeNull();
    expect(calls.applyCalls()).toBe(0);
    expect(calls.knowledgeWrites()).toHaveLength(0);
  });

  test('conflicting or invalid CSV hours never become implied availability or writes', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'conflictos.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('dia,apertura,cierre\nLunes,09:00,12:00\nLunes,12:00,18:00\nMartes,xx,16:00\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-warning')).toContainText(/conflicto|incompleto|inválido/i);
    await expect(page.getByTestId('free-setup-row-0')).toHaveCount(0);
    expect(calls.savedHours()).toBeNull();
    expect(calls.knowledgeWrites()).toHaveLength(0);
  });



  test('mixed FAQ and hours approvals fail before any mutation', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles([
      { name: 'faq.csv', mimeType: 'text/csv', buffer: Buffer.from('pregunta,respuesta\n¿Hacen entregas?,Sí\n') },
      { name: 'horarios.tsv', mimeType: 'text/tab-separated-values',
        buffer: Buffer.from('dia\tapertura\tcierre\nLunes\t09:00\t18:00\n') }
    ]);
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByLabel('Seleccionar sugerencia 1').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/por separado/i);
    expect(calls.knowledgeWrites()).toHaveLength(0);
    expect(calls.selectedPatch()).toBeNull();
  });

  test('edited invalid hours fail before GET or PATCH', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'horas.tsv', mimeType: 'text/tab-separated-values',
      buffer: Buffer.from('dia\tapertura\tcierre\nMartes\t10:00\t18:00\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByLabel('Respuesta sugerida 0').fill('18:00-09:00');
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/Horario inválido/i);
    expect(calls.selectedPatch()).toBeNull();
    expect(calls.savedHours()).toBeNull();
  });

  test('editing two FAQ to the same title is rejected without writing', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'faq.csv', mimeType: 'text/csv',
      buffer: Buffer.from('pregunta,respuesta\n¿Dónde están?,Aquí\n¿Abren sábados?,Sí\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByLabel('Seleccionar sugerencia 1').check();
    await page.getByLabel('Clave sugerida 1').fill('¿Dónde están?');
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/duplicada/i);
    expect(calls.knowledgeWrites()).toHaveLength(0);
  });

  test('failure reading existing FAQ never creates a record', async ({ page }) => {
    const calls = await boot(page, { products: [], failKnowledgeRead: true });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'faq.csv', mimeType: 'text/csv',
      buffer: Buffer.from('pregunta,respuesta\n¿Dónde están?,Santiago\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/guardado|reintentar|error/i);
    expect(calls.knowledgeWrites()).toHaveLength(0);
  });

  test('failure reading existing hours never attempts selected-day mutation', async ({ page }) => {
    const calls = await boot(page, { products: [], failHoursRead: true });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'horas.csv', mimeType: 'text/csv',
      buffer: Buffer.from('dia,apertura,cierre\nLunes,09:00,18:00\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/guardado|reintentar|error/i);
    expect(calls.selectedPatch()).toBeNull();
    expect(calls.savedHours()).toBeNull();
  });

  test('a partial FAQ network failure reports that retry requires checking existing state', async ({ page }) => {
    const calls = await boot(page, { products: [], failKnowledgeWriteAt: 2 });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'faq.csv', mimeType: 'text/csv',
      buffer: Buffer.from('pregunta,respuesta\n¿Dónde están?,Santiago\n¿Hacen envíos?,Sí\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByLabel('Seleccionar sugerencia 1').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/Comprueba lo guardado antes de reintentar/i);
    expect(calls.knowledgeWrites()).toHaveLength(1);
    expect(calls.savedHours()).toBeNull();
  });

  test('unclosed CSV quotes and spreadsheet formulas produce warnings without writes', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles([
      { name: 'broken.csv', mimeType: 'text/csv',
        buffer: Buffer.from('pregunta,respuesta\n"¿Pregunta?","sin cerrar') },
      { name: 'formulas.csv', mimeType: 'text/csv',
        buffer: Buffer.from('pregunta,respuesta\n=FORMULA,Texto\n¿Otra pregunta?,=SUM(1)\n') }
    ]);
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-warning')).toContainText(/comillas sin cerrar|fórmula/i);
    await expect(page.getByTestId('free-setup-row-0')).toHaveCount(0);
    expect(calls.knowledgeWrites()).toHaveLength(0);
    expect(calls.savedHours()).toBeNull();
  });

  test('free proposal limit is visible instead of silently importing every CSV row', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    const source = 'pregunta,respuesta\n' +
      Array.from({ length: 102 }, (_, index) => 'Pregunta ' + index + ',Respuesta ' + index).join('\n');
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'faq.csv', mimeType: 'text/csv', buffer: Buffer.from(source)
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-row-99')).toBeVisible();
    await expect(page.getByTestId('free-setup-row-100')).toHaveCount(0);
    await expect(page.getByTestId('free-setup-warning')).toContainText(/100 sugerencias/i);
    expect(calls.knowledgeWrites()).toHaveLength(0);
  });

  test('quoted multiline FAQ and identical duplicate row keep explicit original evidence', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'faq.csv', mimeType: 'text/csv',
      buffer: Buffer.from('consulta frecuente;solucion\r\n"¿Dónde están?";"Santiago\nCentro"\r\n"¿Dónde están?";"Santiago\nCentro"\r\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-row-0')).toContainText('fila 2');
    await expect(page.getByTestId('free-setup-row-1')).toHaveCount(0);
    await expect(page.getByLabel('Respuesta sugerida 0')).toHaveValue('Santiago\nCentro');
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect.poll(() => calls.knowledgeWrites().length).toBe(1);
    expect(calls.knowledgeWrites()[0].content).toBe('Santiago\nCentro');
  });

  test('unsupported headers and oversized free file do not produce invented suggestions', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles([
      { name: 'unknown.csv', mimeType: 'text/csv', buffer: Buffer.from('a,b\nfoo,bar\n') },
      { name: 'large.csv', mimeType: 'text/csv', buffer: Buffer.alloc(1024 * 1024 + 1, 65) }
    ]);
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-warning')).toContainText(/1 MB/i);
    await expect(page.getByTestId('free-setup-row-0')).toHaveCount(0);
    expect(calls.knowledgeWrites()).toHaveLength(0);
    expect(calls.savedHours()).toBeNull();
  });

  test('metadata-only PDF can be reviewed and approved with no catalog services', async ({ page }) => {
    const calls = await boot(page, {
      products: [],
      businessFacts: { publicPhone: null, publicEmail: null, websiteUrl: null,
        addressLine: null, commune: "Providencia", city: "Santiago",
        region: null, countryCode: null, publicDescription: null,
        sourceName: "centro.pdf", hours: [] }
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'centro.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%test')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('business-import-facts')).toBeVisible();
    await expect(page.getByText('No hay productos o servicios listos para aplicar.')).toBeVisible();
    expect(calls.savedProfile()).toBeNull();
    expect(calls.applyCalls()).toBe(0);
    await page.getByRole('button', { name: 'Confirmar y guardar perfil' }).click();
    await expect(page.getByRole('button', { name: 'Perfil guardado' })).toBeVisible();
    expect(calls.savedProfile().commune).toBe('Providencia');
    expect(calls.applyCalls()).toBe(0);
  });



  test('Aurora document fields are suggestions until independently confirmed, preserving unrelated profile facts', async ({ page }) => {
    const calls = await boot(page, {
      currentProfile: { presetKey: "medical", publicEmail: "actual@example.test", defaultCurrency: "CLP",
        sellsServices: true, usesReservations: true },
      businessFacts: {
        publicPhone: null, publicEmail: null, websiteUrl: null, addressLine: null,
        commune: "Providencia", city: "Santiago", region: null, countryCode: null,
        publicDescription: "Centro médico ambulatorio", sourceName: "aurora.pdf",
        hours: [{ dayOfWeek: 1, openTime: "08:00", closeTime: "20:00" }]
      }
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'aurora.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%test')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('business-import-facts')).toBeVisible();
    await expect(page.getByLabel('Comuna')).toHaveValue('Providencia');
    await expect(page.getByLabel('Teléfono público')).toHaveValue('');
    expect(calls.savedProfile()).toBeNull();
    expect(calls.savedHours()).toBeNull();
    await page.getByLabel('Comuna').fill('Providencia Norte');
    await page.getByRole('button', { name: 'Confirmar y guardar perfil' }).click();
    await expect(page.getByRole('button', { name: 'Perfil guardado' })).toBeVisible();
    expect(calls.savedProfile().commune).toBe('Providencia Norte');
    expect(calls.savedProfile().publicEmail).toBe('actual@example.test');
    expect(calls.savedProfile().presetKey).toBe('medical');
    expect(calls.savedProfile().publicPhone).toBeNull();
    expect(calls.savedProfile().usesReservations).toBe(true);
    expect(calls.savedHours()).toBeNull();
    await page.getByRole('button', { name: 'Confirmar y guardar horarios' }).click();
    await expect(page.getByRole('button', { name: 'Horarios guardados' })).toBeVisible();
    expect(calls.savedHours().hours).toEqual([{ dayOfWeek: 1, openTime: '08:00', closeTime: '20:00' }]);
    expect(calls.applyCalls()).toBe(0);
  });

  test('file-import schedule never overwrites an existing weekly calendar', async ({ page }) => {
    const calls = await boot(page, {
      existingHours: [{ dayOfWeek: 2, openTime: '10:00', closeTime: '19:00' }],
      businessFacts: { publicPhone: null, publicEmail: null, websiteUrl: null, addressLine: null,
        commune: 'Providencia', city: null, region: null, countryCode: null, publicDescription: null,
        sourceName: 'aurora.pdf', hours: [{ dayOfWeek: 1, openTime: '08:00', closeTime: '20:00' }] }
    });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'aurora.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4\n%test')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await page.getByRole('button', { name: 'Confirmar y guardar horarios' }).click();
    await expect(page.getByText(/Ya existen horarios configurados/)).toBeVisible();
    expect(calls.savedHours()).toBeNull();
  });


  test('admin previews, reviews and explicitly applies without inventing service inventory', async ({ page }) => {
    const calls = await boot(page);

    await page.goto('/app/settings/import');

    await expect(page.getByRole('heading', { level: 1, name: 'Importar negocio' })).toBeVisible();
    await expect(page.getByText(/vista previa.*revisión.*aplicar/i)).toBeVisible();

    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'tarifario.csv',
      mimeType: 'text/csv',
      buffer: Buffer.from('Tipo;Nombre;Precio;Duración;SKU\nServicio;Consulta veterinaria;20000;45 min;\nProducto;Alimento premium;15990;;ALI-01')
    });

    expect(calls.previewCalls()).toBe(0);
    expect(calls.applyCalls()).toBe(0);

    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();

    await expect.poll(calls.previewCalls).toBe(1);
    expect(calls.applyCalls()).toBe(0);

    const serviceRow = page.getByTestId('business-import-row-0');
    await expect(serviceRow.getByLabel('Tipo')).toHaveValue('SERVICE');
    await expect(serviceRow.getByLabel('Duración')).toHaveValue('45');
    await expect(serviceRow.getByLabel('SKU')).toBeDisabled();
    await expect(serviceRow.getByLabel('Stock')).toBeDisabled();

    const productRow = page.getByTestId('business-import-row-1');
    await expect(productRow.getByLabel('Stock')).toHaveValue('');

    expect(calls.applyCalls()).toBe(0);
    await page.getByRole('button', { name: 'Importar al negocio' }).click();
    await expect.poll(calls.applyCalls).toBe(1);

    const payload = calls.applyPayload();
    expect(payload.products).toHaveLength(2);
    expect(payload.products[0]).toMatchObject({
      name: 'Consulta veterinaria',
      kind: 'SERVICE',
      durationMinutes: 45,
      sku: null,
      onHand: null
    });
    expect(payload.products[1]).toMatchObject({
      name: 'Alimento premium',
      kind: 'PRODUCT',
      sku: 'ALI-01',
      onHand: null
    });

    await expect(page.getByRole('heading', { name: 'Importación aplicada' })).toBeVisible();
    await expect(page.getByTestId('business-import-created')).toHaveText('2');
    await expect(page.getByTestId('business-import-updated')).toHaveText('0');
    await expect(page.getByTestId('business-import-inventory')).toHaveText('1');
  });

  test('non-admin cannot use the import surface', async ({ page }) => {
    let previewCalls = 0;
    await boot(page, {
      roles: ['OPERATOR'],
      permissions: ['BUSINESS_READ', 'CATALOG_READ', 'INVENTORY_READ']
    });

    await page.route('**/api/v1/onboarding/import/preview', route => {
      previewCalls += 1;
      return route.fulfill(json({}));
    });

    await page.goto('/app/settings/import');

    await expect(page).toHaveURL(/\/app\/settings\/?$/);
    expect(previewCalls).toBe(0);
  });

  test('legacy URL is compatibility-only and does not load retired page assets', async ({ page }) => {
    await boot(page);
    const requested = [];
    page.on('request', request => requested.push(new URL(request.url()).pathname));

    await page.goto('/business-import.html');

    await expect(page).toHaveURL(/\/app\/settings\/import\/?$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Importar negocio' })).toBeVisible();
    expect(requested).not.toContain('/business-import.js');
    expect(requested).not.toContain('/business-import.css');
  });

  test('Settings and Inventory point directly to the canonical React importer', async ({ page }) => {
    const root = path.resolve(__dirname, '..');
    const settings = fs.readFileSync(path.join(root, 'frontend/src/pages/Settings/SettingsPage.tsx'), 'utf8');
    const inventory = fs.readFileSync(path.join(root, 'frontend/src/pages/Inventory/InventoryPage.tsx'), 'utf8');

    expect(settings).not.toContain('/business-import.html');
    expect(inventory).not.toContain('/business-import.html');
    expect(settings).toContain('/app/settings/import');
    expect(inventory).toContain('/app/settings/import');
  });
  test('invalid TSV days and time windows remain warnings while only explicit valid rows can be approved', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles([
      { name: 'sin-datos.csv', mimeType: 'text/csv', buffer: Buffer.from('   \n') },
      { name: 'horas.tsv', mimeType: 'text/tab-separated-values',
        buffer: Buffer.from('dia\tapertura\tcierre\nFeriado\t09:00\t18:00\nLunes\t25:00\t18:00\nMartes\t18:00\t09:00\nMiércoles\t08:00\t12:00\n') }
    ]);
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-warning')).toContainText(/incompleto o inválido/i);
    await expect(page.getByTestId('free-setup-row-0')).toContainText('fila 5');
    await expect(page.getByTestId('free-setup-row-1')).toHaveCount(0);
    expect(calls.savedHours()).toBeNull();
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect.poll(() => calls.selectedPatch()).not.toBeNull();
    expect(calls.selectedPatch().hours).toEqual([
      { dayOfWeek: 3, openTime: '08:00', closeTime: '12:00' }
    ]);
  });

  test('incomplete FAQ rows are rejected, doubled quotes are parsed, and editing blank value blocks every write', async ({ page }) => {
    const calls = await boot(page, { products: [] });
    await page.goto('/app/settings/import');
    await page.getByLabel('Archivos del negocio').setInputFiles({
      name: 'faq.csv', mimeType: 'text/csv',
      buffer: Buffer.from('pregunta,respuesta\n¿Sin respuesta?,\n,Respuesta sin pregunta\n"¿Dicen ""hola""?","Sí"\n')
    });
    await page.getByRole('button', { name: 'Analizar y crear borrador' }).click();
    await expect(page.getByTestId('free-setup-warning')).toContainText(/incompleta o demasiado larga/i);
    await expect(page.getByTestId('free-setup-row-0')).toContainText('fila 4');
    await expect(page.getByLabel('Clave sugerida 0')).toHaveValue('¿Dicen "hola"?');
    await page.getByLabel('Seleccionar sugerencia 0').check();
    await page.getByLabel('Respuesta sugerida 0').fill('');
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect(page.getByRole('alert')).toContainText(/Completa los datos seleccionados/i);
    expect(calls.knowledgeWrites()).toHaveLength(0);
    await page.getByLabel('Respuesta sugerida 0').fill('Sí, por supuesto.');
    await page.getByRole('button', { name: 'Aprobar sugerencias seleccionadas' }).click();
    await expect.poll(() => calls.knowledgeWrites().length).toBe(1);
    expect(calls.knowledgeWrites()[0]).toMatchObject({
      title: '¿Dicen "hola"?', content: 'Sí, por supuesto.'
    });
  });

});
