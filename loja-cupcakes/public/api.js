// Funções comuns às duas páginas (camada de acesso à API)
const Sessao = {
  get token() { return localStorage.getItem('token'); },
  get nome() { return localStorage.getItem('nome'); },
  get perfil() { return localStorage.getItem('perfil'); },
  salvar(r) { localStorage.setItem('token', r.token); localStorage.setItem('nome', r.nome); localStorage.setItem('perfil', r.perfil); },
  limpar() { localStorage.clear(); }
};

async function api(metodo, url, corpo) {
  const opcoes = { method: metodo, headers: { 'Content-Type': 'application/json' } };
  if (Sessao.token) opcoes.headers['Authorization'] = 'Bearer ' + Sessao.token;
  if (corpo !== undefined) opcoes.body = JSON.stringify(corpo);
  const resp = await fetch(url, opcoes);
  const dados = await resp.json().catch(() => ({}));
  if (!resp.ok) {
    if (resp.status === 401) Sessao.limpar();
    throw new Error(dados.erro || 'Erro ' + resp.status);
  }
  return dados;
}

const reais = v => Number(v).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });

function aviso(id, texto, tipo) {
  const el = document.getElementById(id);
  el.textContent = texto;
  el.className = 'msg ' + (tipo || 'ok');
  if (!texto) el.className = 'msg';
}

function esc(t) {   // evita injeção de HTML
  return String(t ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
