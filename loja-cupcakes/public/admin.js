// Painel do dono (View) - US12, US13, US14, US15
const STATUS = ['AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_PREPARO', 'ENVIADO', 'ENTREGUE', 'CANCELADO'];
let editandoId = null;
let produtosCache = [];

function admin() { return Sessao.token && Sessao.perfil === 'ADMINISTRADOR'; }

async function entrar() {
  try {
    const r = await api('POST', '/api/login', {
      email: document.getElementById('login-email').value,
      senha: document.getElementById('login-senha').value });
    if (r.perfil !== 'ADMINISTRADOR') { aviso('msg-login', 'Esta conta não é de administrador', 'erro'); return; }
    Sessao.salvar(r);
    iniciar();
  } catch (e) { aviso('msg-login', e.message, 'erro'); }
}

async function sair() {
  try { await api('POST', '/api/logout'); } catch (e) {}
  Sessao.limpar();
  iniciar();
}

function tabela(colunas, linhas) {
  if (!linhas.length) return '<p>Sem dados.</p>';
  return '<table><tr>' + colunas.map(c => `<th>${c[0]}</th>`).join('') + '</tr>' +
    linhas.map(l => '<tr>' + colunas.map(c => `<td>${c[2] ? c[2](l[c[1]]) : esc(l[c[1]])}</td>`).join('') + '</tr>').join('') + '</table>';
}

// Atualização automática do dashboard (a cada 10 s; pausa quando a aba está oculta)
let timerDashboard = null;
function agendarDashboard() {
  clearInterval(timerDashboard);
  timerDashboard = setInterval(() => {
    if (!admin()) { clearInterval(timerDashboard); return; }
    if (!document.hidden) carregarDashboard().catch(() => {});
  }, window.INTERVALO_DASHBOARD_MS || 10000);
}

async function carregarDashboard() {
  const d = await api('GET', '/api/admin/dashboard');
  const hora = new Date().toLocaleTimeString('pt-BR');
  const marca = document.getElementById('dash-atualizado');
  if (marca) marca.textContent = 'Atualizado às ' + hora + ' (atualização automática a cada 10 s)';
  document.getElementById('dashboard').innerHTML =
    '<h3>Vendas por dia</h3>' + tabela([['Dia', 'dia'], ['Pedidos', 'pedidos'], ['Faturamento', 'faturamento', reais], ['Ticket médio', 'ticketMedio', reais]], d.vendasDiarias) +
    '<h3>Mais vendidos</h3>' + tabela([['Produto', 'nome'], ['Unidades', 'unidades'], ['Receita', 'receita', reais]], d.maisVendidos) +
    '<h3>Estoque</h3>' + tabela([['Produto', 'nome'], ['Estoque', 'estoque'], ['Situação', 'situacao']], d.estoque);
}

async function carregarPedidos() {
  const lista = await api('GET', '/api/admin/pedidos');
  const div = document.getElementById('pedidos');
  if (!lista.length) { div.innerHTML = '<p>Nenhum pedido.</p>'; return; }
  div.innerHTML = '<table><tr><th>#</th><th>Cliente</th><th>Data</th><th>Total</th><th>Pagamento</th><th>Status</th></tr>' +
    lista.map(p => `<tr><td>${p.id}</td><td>${esc(p.cliente)}</td><td>${esc(p.dataPedido)}</td><td>${reais(p.valorTotal)}</td>
      <td>${esc(p.statusPagamento || '—')}</td>
      <td><select onchange="mudarStatus(${p.id}, this.value)">${STATUS.map(s => `<option ${s === p.status ? 'selected' : ''}>${s}</option>`).join('')}</select></td></tr>`).join('') +
    '</table>';
}

async function mudarStatus(id, status) {
  try {
    await api('PUT', `/api/admin/pedidos/${id}/status`, { status });
    aviso('msg-pedidos', 'Status do pedido #' + id + ' atualizado', 'ok');
  } catch (e) { aviso('msg-pedidos', e.message, 'erro'); }
  carregarPedidos(); carregarDashboard();
}

async function carregarCategorias() {
  const cats = await api('GET', '/api/categorias');
  document.getElementById('p-categorias').innerHTML =
    cats.map(c => `<label><input type="checkbox" class="cat" value="${c.id}" data-nome="${esc(c.nome)}"> ${esc(c.nome)}</label> `).join('');
}

async function carregarProdutos() {
  produtosCache = await api('GET', '/api/admin/cupcakes');
  document.getElementById('produtos').innerHTML = '<h3>Produtos cadastrados</h3>' +
    '<table><tr><th>Nome</th><th>Preço</th><th>Estoque</th><th>Ativo</th><th></th></tr>' +
    produtosCache.map(c => `<tr><td>${esc(c.nome)}</td><td>${reais(c.preco)}</td><td>${c.estoque}</td>
      <td>${c.ativo ? 'Sim' : 'Não'}</td><td><button class="sec" onclick="editar(${c.id})">Editar</button></td></tr>`).join('') + '</table>';
}

function editar(id) {
  const c = produtosCache.find(x => x.id === id);
  editandoId = id;
  document.getElementById('titulo-form').textContent = 'Editar cupcake #' + id;
  document.getElementById('p-nome').value = c.nome;
  document.getElementById('p-preco').value = c.preco;
  document.getElementById('p-estoque').value = c.estoque;
  document.getElementById('p-imagem').value = c.imagemUrl || '';
  document.getElementById('p-desc').value = c.descricao;
  document.getElementById('p-ingr').value = c.ingredientes || '';
  document.getElementById('p-destaque').checked = c.destaque;
  document.getElementById('p-ativo').checked = c.ativo;
  const nomes = c.categorias.split(', ');
  document.querySelectorAll('.cat').forEach(cb => cb.checked = nomes.includes(cb.dataset.nome));
  window.scrollTo({ top: document.getElementById('titulo-form').offsetTop - 20, behavior: 'smooth' });
}

function limparForm() {
  editandoId = null;
  document.getElementById('titulo-form').textContent = 'Novo cupcake';
  for (const id of ['p-nome', 'p-preco', 'p-estoque', 'p-imagem', 'p-desc', 'p-ingr']) document.getElementById(id).value = '';
  document.getElementById('p-destaque').checked = false;
  document.getElementById('p-ativo').checked = true;
  document.querySelectorAll('.cat').forEach(cb => cb.checked = false);
}

async function salvarProduto() {
  const v = id => document.getElementById(id).value;
  const corpo = {
    nome: v('p-nome'), descricao: v('p-desc'), ingredientes: v('p-ingr'), imagemUrl: v('p-imagem'),
    preco: v('p-preco'), estoque: v('p-estoque') || 0,
    destaque: document.getElementById('p-destaque').checked, ativo: document.getElementById('p-ativo').checked,
    categorias: [...document.querySelectorAll('.cat:checked')].map(cb => cb.value).join(',')
  };
  if (corpo.preco === '') { aviso('msg-produto', 'Informe o preço', 'erro'); return; }
  corpo.preco = Number(corpo.preco);
  try {
    if (editandoId) await api('PUT', '/api/admin/cupcakes/' + editandoId, corpo);
    else await api('POST', '/api/admin/cupcakes', corpo);
    aviso('msg-produto', 'Produto salvo', 'ok');
    limparForm(); carregarProdutos(); carregarDashboard();
  } catch (e) { aviso('msg-produto', e.message, 'erro'); }
}

async function iniciar() {
  const ok = admin();
  document.getElementById('sec-login').classList.toggle('oculto', ok);
  document.getElementById('painel').classList.toggle('oculto', !ok);
  document.getElementById('quem').textContent = ok ? Sessao.nome : '';
  if (!ok) return;
  try {
    await carregarCategorias();
    await Promise.all([carregarDashboard(), carregarPedidos(), carregarProdutos()]);
    agendarDashboard();
  } catch (e) { Sessao.limpar(); iniciar(); }
}
iniciar();
