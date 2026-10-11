#!/usr/bin/env node
'use strict';

const { readdirSync, readFileSync, mkdirSync, writeFileSync, existsSync } = require('node:fs');
const path = require('node:path');
const { createCoverageMap, createCoverageSummary } = require('istanbul-lib-coverage');
const { createSourceMapStore } = require('istanbul-lib-source-maps');

const rawDir = path.join(process.cwd(), '.home-coverage-raw');
const outDir = path.join(process.cwd(), 'test-results', 'home-coverage');
const expected = [
  'src/pages/Home/HomePage.tsx',
  'src/pages/Home/HomeAttentionRows.tsx',
  'src/features/operations/api.ts'
];
const dimensions = ['statements', 'lines', 'branches', 'functions'];

// Strict Home instrumentation: no exceptions.
const requiredCoveragePercent = 100;
const map = createCoverageMap({});
const samples = existsSync(rawDir)
  ? readdirSync(rawDir).filter(name => name.endsWith('.coverage.json')).sort()
  : [];
for (const filename of samples) {
  map.merge(JSON.parse(readFileSync(path.join(rawDir, filename), 'utf8')));
}
const unavailableSamples = existsSync(rawDir)
  ? readdirSync(rawDir).filter(name => name.endsWith('.unavailable.json')).sort()
  : [];
const manifest = existsSync(rawDir)
  ? readdirSync(rawDir).filter(name => name.endsWith('.record.json')).sort()
  : [];
const records = manifest.map(filename =>
  JSON.parse(readFileSync(path.join(rawDir, filename), 'utf8')));
const recordedSuites = new Set(records.map(record => record.suite));
const archivedDocuments = records.reduce((sum, record) =>
  sum + (record.archivedDocuments || 0), 0);
const archivedFailures = records.filter(record => record.archiveFailed)
  .map(record => ({ suite: record.suite, test: record.test }));
const requiredSuites = ['react-home.spec.js'];
const missingSuites = requiredSuites.filter(name => !recordedSuites.has(name));
const discoveredManifestPath = path.join(process.cwd(), '.home-coverage-tests.txt');
const discoveredManifest = existsSync(discoveredManifestPath)
  ? readFileSync(discoveredManifestPath, 'utf8')
  : '';
const discoveredTotals = discoveredManifest.match(/Total:\s*(\d+)\s+tests?\s+in\s+(\d+)\s+files?/);
const discoveredTests = discoveredTotals ? Number(discoveredTotals[1]) : null;
const discoveredFiles = discoveredTotals ? Number(discoveredTotals[2]) : null;
async function certifyCoverage() {
  // vite-plugin-istanbul measures transpiled React code and attaches
  // inputSourceMap to each file. Always remap to the actual TS/TSX original
  // before reporting line/branch/function coverage or enforcing 100%.
  const instrumentedSources = map.files();
  const inputSourceMaps = instrumentedSources.filter(file =>
    Boolean(map.fileCoverageFor(file).data.inputSourceMap)
  );
  const sourceMapStore = createSourceMapStore({ baseDir: process.cwd() });
  let remapped;
  try {
    remapped = await sourceMapStore.transformCoverage(map);
  } finally {
    sourceMapStore.dispose();
  }
  const sources = remapped.files();
const normalize = value => value.replaceAll('\\', '/').split('?')[0];
const details = [];
const missing = [];
const aggregate = createCoverageSummary();
let failed = inputSourceMaps.length !== expected.length ||
  archivedDocuments === 0 || archivedFailures.length > 0 ||
  samples.length === 0 ||
  missingSuites.length > 0 ||
  manifest.length !== samples.length + unavailableSamples.length ||
  discoveredTests === null ||
  discoveredTests !== manifest.length ||
  discoveredFiles !== requiredSuites.length;

for (const wanted of expected) {
  const candidates = sources.filter(name => normalize(name).endsWith('/' + wanted));
  if (candidates.length !== 1) {
    missing.push({ path: wanted, reason: 'Expected exactly one instrumented source, got ' + candidates.length });
    failed = true;
    continue;
  }
  const data = remapped.fileCoverageFor(candidates[0]);
  const summary = data.toSummary();
  aggregate.merge(summary);
  const coverage = Object.fromEntries(dimensions.map(key => [key, summary.data[key].pct]));
  const uncoveredLines = data.getUncoveredLines().map(Number).slice(0, 100);
  const uncoveredFunctions = Object.entries(data.f)
    .filter(([, hits]) => hits === 0)
    .map(([id]) => ({ name: data.fnMap[id]?.name ?? id,
      line: data.fnMap[id]?.loc?.start?.line ?? null })).slice(0, 60);
  const uncoveredBranches = Object.entries(data.b).flatMap(([id, hits]) =>
    hits.flatMap((count, index) => {
      if (count !== 0) return [];
      const branch = data.branchMap[id];
      const location = branch?.locations?.[index] ?? branch?.loc;
      return [{
        line: location?.start?.line ?? branch?.line ?? null,
        column: location?.start?.column ?? null,
        index,
        type: branch?.type ?? 'unknown'
      }];
    })).slice(0, 100);
  if (dimensions.some(key => coverage[key] < requiredCoveragePercent)) failed = true;
  details.push({ path: wanted, ...coverage, uncoveredLines, uncoveredFunctions, uncoveredBranches });
}

const report = {
  source: 'Istanbul browser counters remapped through embedded Vite source maps to original TS/TSX',
  originalSourceRemapping: true,
  archivedPriorNavigations: archivedDocuments,
  archivedFailures,
  instrumentedSourceFilesWithInputSourceMaps: inputSourceMaps.length,
  mappedOriginalSourceFiles: sources.length,
  capturedBrowserTests: samples.length,
  unavailableBrowserTests: unavailableSamples.length,
  totalRecordedTests: manifest.length,
  discoveredPlaywrightTests: discoveredTests,
  discoveredPlaywrightFiles: discoveredFiles,
  recordedSuites: [...recordedSuites].sort(),
  missingSuites,
  requiredCoveragePercent,
  coverageExceptionsAllowed: false,
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
  '# Home source coverage from browser execution',
  '',
  'Instrumented snapshots: ' + samples.length,
  'Previously navigated document snapshots merged: ' + archivedDocuments,
  'Source archive failures: ' + archivedFailures.length,
  'Original source remapping: ENABLED (embedded Vite inputSourceMap)',
  'Instrumented files with source maps: ' + inputSourceMaps.length,
  'Mapped original files: ' + sources.length,
  'Unavailable at teardown: ' + unavailableSamples.length,
  'Total browser tests recorded: ' + manifest.length,
  'Playwright-discovered tests: ' + (discoveredTests ?? 'MISSING'),
  'Playwright-discovered spec files: ' + (discoveredFiles ?? 'MISSING'),
  'Suites captured: ' + [...recordedSuites].sort().join(', '),
  'Missing suites: ' + (missingSuites.length ? missingSuites.join(', ') : 'none'),
  '',
  '| Source | Statements | Lines | Branches | Functions |',
  '| --- | ---: | ---: | ---: | ---: |',
  ...details.map(file => '| ' + file.path + ' | ' +
    dimensions.map(key => String(file[key]) + '%').join(' | ') + ' |'),
  ...missing.map(file => '| MISSING ' + file.path + ' | - | - | - | - |'),
  '',
  'Required: ' + requiredCoveragePercent + '% for every applicable dimension of EACH affected source file.',
  'Exception: none, every Home source and dimension requires 100%',
  'Result: ' + (failed ? 'FAIL' : 'PASS'),
  'Missing source is a failure; Playwright pass count is not a coverage percentage.'
];
writeFileSync(path.join(outDir, 'summary.md'), markdown.join('\n') + '\n');
console.log(markdown.join('\n'));
if (failed) process.exitCode = 1;

}
certifyCoverage().catch(error => {
  console.error('Home original-source coverage remapping failed:', error);
  process.exitCode = 1;
});
