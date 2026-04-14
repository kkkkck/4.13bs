#!/usr/bin/env node

const fs = require('fs');
const os = require('os');
const path = require('path');
const http = require('http');
const net = require('net');
const { spawn } = require('child_process');

function fail(message) {
  console.error(message);
  process.exit(1);
}

function detectBrowserPath() {
  const candidates = [
    process.env.APKG_BROWSER,
    'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Microsoft/Edge/Application/msedge.exe',
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe'
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (fs.existsSync(candidate)) {
      return candidate;
    }
  }

  fail('No supported browser found. Set APKG_BROWSER to Edge or Chrome.');
}

function get(url) {
  return new Promise((resolve, reject) => {
    http
      .get(url, (res) => {
        let data = '';
        res.on('data', (chunk) => {
          data += chunk;
        });
        res.on('end', () => resolve(data));
      })
      .on('error', reject);
  });
}

function findOpenPort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      server.close(() => resolve(port));
    });
    server.on('error', reject);
  });
}

function renderTemplate(template, fieldNames, fieldValues) {
  let rendered = template;
  fieldNames.forEach((name, index) => {
    const value = fieldValues[index] || '';
    rendered = rendered.split(`{{${name}}}`).join(value);
  });
  return `<html><head><meta charset="utf-8"><style>${templateInput.css}</style></head><body>${rendered}</body></html>`;
}

let templateInput = null;

function buildExtractionExpression() {
  return `(() => {
    const bodyChildren = [...document.body.childNodes]
      .map((node) => ({
        type: node.nodeType,
        text: (node.textContent || '').trim()
      }))
      .filter((item) => item.text);

    const questionNode = bodyChildren.find((item) =>
      item.type === 3 &&
      !item.text.includes('本anki手动整理') &&
      !item.text.includes('整理自【公众号：研小帮】')
    );

    const analysisNode = [...document.body.children]
      .map((node) => (node.innerText || '').trim())
      .find((text) => text.startsWith('【简析】') || text.startsWith('解析'));

    return {
      question: questionNode ? questionNode.text : '',
      options: [...document.querySelectorAll('#optionList li label')].map((node) => (node.innerText || '').trim()),
      answer: (document.getElementById('answer')?.innerText || '').trim(),
      analysis: analysisNode || '',
      bodyText: (document.body.innerText || '').trim(),
      errorText: document.body.innerText.includes('获取更多Anki资源请访问') ? document.body.innerText : ''
    };
  })()`;
}

async function main() {
  const inputPath = process.argv[2];
  if (!inputPath) {
    fail('Usage: node tools/decrypt_apkg_notes.js <input-json-path>');
  }

  templateInput = JSON.parse(fs.readFileSync(inputPath, 'utf8'));
  const browserPath = detectBrowserPath();
  const debugPort = await findOpenPort();
  const httpPort = await findOpenPort();
  const profileDir = fs.mkdtempSync(path.join(os.tmpdir(), 'apkg-browser-'));

  const server = http.createServer((req, res) => {
    const match = /^\/note\/(\d+)$/.exec(req.url || '');
    if (!match) {
      res.writeHead(404);
      res.end('not found');
      return;
    }

    const note = templateInput.notes[Number(match[1])];
    if (!note) {
      res.writeHead(404);
      res.end('note not found');
      return;
    }

    const html = renderTemplate(templateInput.template, templateInput.fieldNames, note.fields);
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(html);
  });

  await new Promise((resolve, reject) => {
    server.listen(httpPort, '127.0.0.1', resolve);
    server.on('error', reject);
  });

  const browser = spawn(
    browserPath,
    [
      '--headless=new',
      '--disable-gpu',
      '--no-first-run',
      '--no-default-browser-check',
      `--remote-debugging-port=${debugPort}`,
      `--user-data-dir=${profileDir}`,
      'about:blank'
    ],
    { stdio: 'ignore' }
  );

  const cleanup = () => {
    try {
      server.close();
    } catch {}
    try {
      if (!browser.killed) {
        browser.kill();
      }
    } catch {}
    try {
      fs.rmSync(profileDir, { recursive: true, force: true });
    } catch {}
  };

  try {
    let wsUrl = '';
    for (let attempt = 0; attempt < 60; attempt += 1) {
      try {
        const targets = JSON.parse(await get(`http://127.0.0.1:${debugPort}/json/list`));
        const page = targets.find((target) => target.type === 'page' && target.url === 'about:blank');
        if (page?.webSocketDebuggerUrl) {
          wsUrl = page.webSocketDebuggerUrl;
          break;
        }
      } catch {}
      await new Promise((resolve) => setTimeout(resolve, 250));
    }

    if (!wsUrl) {
      fail('Failed to connect to browser debugging target.');
    }

    const socket = new WebSocket(wsUrl);
    await new Promise((resolve, reject) => {
      socket.onopen = resolve;
      socket.onerror = reject;
    });

    let nextId = 1;
    const pending = new Map();

    socket.onmessage = (event) => {
      const message = JSON.parse(event.data);
      if (message.id && pending.has(message.id)) {
        const { resolve, reject } = pending.get(message.id);
        pending.delete(message.id);
        if (message.error) {
          reject(message.error);
        } else {
          resolve(message.result);
        }
      }
    };

    const send = (method, params = {}) =>
      new Promise((resolve, reject) => {
        const id = nextId++;
        pending.set(id, { resolve, reject });
        socket.send(JSON.stringify({ id, method, params }));
      });

    const evaluate = async (expression) => {
      const result = await send('Runtime.evaluate', {
        expression,
        awaitPromise: true,
        returnByValue: true
      });
      return result?.result?.value;
    };

    await send('Page.enable');
    await send('Runtime.enable');

    const extracted = [];
    const extractionExpression = buildExtractionExpression();

    for (let index = 0; index < templateInput.notes.length; index += 1) {
      await send('Page.navigate', { url: `http://127.0.0.1:${httpPort}/note/${index}` });

      let payload = null;
      let stableHits = 0;
      let lastSignature = '';
      const deadline = Date.now() + (templateInput.timeoutMs || 20000);

      while (Date.now() < deadline) {
        payload = await evaluate(extractionExpression);
        const signature = JSON.stringify(payload || {});
        const ready = payload && (payload.answer || payload.errorText);
        if (ready) {
          if (signature === lastSignature) {
            stableHits += 1;
          } else {
            stableHits = 1;
            lastSignature = signature;
          }

          if (stableHits >= 2) {
            break;
          }
        }
        await new Promise((resolve) => setTimeout(resolve, 250));
      }

      if (!payload || (!payload.answer && !payload.errorText)) {
        throw new Error(`Timed out decrypting note index ${index}`);
      }

      extracted.push({
        ...templateInput.notes[index].meta,
        ...payload
      });
    }

    socket.close();
    process.stdout.write(JSON.stringify({ notes: extracted }, null, 2));
  } finally {
    cleanup();
  }
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
