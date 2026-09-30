#!/usr/bin/env node
'use strict';

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..', '..');
const STATIC = path.join(ROOT, 'src', 'main', 'resources', 'static');
const FOUNDATION = path.join(STATIC, 'frontend-foundation.css');
const MANIFEST = path.join(ROOT, 'docs', 'frontend', 'FRAME_CONTRACT.json');
const CONTRACT = path.join(ROOT, 'docs', 'frontend', 'FRAME_CONTRACT.md');
const AGENTS = path.join(ROOT, 'AGENTS.md');
const PR_TEMPLATE = path.join(ROOT, '.github', 'pull_request_template.md');

function fail(message) {
  console.error('[frontend-frame-contract] ' + message);
  process.exitCode = 1;
}

function read(file) {
  if (!fs.existsSync(file)) {
    fail('Missing required file: ' + path.relative(ROOT, file));
    return '';
  }
  return fs.readFileSync(file, 'utf8');
}

function walkCss(dir) {
  if (!fs.existsSync(dir)) return [];
  const files = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) files.push(...walkCss(full));
    else if (entry.isFile() && entry.name.endsWith('.css')) files.push(full);
  }
  return files;
}

const contractText = read(CONTRACT);
const manifestText = read(MANIFEST);
const foundation = read(FOUNDATION);
const agents = read(AGENTS);
const prTemplate = read(PR_TEMPLATE);

let manifest = null;
try {
  manifest = JSON.parse(manifestText);
} catch (error) {
  fail('FRAME_CONTRACT.json is not valid JSON: ' + error.message);
}

if (manifest) {
  if (manifest.protectedNamespace !== '--rv-frame-') {
    fail('protectedNamespace must remain --rv-frame-');
  }

  const expectedGeometry = {
    sidebarWidth: 220,
    topbarHeight: 64,
    contentMax: 1420,
    gutterDesktop: 24,
    gutterCompact: 20,
    gutterMobile: 12
  };

  for (const [key, value] of Object.entries(expectedGeometry)) {
    if (manifest.geometry?.[key] !== value) {
      fail(`geometry.${key} must remain ${value}; use FRAME CHANGE: YES for intentional changes`);
    }
  }

  const expectedBreakpoints = { desktopMin: 981, compactMax: 980, mobileMax: 620 };
  for (const [key, value] of Object.entries(expectedBreakpoints)) {
    if (manifest.breakpoints?.[key] !== value) {
      fail(`breakpoints.${key} must remain ${value}; use FRAME CHANGE: YES for intentional changes`);
    }
  }
}

const requiredTokens = [
  '--rv-frame-sidebar-width: 220px',
  '--rv-frame-topbar-height: 64px',
  '--rv-frame-content-max: 1420px',
  '--rv-frame-gutter-desktop: 24px',
  '--rv-frame-gutter-compact: 20px',
  '--rv-frame-gutter-mobile: 12px'
];

for (const token of requiredTokens) {
  if (!foundation.includes(token)) fail('Canonical foundation is missing protected token: ' + token);
}

const protectedSelectors = [
  '.rv-frame-shell',
  '.rv-frame-topbar',
  '.rv-page-frame',
  '.rv-page-header',
  '.rv-page-grid',
  '.rv-frame-slot'
];

for (const selector of protectedSelectors) {
  if (!foundation.includes(selector)) fail('Canonical foundation is missing protected selector: ' + selector);
}

for (const cssFile of walkCss(STATIC)) {
  if (path.resolve(cssFile) === path.resolve(FOUNDATION)) continue;
  const css = fs.readFileSync(cssFile, 'utf8');
  const relative = path.relative(ROOT, cssFile);

  if (/--rv-frame-[a-z0-9-]+\s*:/i.test(css)) {
    fail(relative + ' defines a protected --rv-frame-* token');
  }

  if (/\.rv-(?:frame-shell|frame-topbar|page-frame|page-header|page-grid|frame-slot)\b/.test(css)) {
    fail(relative + ' defines a protected frame selector');
  }
}

for (const phrase of ['FRAME CHANGE: NO', 'FRAME CHANGE: YES', 'Zonas protegidas', 'Zonas editables']) {
  if (!contractText.includes(phrase)) fail('FRAME_CONTRACT.md is missing: ' + phrase);
}

for (const phrase of ['docs/frontend/FRAME_CONTRACT.md', 'FRAME CHANGE: NO', 'FRAME CHANGE: YES']) {
  if (!agents.includes(phrase)) fail('AGENTS.md is missing frontend frame guardrail: ' + phrase);
}

for (const phrase of ['## Frontend frame contract', 'FRAME CHANGE:', 'Editable slot(s):', 'Cross-screen evidence']) {
  if (!prTemplate.includes(phrase)) fail('PR template is missing frame field: ' + phrase);
}

if (process.exitCode) process.exit(process.exitCode);

console.log('[frontend-frame-contract] PASS');
console.log('[frontend-frame-contract] protected namespace: --rv-frame-*');
console.log('[frontend-frame-contract] version: ' + (manifest?.version || 'unknown'));
