import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import ts from 'typescript';

const source = await readFile(new URL('../src/services/chunk-recovery.ts', import.meta.url), 'utf8');
const compiled = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText;
const exports = {};
new Function('exports', compiled)(exports);
const { recoverChunkLoad } = exports;

function fixture() {
  const storage = new Map();
  const calls = { reloads: 0, urls: [] };
  const browser = {
    sessionStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    history: { replaceState: (_state, _title, url) => calls.urls.push(url) },
    location: { pathname: '/', search: '?demo=1', reload: () => calls.reloads++ },
  };
  return { browser, calls };
}

test('reloads a missing module at the requested route, with a loop guard', () => {
  const { browser, calls } = fixture();
  const error = new Error('Failed to fetch dynamically imported module: /chunk-old.js');
  assert.equal(recoverChunkLoad(error, '/screener', browser, 100_000), true);
  assert.deepEqual(calls.urls, ['/?demo=1#/screener']);
  assert.equal(recoverChunkLoad(error, '/screener', browser, 101_000), false);
  assert.equal(calls.reloads, 1);
  assert.equal(recoverChunkLoad(error, '/screener', browser, 161_000), true);
});

test('does not reload unrelated API or navigation errors', () => {
  const { browser, calls } = fixture();
  assert.equal(recoverChunkLoad(new Error('HTTP 401'), '/screener', browser), false);
  assert.equal(calls.reloads, 0);
});

test('does not risk a reload loop when storage is unavailable', () => {
  const { browser, calls } = fixture();
  browser.sessionStorage.setItem = () => { throw new Error('Storage blocked'); };
  assert.equal(recoverChunkLoad(new Error('Importing a module script failed'), '/screener', browser), false);
  assert.equal(calls.reloads, 0);
});