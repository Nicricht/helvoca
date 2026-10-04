const fs = require('node:fs');
const { test, expect } = require('@playwright/test');

const read = path => fs.readFileSync(path, 'utf8');

test.describe('RecepVoz visual system v2 contract', () => {
  test('shared shell exposes the approved premium cockpit frame', async () => {
    const shell = read('frontend/src/components/AppShell/AppShell.tsx');
    const css = read('frontend/src/components/AppShell/AppShell.module.css');

    expect(shell).toContain('data-visual-system="v2"');
    expect(shell).toContain('Tu recepcionista IA');
    expect(shell).toContain('Tu plan');
    expect(shell).toContain('Minutos IA');
    expect(shell).toContain('Administrador');
    expect(shell).toContain('RecepVoz activo');

    expect(css).toContain('@keyframes shellActivePulse');
    expect(css).toContain('@keyframes shellGlowDrift');
  });

  test('global visual foundation owns reusable neon and motion tokens', async () => {
    const foundation = read('src/main/resources/static/frontend-foundation.css');

    for (const token of [
      '--rv-glow-cyan',
      '--rv-glow-violet',
      '--rv-glow-emerald',
      '--rv-motion-fast',
      '--rv-motion-medium',
      '--rv-motion-slow'
    ]) {
      expect(foundation).toContain(token);
    }

    expect(foundation).toContain('@media (prefers-reduced-motion: reduce)');
  });

  test('every principal screen declares its approved v2 visual identity', async () => {
    const screens = [
      ['frontend/src/pages/Home/HomePage.tsx', 'data-visual-page="home"'],
      ['frontend/src/pages/Agenda/AgendaPage.tsx', 'data-visual-page="agenda"', '/app/assets/recepvoz/v2/agenda/hero-calendar-robot.webp'],
      ['frontend/src/pages/Orders/OrdersPage.tsx', 'data-visual-page="operations"', '/app/assets/recepvoz/v2/operations/hero-order-robot.webp'],
      ['frontend/src/pages/Inventory/InventoryPage.tsx', 'data-visual-page="inventory"', '/app/assets/recepvoz/v2/inventory/hero-stock-robot.webp'],
      ['frontend/src/pages/Settings/SettingsPage.tsx', 'data-visual-page="settings"', '/app/assets/recepvoz/v2/settings/hero-ai-settings.webp'],
      ['frontend/src/pages/PlanConsumption/PlanConsumptionPage.tsx', 'data-visual-page="plan"', '/app/assets/recepvoz/v2/plan/hero-usage-robot.webp']
    ];

    for (const [path, ...needles] of screens) {
      const source = read(path);
      for (const needle of needles) expect(source, path).toContain(needle);
    }
  });

  test('settings mobile import action is presented as a polished compact control', async () => {
    const settings = read('frontend/src/pages/Settings/SettingsPage.tsx');
    const css = read('frontend/src/pages/Settings/SettingsPage.module.css');

    expect(settings).toContain('className={styles.headerAction}');
    expect(css).toMatch(/\.headerAction\s*\{[^}]*text-decoration:\s*none;/s);
    expect(css).toMatch(/@media\s*\(max-width:\s*430px\)[\s\S]*?\.headerAction\s*\{[^}]*width:\s*auto;/);
  });

  test('screen motion remains substantial but reduced-motion safe', async () => {
    const cssFiles = [
      'frontend/src/pages/Home/HomePage.module.css',
      'frontend/src/pages/Agenda/AgendaPage.module.css',
      'frontend/src/pages/Orders/OrdersPage.module.css',
      'frontend/src/pages/Inventory/InventoryPage.module.css',
      'frontend/src/pages/Settings/SettingsPage.module.css',
      'frontend/src/pages/PlanConsumption/PlanConsumptionPage.module.css'
    ];

    for (const path of cssFiles) {
      const css = read(path);
      expect(css, path).toContain('@media (prefers-reduced-motion: reduce)');
      expect(css, path).toMatch(/@keyframes\s+[A-Za-z0-9_-]+/);
    }
  });
});
