/* ==========================================================================
 * Front-end do Inventário do Aventureiro (JavaScript puro, sem dependências).
 * Fala com a API REST (/api/itens, /api/chaos) e lê as métricas do Actuator.
 * ========================================================================== */
'use strict';

const API_ITENS = '/api/itens';
const MAX_PONTOS = 60;      // pontos mantidos nos gráficos (60 x 2s = 2 minutos)
const INTERVALO_MS = 2000;  // frequência de leitura das métricas

const $ = (seletor, raiz = document) => raiz.querySelector(seletor);
const ICONES = { ARMA: '⚔️', ARMADURA: '🛡️', POCAO: '🧪', MARAVILHOSO: '✨' };
const ROTULOS_CATEGORIA = { ARMA: 'Arma', ARMADURA: 'Armadura', POCAO: 'Poção', MARAVILHOSO: 'Maravilhoso' };
const ROTULOS_RARIDADE = { COMUM: 'Comum', INCOMUM: 'Incomum', RARO: 'Raro', MUITO_RARO: 'Muito raro', LENDARIO: 'Lendário' };

const estado = { itens: [], editandoId: null, abaAtiva: 'inventario', atividades: [] };
const historico = { cpu: [], heap: [] };

/* ---------- utilidades ---------- */
const numero = (n, casas = 2) => Number(n).toLocaleString('pt-BR', { minimumFractionDigits: 0, maximumFractionDigits: casas });
const valorTotal = (item) => Number(item.precoEmOuro) * Number(item.quantidade);

function toast(mensagem, erro = false) {
  const el = $('#toast');
  el.textContent = mensagem;
  el.classList.toggle('erro-toast', erro);
  el.classList.add('mostra');
  clearTimeout(toast.t);
  toast.t = setTimeout(() => el.classList.remove('mostra'), 3200);
}

/** Cria elemento com texto seguro (textContent evita injeção de HTML vinda da API). */
function el(tag, { classe, texto, attrs } = {}, filhos = []) {
  const e = document.createElement(tag);
  if (classe) e.className = classe;
  if (texto !== undefined) e.textContent = texto;
  if (attrs) Object.entries(attrs).forEach(([k, v]) => e.setAttribute(k, v));
  filhos.forEach((f) => e.append(f));
  return e;
}

/* ---------- painel de atividade (didático) ---------- */

/** Traduz uma chamada HTTP no nível de log que o backend registra (conforme Service e Handler). */
function nivelDeLog(metodo, caminho, status) {
  if (status === 0) return { nivel: 'ERROR', texto: 'A API não respondeu (conexão recusada ou caiu).' };
  if (status >= 500) return { nivel: 'ERROR', texto: 'Falha interna: o GlobalExceptionHandler grava o stack trace no log e envia ao Graylog.' };
  if (status === 404) return { nivel: 'ERROR', texto: 'ID inexistente: o Service registra ERROR antes de lançar ItemNaoEncontradoException.' };
  if (status >= 400) return { nivel: 'WARN', texto: 'Erro do cliente (validação ou requisição inválida): registrado como WARN.' };
  if (caminho.startsWith('/api/chaos')) return { nivel: 'INFO', texto: 'Teste de caos concluído (o início já foi registrado como WARN).' };
  if (metodo === 'POST') return { nivel: 'INFO', texto: 'Item adicionado com sucesso (e DEBUG com os detalhes técnicos).' };
  if (metodo === 'PUT') return { nivel: 'INFO', texto: 'Item atualizado; o DEBUG mostra o valor total antes e depois.' };
  if (metodo === 'DELETE') return { nivel: 'INFO', texto: 'Item removido com sucesso.' };
  return { nivel: 'DEBUG', texto: 'Consulta: apenas detalhes técnicos em DEBUG.' };
}

function registrarAtividade(metodo, caminho, status, ms) {
  estado.atividades.unshift({ hora: new Date().toLocaleTimeString('pt-BR'), metodo, caminho, status, ms });
  estado.atividades = estado.atividades.slice(0, 40);
  renderAtividades();
}

function renderAtividades() {
  const ul = $('#lista-atividade');
  ul.replaceChildren();
  if (!estado.atividades.length) {
    ul.append(el('li', { classe: 'vazio-ativ', texto: 'Nenhuma requisição ainda. Use a tela e acompanhe aqui.' }));
    return;
  }
  for (const a of estado.atividades) {
    const { nivel, texto } = nivelDeLog(a.metodo, a.caminho, a.status);
    const classeHttp = 's' + Math.floor(a.status / 100);
    ul.append(el('li', {}, [
      el('span', { classe: 'hora', texto: a.hora }),
      el('span', { classe: 'metodo', texto: a.metodo }),
      el('span', { classe: 'caminho', texto: a.caminho, attrs: { title: a.caminho } }),
      el('span', { classe: `http ${classeHttp}`, texto: a.status || 'ERR' }),
      el('span', { classe: 'ms hora', texto: `${a.ms} ms` }),
      el('span', { classe: `nivel ${nivel}`, texto: nivel }),
      el('span', { classe: 'expl', texto: texto }),
    ]));
  }
}

/* ---------- chamadas à API ---------- */

/** Wrapper do fetch: mede o tempo, registra no painel de atividade e converte erros HTTP em exceções. */
async function chamarApi(caminho, opcoes = {}) {
  const metodo = opcoes.method || 'GET';
  const inicio = performance.now();
  let resposta;
  try {
    resposta = await fetch(caminho, { headers: { 'Content-Type': 'application/json' }, ...opcoes });
  } catch (e) {
    registrarAtividade(metodo, caminho, 0, Math.round(performance.now() - inicio));
    throw new Error('Não foi possível conectar à API.');
  }
  const ms = Math.round(performance.now() - inicio);
  const texto = await resposta.text();
  let corpo = null;
  try { corpo = texto ? JSON.parse(texto) : null; } catch { corpo = texto; }
  registrarAtividade(metodo, caminho, resposta.status, ms);

  if (!resposta.ok) {
    const erro = new Error((corpo && corpo.mensagem) || `Erro HTTP ${resposta.status}`);
    erro.status = resposta.status;
    throw erro;
  }
  return corpo;
}

/* ---------- aba Inventário ---------- */
async function carregarItens() {
  try {
    estado.itens = await chamarApi(API_ITENS);
    renderInventario();
  } catch (e) {
    toast(e.message, true);
  }
}

function renderInventario() {
  const busca = $('#busca').value.trim().toLowerCase();
  const categoria = $('#filtro-categoria').value;
  const filtrados = estado.itens.filter((i) =>
    (!categoria || i.categoria === categoria) &&
    (!busca || `${i.nome} ${i.descricao || ''}`.toLowerCase().includes(busca)));

  const corpo = $('#corpo-tabela');
  corpo.replaceChildren();
  for (const item of filtrados) {
    const btnEditar = el('button', { texto: '✏️', attrs: { title: 'Editar (PUT)' } });
    const btnRemover = el('button', { texto: '🗑️', attrs: { title: 'Remover (DELETE)' } });
    btnEditar.addEventListener('click', () => abrirFormulario(item));
    btnRemover.addEventListener('click', () => removerItem(item));

    corpo.append(el('tr', {}, [
      el('td', {}, [el('div', { classe: 'item-nome', texto: item.nome }), el('div', { classe: 'item-desc', texto: item.descricao || '' })]),
      el('td', { texto: `${ICONES[item.categoria] || ''} ${ROTULOS_CATEGORIA[item.categoria] || item.categoria}` }),
      el('td', {}, [el('span', { classe: `chip r-${item.raridade}`, texto: ROTULOS_RARIDADE[item.raridade] || item.raridade })]),
      el('td', { classe: 'num', texto: numero(item.peso, 1) }),
      el('td', { classe: 'num', texto: numero(item.precoEmOuro) }),
      el('td', { classe: 'num', texto: item.quantidade }),
      el('td', { classe: 'num', texto: numero(valorTotal(item)) }),
      el('td', { classe: 'acoes' }, [btnEditar, btnRemover]),
    ]));
  }
  $('#vazio').hidden = filtrados.length > 0;

  // Resumo considera todos os itens (não só os filtrados)
  const pesoTotal = estado.itens.reduce((s, i) => s + Number(i.peso) * Number(i.quantidade), 0);
  const ouroTotal = estado.itens.reduce((s, i) => s + valorTotal(i), 0);
  $('#stat-itens').textContent = estado.itens.length;
  $('#stat-peso').textContent = `${numero(pesoTotal, 1)} lb`;
  $('#stat-valor').textContent = `${numero(ouroTotal)} po`;
}

function abrirFormulario(item = null) {
  const form = $('#form-item');
  form.reset();
  $('#erro-form').hidden = true;
  estado.editandoId = item ? item.id : null;
  $('#dlg-titulo').textContent = item ? `Editar: ${item.nome}` : 'Novo item';
  if (item) {
    for (const campo of ['nome', 'descricao', 'categoria', 'raridade', 'peso', 'precoEmOuro', 'quantidade']) {
      form.elements[campo].value = item[campo] ?? '';
    }
  }
  $('#dlg').showModal();
}

async function salvarItem(evento) {
  evento.preventDefault();
  const f = $('#form-item').elements;
  const corpo = {
    nome: f.nome.value.trim(),
    descricao: f.descricao.value.trim(),
    categoria: f.categoria.value,
    raridade: f.raridade.value,
    peso: f.peso.value === '' ? null : Number(f.peso.value),
    precoEmOuro: f.precoEmOuro.value === '' ? null : Number(f.precoEmOuro.value),
    quantidade: f.quantidade.value === '' ? null : Number(f.quantidade.value),
  };
  const editando = estado.editandoId !== null;
  try {
    await chamarApi(editando ? `${API_ITENS}/${estado.editandoId}` : API_ITENS,
      { method: editando ? 'PUT' : 'POST', body: JSON.stringify(corpo) });
    $('#dlg').close();
    toast(editando ? 'Item atualizado!' : 'Item adicionado!');
    await carregarItens();
  } catch (e) {
    // A mensagem vem do GlobalExceptionHandler (ex.: "nome: O nome é obrigatório")
    const caixa = $('#erro-form');
    caixa.textContent = e.message;
    caixa.hidden = false;
  }
}

async function removerItem(item) {
  if (!confirm(`Remover "${item.nome}" do inventário?`)) return;
  try {
    await chamarApi(`${API_ITENS}/${item.id}`, { method: 'DELETE' });
    toast('Item removido.');
  } catch (e) {
    toast(e.message, true);
  }
  await carregarItens();
}

/* ---------- aba Observabilidade: leitura do /actuator/prometheus ---------- */

/** Converte o texto do formato de exposição do Prometheus em uma lista de {nome, labels, valor}. */
function parsearPrometheus(texto) {
  const amostras = [];
  const regex = /^([a-zA-Z_:][a-zA-Z0-9_:]*)(?:\{(.*)\})?\s+(-?[0-9.eE+-]+|NaN|\+Inf|-Inf)/;
  for (const linha of texto.split('\n')) {
    if (!linha || linha.startsWith('#')) continue;
    const m = regex.exec(linha);
    if (!m) continue;
    const labels = {};
    if (m[2]) for (const l of m[2].matchAll(/(\w+)="((?:[^"\\]|\\.)*)"/g)) labels[l[1]] = l[2];
    amostras.push({ nome: m[1], labels, valor: parseFloat(m[3]) });
  }
  return amostras;
}

const somar = (lista) => lista.reduce((s, a) => s + a.valor, 0);

function formatarUptime(segundos) {
  const s = Math.floor(segundos);
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60);
  return h ? `${h}h ${m}min` : m ? `${m}min ${s % 60}s` : `${s}s`;
}

async function atualizarMetricas() {
  let amostras;
  try {
    const resposta = await fetch('/actuator/prometheus'); // fetch direto: não polui o painel de atividade
    amostras = parsearPrometheus(await resposta.text());
  } catch {
    return;
  }
  const porNome = (nome) => amostras.filter((a) => a.nome === nome);

  // CPU (0 a 1) e heap (bytes)
  const cpu = (porNome('process_cpu_usage')[0] || { valor: 0 }).valor;
  const heapUsado = somar(porNome('jvm_memory_used_bytes').filter((a) => a.labels.area === 'heap'));
  const heapMax = somar(porNome('jvm_memory_max_bytes').filter((a) => a.labels.area === 'heap' && a.valor > 0));
  historico.cpu.push(cpu * 100);
  historico.heap.push(heapUsado / 1048576);
  Object.values(historico).forEach((s) => { if (s.length > MAX_PONTOS) s.shift(); });

  $('#v-cpu').textContent = `${numero(cpu * 100, 1)}%`;
  $('#v-heap').textContent = heapMax > 0
    ? `${numero(heapUsado / 1048576, 0)} / ${numero(heapMax / 1048576, 0)} MB`
    : `${numero(heapUsado / 1048576, 0)} MB`;
  $('#v-threads').textContent = numero((porNome('jvm_threads_live_threads')[0] || { valor: 0 }).valor, 0);
  $('#v-uptime').textContent = formatarUptime((porNome('process_uptime_seconds')[0] || { valor: 0 }).valor);

  desenharGrafico($('#graf-cpu'), historico.cpu, { maximo: 100, cor: '#e0b04a', sufixo: '%' });
  desenharGrafico($('#graf-heap'), historico.heap,
    { maximo: heapMax > 0 ? heapMax / 1048576 : Math.max(...historico.heap, 1) * 1.2, cor: '#5aa0e6', sufixo: ' MB' });

  // Requisições HTTP (ignora o próprio /actuator)
  const contagens = porNome('http_server_requests_seconds_count').filter((a) => !(a.labels.uri || '').startsWith('/actuator'));
  const somas = porNome('http_server_requests_seconds_sum');
  const porClasse = { 2: 0, 4: 0, 5: 0 };
  contagens.forEach((a) => { const c = Math.floor(Number(a.labels.status) / 100); if (c in porClasse) porClasse[c] += a.valor; });
  $('#v-2xx').textContent = numero(porClasse[2], 0);
  $('#v-4xx').textContent = numero(porClasse[4], 0);
  $('#v-5xx').textContent = numero(porClasse[5], 0);

  renderTabelaHttp(contagens, somas);
}

function renderTabelaHttp(contagens, somas) {
  const corpo = $('#corpo-http');
  corpo.replaceChildren();
  const ordenadas = [...contagens].filter((a) => a.valor > 0).sort((a, b) => b.valor - a.valor).slice(0, 12);
  $('#vazio-http').hidden = ordenadas.length > 0;
  for (const a of ordenadas) {
    const soma = somas.find((s) => s.labels.uri === a.labels.uri && s.labels.method === a.labels.method && s.labels.status === a.labels.status);
    const mediaMs = soma ? (soma.valor / a.valor) * 1000 : 0;
    corpo.append(el('tr', {}, [
      el('td', {}, [el('span', { classe: 'metodo', texto: a.labels.method })]),
      el('td', {}, [el('code', { texto: a.labels.uri })]),
      el('td', {}, [el('span', { classe: `http s${Math.floor(Number(a.labels.status) / 100)}`, texto: a.labels.status })]),
      el('td', { classe: 'num', texto: numero(a.valor, 0) }),
      el('td', { classe: 'num', texto: `${numero(mediaMs, 1)} ms` }),
    ]));
  }
}

/** Gráfico de linha simples em <canvas> (sem bibliotecas). A amostra mais recente fica à direita. */
function desenharGrafico(canvas, serie, { maximo, cor, sufixo }) {
  const dpr = window.devicePixelRatio || 1;
  const w = canvas.clientWidth, h = canvas.clientHeight;
  if (!w || !h) return; // aba oculta
  canvas.width = w * dpr; canvas.height = h * dpr;
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);
  ctx.clearRect(0, 0, w, h);

  // linhas de grade
  ctx.strokeStyle = '#3a322b'; ctx.lineWidth = 1; ctx.fillStyle = '#a89b8a'; ctx.font = '11px system-ui';
  for (let i = 0; i <= 4; i++) {
    const y = 4 + ((h - 8) * i) / 4;
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(w, y); ctx.stroke();
  }
  ctx.fillText(`${numero(maximo, 0)}${sufixo}`, 4, 14);
  if (serie.length < 2) return;

  const passo = w / (MAX_PONTOS - 1);
  const pontos = serie.map((v, i) => [w - (serie.length - 1 - i) * passo, h - 4 - Math.min(v / maximo, 1) * (h - 8)]);

  ctx.beginPath();
  pontos.forEach(([x, y], i) => (i ? ctx.lineTo(x, y) : ctx.moveTo(x, y)));
  ctx.strokeStyle = cor; ctx.lineWidth = 2; ctx.stroke();

  ctx.lineTo(pontos[pontos.length - 1][0], h); ctx.lineTo(pontos[0][0], h); ctx.closePath();
  ctx.globalAlpha = 0.15; ctx.fillStyle = cor; ctx.fill(); ctx.globalAlpha = 1;
}

/* ---------- painel de caos ---------- */

/** Desabilita o botão durante a execução (os testes de CPU e memória duram vários segundos). */
async function executarCaos(botao, rotuloOcupado, acao) {
  const original = botao.textContent;
  botao.disabled = true; botao.textContent = rotuloOcupado;
  try { await acao(); } finally { botao.disabled = false; botao.textContent = original; }
}

const valorCampo = (id) => Number($(id).value) || 1;

async function dispararCpu(botao) {
  await executarCaos(botao, 'Estressando… 🔥', async () => {
    try {
      const r = await chamarApi(`/api/chaos/cpu?segundos=${valorCampo('#cpu-seg')}&threads=${valorCampo('#cpu-thr')}`);
      toast(`CPU: ${numero(r.primosEncontrados, 0)} primos calculados.`);
    } catch (e) { toast(e.message, true); }
  });
}

async function dispararMemoria(botao) {
  await executarCaos(botao, 'Alocando… 🧠', async () => {
    try {
      await chamarApi(`/api/chaos/memory?megabytes=${valorCampo('#mem-mb')}&segundos=${valorCampo('#mem-seg')}`);
      toast('Memória liberada para o Garbage Collector.');
    } catch (e) { toast(e.message, true); }
  });
}

async function dispararErros(codigo) {
  const vezes = Math.min(valorCampo('#err-qtd'), 100);
  const chamadas = Array.from({ length: vezes }, () => chamarApi(`/api/chaos/${codigo}`).catch(() => null));
  await Promise.all(chamadas);
  toast(`${vezes} requisição(ões) com erro ${codigo} enviadas.`, true);
  atualizarMetricas();
}

async function removerInexistente() {
  try { await chamarApi(`${API_ITENS}/9999`, { method: 'DELETE' }); }
  catch (e) { toast(`${e.status}: ${e.message}`, true); }
  atualizarMetricas();
}

/* ---------- abas e inicialização ---------- */
function trocarAba(nome) {
  estado.abaAtiva = nome;
  document.querySelectorAll('.aba-btn').forEach((b) => b.classList.toggle('ativa', b.dataset.aba === nome));
  document.querySelectorAll('.aba').forEach((s) => s.classList.toggle('ativa', s.id === `aba-${nome}`));
  if (nome === 'observabilidade') atualizarMetricas();
}

function iniciar() {
  document.querySelectorAll('.aba-btn').forEach((b) => b.addEventListener('click', () => trocarAba(b.dataset.aba)));
  $('#btn-novo').addEventListener('click', () => abrirFormulario());
  $('#btn-cancelar').addEventListener('click', () => $('#dlg').close());
  $('#form-item').addEventListener('submit', salvarItem);
  $('#busca').addEventListener('input', renderInventario);
  $('#filtro-categoria').addEventListener('change', renderInventario);

  $('#btn-cpu').addEventListener('click', (e) => dispararCpu(e.currentTarget));
  $('#btn-mem').addEventListener('click', (e) => dispararMemoria(e.currentTarget));
  $('#btn-400').addEventListener('click', () => dispararErros(400));
  $('#btn-500').addEventListener('click', () => dispararErros(500));
  $('#btn-404').addEventListener('click', removerInexistente);

  renderAtividades();
  carregarItens();

  // Atualiza as métricas periodicamente, apenas quando a aba está visível
  setInterval(() => {
    if (estado.abaAtiva === 'observabilidade' && !document.hidden) atualizarMetricas();
  }, INTERVALO_MS);
}

document.addEventListener('DOMContentLoaded', iniciar);
