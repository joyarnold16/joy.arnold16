/*
 * Resolving Playwright.
 *
 * CI installs it into node_modules beside this file; some development machines
 * only have it globally, where a plain require() cannot see it. Try normal
 * resolution first and fall back to the global root - never hard-code either
 * path, which is how this broke the first time.
 */
'use strict';

const path = require('path');
const { execSync } = require('child_process');

function load() {
  try {
    return require('playwright');
  } catch (e) {
    if (e.code !== 'MODULE_NOT_FOUND') throw e;
  }
  try {
    const root = execSync('npm root -g', { encoding: 'utf8', stdio: ['ignore', 'pipe', 'ignore'] }).trim();
    return require(path.join(root, 'playwright'));
  } catch (e) {
    throw new Error(
      'Playwright is not installed.\n' +
      '  npm install --no-save playwright && npx playwright install chromium');
  }
}

module.exports = load();
