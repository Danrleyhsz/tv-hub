'use strict';

// O token do pareamento vale por navegador e fica guardado aqui.
const KEY = 'tvhub_remote_token';
let token = localStorage.getItem(KEY) || '';

const $ = (id) => document.getElementById(id);
const pairScreen = $('pair'), remoteScreen = $('remote');

async function api(path, options = {}) {
  const res = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: 'Bearer ' + token } : {}) },
  });
  if (res.status === 401) {
    token = '';
    localStorage.removeItem(KEY);
    show(false);
    throw new Error('pareamento perdido');
  }
  return { status: res.status, body: await res.json().catch(() => ({})) };
}

function show(paired) {
  pairScreen.classList.toggle('hidden', paired);
  remoteScreen.classList.toggle('hidden', !paired);
  if (paired) refresh();
}

// ---------------------------------------------------------------- pareamento

$('pairBtn').onclick = async () => {
  const pin = $('pin').value.trim();
  const res = await fetch('/api/pair', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ pin }),
  });
  const body = await res.json().catch(() => ({}));
  if (body.token) {
    token = body.token;
    localStorage.setItem(KEY, token);
    $('pairErr').textContent = '';
    show(true);
  } else {
    $('pairErr').textContent = 'Código errado. Olhe o número na TV.';
  }
};

$('pin').addEventListener('keydown', (e) => { if (e.key === 'Enter') $('pairBtn').click(); });

// ---------------------------------------------------------------- teclas

let hintTimer;
function hint(text) {
  $('hint').textContent = text || '';
  clearTimeout(hintTimer);
  if (text) hintTimer = setTimeout(() => { $('hint').textContent = baseHint; }, 2600);
}

let baseHint = '';

async function press(key) {
  if (navigator.vibrate) navigator.vibrate(8);
  try {
    const { body } = await api('/api/remote/key', { method: 'POST', body: JSON.stringify({ key }) });
    if (body.error) hint(body.error);
  } catch (e) {
    hint('sem conexão com a TV');
  }
}

for (const el of document.querySelectorAll('[data-key]')) {
  // pointerdown responde na hora; o clique normal espera o dedo sair.
  el.addEventListener('pointerdown', (e) => { e.preventDefault(); press(el.dataset.key); });
}

// ---------------------------------------------------------------- apps e estado

async function refresh() {
  try {
    const { body } = await api('/api/remote/state');
    $('dot').classList.remove('off');
    $('status').textContent = 'TV conectada';
    baseHint = body.dpadEverywhere
      ? ''
      : 'As setas e o OK mexem na tela do TV Hub. Dentro de um app, use o controle da TV.';
    if (!body.service) {
      baseHint = 'Ligue o serviço de acessibilidade do TV Hub para Voltar e Recentes funcionarem.';
    }
    $('hint').textContent = baseHint;
    drawApps(body.apps || []);
  } catch (e) {
    $('dot').classList.add('off');
    $('status').textContent = 'sem conexão';
  }
}

function drawApps(apps) {
  const box = $('apps');
  if (box.childElementCount === apps.length) return;
  box.textContent = '';
  for (const a of apps) {
    const b = document.createElement('button');
    b.className = 'app';
    b.textContent = a.label;
    b.onclick = async () => {
      const { body } = await api('/api/remote/launch', { method: 'POST', body: JSON.stringify({ pkg: a.pkg }) });
      if (body.error) hint(body.error);
    };
    box.appendChild(b);
  }
}

show(!!token);
setInterval(() => { if (token && !document.hidden) refresh(); }, 15000);
document.addEventListener('visibilitychange', () => { if (!document.hidden && token) refresh(); });
