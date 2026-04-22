#!/usr/bin/env node

import { performance } from 'node:perf_hooks';

const args = parseArgs(process.argv.slice(2));

const baseUrl = stripTrailingSlash(args.url || process.env.LOAD_TEST_BASE_URL || 'http://127.0.0.1:8080');
const scenario = args.scenario || process.env.LOAD_TEST_SCENARIO || 'health';
const totalRequests = toPositiveInt(args.requests || process.env.LOAD_TEST_REQUESTS, 1000);
const concurrency = Math.min(totalRequests, toPositiveInt(args.concurrency || process.env.LOAD_TEST_CONCURRENCY, 50));
const timeoutMs = toPositiveInt(args.timeout || process.env.LOAD_TEST_TIMEOUT_MS, 10000);
const account = args.account || process.env.LOAD_TEST_ACCOUNT || 'admin@example.com';
const password = args.password || process.env.LOAD_TEST_PASSWORD || 'admin123';
const categoryId = args.categoryId || process.env.LOAD_TEST_CATEGORY_ID || '1';
const pageSize = args.pageSize || process.env.LOAD_TEST_PAGE_SIZE || '20';

if (typeof fetch !== 'function') {
  throw new Error('This script requires Node.js 18+ because it uses global fetch.');
}

let bearerToken = args.token || process.env.LOAD_TEST_TOKEN || '';
if (scenario === 'read' && !bearerToken) {
  bearerToken = await loginForToken();
}

let cursor = 0;
const results = [];
const startedAt = performance.now();

await Promise.all(
  Array.from({ length: concurrency }, (_, workerId) => runWorker(workerId))
);

const durationMs = performance.now() - startedAt;
printSummary(results, durationMs);

async function runWorker(workerId) {
  while (true) {
    const requestId = cursor;
    cursor += 1;
    if (requestId >= totalRequests) {
      return;
    }

    const started = performance.now();
    try {
      const response = await runScenario(requestId, workerId);
      results.push({
        ok: response.ok,
        status: response.status,
        appCode: response.appCode,
        latencyMs: performance.now() - started,
        error: response.ok ? '' : response.error || response.message || ''
      });
    } catch (error) {
      results.push({
        ok: false,
        status: 0,
        appCode: 0,
        latencyMs: performance.now() - started,
        error: error instanceof Error ? error.message : String(error)
      });
    }
  }
}

async function runScenario(requestId) {
  if (scenario === 'health') {
    return httpRequest('GET', '/api/health');
  }

  if (scenario === 'login') {
    return httpRequest('POST', '/api/auth/login', {
      body: { account, password }
    });
  }

  if (scenario === 'read') {
    const paths = [
      '/api/categories',
      `/api/questions?categoryId=${encodeURIComponent(categoryId)}&page=1&size=${encodeURIComponent(pageSize)}`,
      '/api/statistics/overview'
    ];
    return httpRequest('GET', paths[requestId % paths.length], {
      headers: { Authorization: `Bearer ${bearerToken}` }
    });
  }

  if (scenario === 'endpoint') {
    const method = (args.method || process.env.LOAD_TEST_METHOD || 'GET').toUpperCase();
    const path = args.path || process.env.LOAD_TEST_PATH || '/api/health';
    const rawBody = args.body || process.env.LOAD_TEST_BODY || '';
    return httpRequest(method, path, {
      body: rawBody ? JSON.parse(rawBody) : undefined,
      headers: bearerToken ? { Authorization: `Bearer ${bearerToken}` } : undefined
    });
  }

  throw new Error(`Unknown scenario: ${scenario}`);
}

async function loginForToken() {
  const response = await httpRequest('POST', '/api/auth/login', {
    body: { account, password }
  });

  if (!response.ok || !response.json?.data?.token) {
    throw new Error(`Login failed before read scenario: HTTP ${response.status}, appCode ${response.appCode}`);
  }

  return response.json.data.token;
}

async function httpRequest(method, path, options = {}) {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  const headers = {
    Accept: 'application/json',
    ...(options.headers || {})
  };

  let body;
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(options.body);
  }

  try {
    const response = await fetch(`${baseUrl}${path}`, {
      method,
      headers,
      body,
      signal: controller.signal
    });
    const text = await response.text();
    const json = parseJson(text);
    const appCode = typeof json?.code === 'number' ? json.code : undefined;
    const ok = response.status >= 200 && response.status < 300 && (appCode === undefined || appCode === 200);

    return {
      ok,
      status: response.status,
      appCode,
      json,
      message: json?.message,
      error: text.slice(0, 300)
    };
  } finally {
    clearTimeout(timer);
  }
}

function printSummary(items, durationMs) {
  const successCount = items.filter((item) => item.ok).length;
  const failedCount = items.length - successCount;
  const latencies = items.map((item) => item.latencyMs).sort((left, right) => left - right);
  const statusCounts = new Map();
  const appCodeCounts = new Map();

  for (const item of items) {
    statusCounts.set(item.status, (statusCounts.get(item.status) || 0) + 1);
    if (item.appCode !== undefined) {
      appCodeCounts.set(item.appCode, (appCodeCounts.get(item.appCode) || 0) + 1);
    }
  }

  console.log(`Load test scenario: ${scenario}`);
  console.log(`Target: ${baseUrl}`);
  console.log(`Requests: ${items.length}, concurrency: ${concurrency}, duration: ${(durationMs / 1000).toFixed(2)}s`);
  console.log(`Throughput: ${(items.length / (durationMs / 1000)).toFixed(2)} req/s`);
  console.log(`Success: ${successCount}, failed: ${failedCount}`);
  console.log(`Latency ms: avg=${average(latencies).toFixed(2)}, p50=${percentile(latencies, 50).toFixed(2)}, p95=${percentile(latencies, 95).toFixed(2)}, p99=${percentile(latencies, 99).toFixed(2)}, max=${(latencies.at(-1) || 0).toFixed(2)}`);
  console.log(`HTTP statuses: ${formatCounts(statusCounts)}`);
  if (appCodeCounts.size > 0) {
    console.log(`App codes: ${formatCounts(appCodeCounts)}`);
  }

  const failures = items.filter((item) => !item.ok).slice(0, 5);
  if (failures.length > 0) {
    console.log('Sample failures:');
    for (const failure of failures) {
      console.log(`- HTTP ${failure.status}, appCode ${failure.appCode || '-'}, ${failure.error || 'no error body'}`);
    }
  }

  process.exitCode = failedCount > 0 ? 1 : 0;
}

function percentile(sortedValues, p) {
  if (sortedValues.length === 0) {
    return 0;
  }
  const index = Math.ceil((p / 100) * sortedValues.length) - 1;
  return sortedValues[Math.max(0, Math.min(index, sortedValues.length - 1))];
}

function average(values) {
  if (values.length === 0) {
    return 0;
  }
  return values.reduce((sum, value) => sum + value, 0) / values.length;
}

function formatCounts(counts) {
  return Array.from(counts.entries())
    .sort(([left], [right]) => Number(left) - Number(right))
    .map(([key, value]) => `${key}:${value}`)
    .join(', ');
}

function parseJson(text) {
  if (!text) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

function parseArgs(argv) {
  const parsed = {};
  for (let index = 0; index < argv.length; index += 1) {
    const arg = argv[index];
    if (!arg.startsWith('--')) {
      continue;
    }
    const [rawKey, rawValue] = arg.slice(2).split('=');
    if (rawValue !== undefined) {
      parsed[rawKey] = rawValue;
      continue;
    }
    const next = argv[index + 1];
    if (next && !next.startsWith('--')) {
      parsed[rawKey] = next;
      index += 1;
    } else {
      parsed[rawKey] = 'true';
    }
  }
  return parsed;
}

function toPositiveInt(value, fallback) {
  const parsed = Number.parseInt(value, 10);
  return Number.isFinite(parsed) && parsed > 0 ? parsed : fallback;
}

function stripTrailingSlash(value) {
  return value.replace(/\/+$/, '');
}
