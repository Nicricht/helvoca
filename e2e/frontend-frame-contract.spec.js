const fs = require('fs');
const path = require('path');
const { test, expect } = require('@playwright/test');

const ROOT = path.resolve(__dirname, '..');
const STATIC = path.join(ROOT, 'src', 'main', 'resources', 'static');
const DOCS = path.join(ROOT, 'docs', 'frontend');

function read(relativePath) {
  return fs.readFileSync(path.join(ROOT, relativePath), 'utf8');
}

test.describe('RecepVoz frontend frame contract', () => {
  test('publishes a machine-readable frame seal and human contract', async () => {
    const manifestPath = path.join(DOCS, 'FRAME_CONTRACT.json');
    const documentPath = path.join(DOCS, 'FRAME_CONTRACT.md');

    expect(fs.existsSync(manifestPath)).toBeTruthy();
    expect(fs.existsSync(documentPath)).toBeTruthy();

    const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));
    const document = fs.readFileSync(documentPath, 'utf8');

    expect(manifest.version).toMatch(/^1\./);
    expect(manifest.protectedNamespace).toBe('--rv-frame-');
    expect(manifest.breakpoints).toEqual({
      desktopMin: 981,
      compactMax: 980,
      mobileMax: 620
    });
    expect(manifest.geometry.sidebarWidth).toBe(220);
    expect(manifest.geometry.topbarHeight).toBe(64);
    expect(manifest.geometry.contentMax).toBe(1420);
    expect(Object.keys(manifest.screens)).toEqual(expect.arrayContaining([
      'dashboard',
      'conversations',
      'agenda',
      'clients',
      'inventory',
      'settings',
      'billing'
    ]));

    expect(document).toContain('FRAME CHANGE: NO');
    expect(document).toContain('FRAME CHANGE: YES');
    expect(document).toContain('Zonas protegidas');
    expect(document).toContain('Zonas editables');
    expect(document).toContain('Dashboard');
    expect(document).toContain('Conversaciones');
    expect(document).toContain('Inventario');
  });

  test('repository guardrails default frontend work to local frame changes', async () => {
    const agents = read('AGENTS.md');

    expect(agents).toContain('docs/frontend/FRAME_CONTRACT.md');
    expect(agents).toContain('FRAME CHANGE: NO');
    expect(agents).toContain('FRAME CHANGE: YES');
    expect(agents).toContain('editable slot');
  });

  test('PR template requires an explicit frame-change seal', async () => {
    const template = read('.github/pull_request_template.md');

    expect(template).toContain('## Frontend frame contract');
    expect(template).toContain('FRAME CHANGE:');
    expect(template).toContain('Editable slot(s):');
    expect(template).toContain('Global frame unchanged:');
    expect(template).toContain('Cross-screen evidence');
  });

  test('Fast Gate enforces the frame seal before Full Gate', async () => {
    const fastGate = read('scripts/ci/fast-gate.sh');
    const checker = path.join(ROOT, 'scripts', 'ci', 'check-frontend-frame-contract.js');

    expect(fs.existsSync(checker)).toBeTruthy();
    expect(fastGate).toContain('check-frontend-frame-contract.js');
  });

  test('canonical foundation exclusively owns the protected frame namespace', async () => {
    const foundation = read('src/main/resources/static/frontend-foundation.css');

    for (const token of [
      '--rv-frame-sidebar-width',
      '--rv-frame-topbar-height',
      '--rv-frame-content-max',
      '--rv-frame-gutter-desktop',
      '--rv-frame-gutter-mobile'
    ]) {
      expect(foundation, token).toContain(token);
    }

    for (const selector of [
      '.rv-frame-shell',
      '.rv-frame-topbar',
      '.rv-page-frame',
      '.rv-page-header',
      '.rv-page-grid',
      '.rv-frame-slot'
    ]) {
      expect(foundation, selector).toContain(selector);
    }

    const cssFiles = fs.readdirSync(STATIC).filter(file => file.endsWith('.css') && file !== 'frontend-foundation.css');
    const violations = [];

    for (const file of cssFiles) {
      const css = fs.readFileSync(path.join(STATIC, file), 'utf8');
      if (/--rv-frame-[a-z0-9-]+\s*:/i.test(css)) violations.push(file + ': protected token');
      if (/\.rv-(?:frame-shell|frame-topbar|page-frame|page-header|page-grid|frame-slot)\b/.test(css)) {
        violations.push(file + ': protected selector');
      }
    }

    expect(violations).toEqual([]);
  });


  test('desktop authenticated rail paints readable navigation labels explicitly', async () => {
    const foundation = read('src/main/resources/static/frontend-foundation.css');

    expect(foundation).toContain('/* Desktop authenticated rail label visibility contract. */');
    expect(foundation).toContain('color:var(--rv-text-secondary)');
    expect(foundation).toContain('opacity:1');
    expect(foundation).toContain('visibility:visible');
    expect(foundation).toContain('text-indent:0');
    expect(foundation).toContain('-webkit-text-fill-color:currentColor');
  });

  test('frame primitives stay contained at canonical viewports', async ({ page }) => {
    const foundation = read('src/main/resources/static/frontend-foundation.css');

    for (const viewport of [
      { width: 1536, height: 950 },
      { width: 1440, height: 900 },
      { width: 1366, height: 768 },
      { width: 1280, height: 720 },
      { width: 768, height: 1024 },
      { width: 390, height: 844 }
    ]) {
      await page.setViewportSize(viewport);
      await page.setContent(`
        <style>${foundation}</style>
        <div class="rv-frame-shell">
          <header class="rv-frame-topbar">
            <strong>RecepVoz</strong>
            <button>Acción</button>
          </header>
          <main class="rv-page-frame">
            <header class="rv-page-header">
              <div><h1>Pantalla</h1><p>Propósito de la pantalla.</p></div>
              <button>Acción principal</button>
            </header>
            <section class="rv-page-grid">
              <article class="rv-frame-slot">Contenido A</article>
              <article class="rv-frame-slot">Contenido B</article>
            </section>
          </main>
        </div>
      `);

      const layout = await page.evaluate(() => {
        const root = getComputedStyle(document.documentElement);
        const frame = document.querySelector('.rv-page-frame').getBoundingClientRect();
        const topbar = document.querySelector('.rv-frame-topbar').getBoundingClientRect();
        return {
          clientWidth: document.documentElement.clientWidth,
          scrollWidth: document.documentElement.scrollWidth,
          frameLeft: frame.left,
          frameRight: frame.right,
          topbarHeight: topbar.height,
          contentMax: Number.parseFloat(root.getPropertyValue('--rv-frame-content-max')),
          desktopGutter: Number.parseFloat(root.getPropertyValue('--rv-frame-gutter-desktop')),
          mobileGutter: Number.parseFloat(root.getPropertyValue('--rv-frame-gutter-mobile'))
        };
      });

      expect(layout.scrollWidth).toBeLessThanOrEqual(layout.clientWidth + 1);
      expect(layout.frameLeft).toBeGreaterThanOrEqual(viewport.width <= 620 ? 12 : 20);
      expect(layout.frameRight).toBeLessThanOrEqual(layout.clientWidth - (viewport.width <= 620 ? 12 : 20));
      expect(layout.topbarHeight).toBeGreaterThanOrEqual(64);
      expect(layout.contentMax).toBe(1420);
      expect(layout.desktopGutter).toBe(24);
      expect(layout.mobileGutter).toBe(12);
    }
  });
});
