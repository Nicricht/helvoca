#!/usr/bin/env node
'use strict';

const { readdirSync, readFileSync, mkdirSync, writeFileSync, existsSync } = require('node:fs');
const path = require('node:path');
const { createCoverageMap, createCoverageSummary } = require('istanbul-lib-coverage');

const rawDir = path.join(process.cwd(), 'test-results', 'inventory-coverage-raw');
const outDir = path.join(process.cwd(), 'test-results', 'inventory-coverage');
const expected = [
  'src/pages/Inventory/InventoryPage.tsx',
  'src/pages/Inventory/InventoryIntro.tsx',
  'src/pages/Inventory/InventoryProductPresentation.tsx',
  'src/pages/Inventory/VariantOptionsEditor.tsx',
  'src/features/inventory/api.ts',
  'src/features/inventory/useInventoryWorkspace.ts'
];
const dimensions = ['statements', 'lines', 'branches', 'functions'];
const map = createCoverageMap({});
const samples = existsSync(rawDir)
  ? readdirSync(rawDir).filter(name => name.endsWith('.coverage.json')).sort()
  : [];
for (const filename of samples) {
  map.merge(JSON.parse(readFileSync(path.join(rawDir, filename), 'utf8')));
}
const sources = map.files();
const normalize = value => value.replaceAll('\\', '/').split('?')[0];
const details = [];
const missing = [];
const aggregate = createCoverageSummary();
let failed = samples.length === 0;

for (const wanted of expected) {
  const candidates = sources.filter(name => normalize(name).endsWith('/' + wanted));
  if (candidates.length !== 1) {
    missing.push({ path: wanted, reason: 'Expected exactly one instrumented source, got ' + candidates.length });
    failed = true;
    continue;
  }
  const data = map.fileCoverageFor(candidates[0]);
  const summary = data.toSummary();
  aggregate.merge(summary);
  const coverage = Object.fromEntries(dimensions.map(key => [key, summary.data[key].pct]));
  const uncoveredLines = data.getUncoveredLines().map(Number).slice(0, 100);
  const uncoveredFunctions = Object.entries(data.f)
    .filter(([, hits]) => hits === 0)
    .map(([id]) => ({ name: data.fnMap[id]?.name ?? id,
      line: data.fnMap[id]?.loc?.start?.line ?? null })).slice(0, 60);
  const uncoveredBranches = Object.entries(data.b).flatMap(([id, hits]) =>
    hits.flatMap((count, index) => count === 0
      ? [{ line: data.branchMap[id]?.line ?? null, index, type: data.branchMap[id]?.type ?? 'unknown' }]
      : [])).slice(0, 100);
  if (dimensions.some(key => coverage[key] < 100)) failed = true;
  details.push({ path: wanted, ...coverage, uncoveredLines, uncoveredFunctions, uncoveredBranches });
}

const report = {
  source: 'Istanbul-instrumented React source exercised by Playwright Chromium',
  capturedBrowserTests: samples.length,
  requiredCoveragePercent: 100,
  expectedSourceFiles: expected.length,
  observedSourceFiles: details.length,
  fileCoverage: details,
  missing,
  aggregate: aggregate.toJSON(),
  passed: !failed
};
mkdirSync(outDir, { recursive: true });
writeFileSync(path.join(outDir, 'summary.json'), JSON.stringify(report, null, 2) + '\n');

const markdown = [
  '# Inventory source coverage from browser execution',
  '',
  'Captured browser test snapshots: ' + samples.length,
  '',
  '| Source | Statements | Lines | Branches | Functions |',
  '| --- | ---: | ---: | ---: | ---: |',
  ...details.map(file => '| ' + file.path + ' | ' +
    dimensions.map(key => String(file[key]) + '%').join(' | ') + ' |'),
  ...missing.map(file => '| MISSING ' + file.path + ' | - | - | - | - |'),
  '',
  'Required: 100% for every applicable dimension and every affected source file.',
  'Result: ' + (failed ? 'FAIL' : 'PASS'),
  'Missing source is a failure; Playwright pass count is not a coverage percentage.'
];
writeFileSync(path.join(outDir, 'summary.md'), markdown.join('\n') + '\n');
console.log(markdown.join('\n'));
if (failed) process.exitCode = 1;
