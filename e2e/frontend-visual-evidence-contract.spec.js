const { test, expect } = require('@playwright/test');
const fs = require('node:fs');

const read = path => fs.readFileSync(path, 'utf8');

test('visual docs describe the current owner home and sales experience', async () => {
  const matrix = read('docs/frontend/VISUAL_ACCEPTANCE_MATRIX.md');
  const blueprints = read('docs/frontend/SCREEN_BLUEPRINTS.md');

  for (const document of [matrix, blueprints]) {
    expect(document).toContain('Qué está pasando hoy');
    expect(document).toContain('Necesita tu atención');
    expect(document).toContain('Actividad reciente');
    expect(document).toContain('Accesos rápidos');
    expect(document).toContain('Ventas y rendimiento');
  }

  expect(matrix).not.toContain('four KPI cards form one coherent first row on desktop');
});

test('release candidate captures exact-head visual evidence for every canonical viewport', async () => {
  const rc = read('e2e/frontend-release-candidate.spec.js');

  expect(rc).toContain('visual-evidence');
  expect(rc).toContain('process.env.VISUAL_EVIDENCE_SHA');
  expect(rc).not.toContain("process.env.GITHUB_SHA || 'local'");

  for (const width of [1536, 1440, 1366, 1280, 768, 390]) {
    expect(rc).toContain(`width: ${width}`);
  }

  for (const route of [
    '/app',
    '/app/agenda',
    '/app/orders',
    '/app/inventory',
    '/app/settings',
    '/app/plan'
  ]) {
    expect(rc).toContain(`'${route}'`);
  }

  for (const retiredRoute of [
    '/conversations.html',
    '/inventory.html',
    '/settings.html',
    '/account.html',
    '/simulator.html'
  ]) {
    expect(rc).not.toContain(`route: '${retiredRoute}'`);
  }
});

test('full CI always uploads visual evidence even when the test suite passes', async () => {
  const workflow = read('.github/workflows/ci.yml');

  expect(workflow).toContain('- name: Upload frontend visual evidence');
  expect(workflow).toMatch(/- name: Upload frontend visual evidence\n\s+if: always\(\)/);
  expect(workflow).toContain('name: frontend-visual-evidence');
  expect(workflow).toContain('test-results/visual-evidence/**');
  expect(workflow).toContain('VISUAL_EVIDENCE_SHA: ${{ github.event.pull_request.head.sha || github.sha }}');
});
