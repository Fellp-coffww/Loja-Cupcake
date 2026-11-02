// Página do cliente (View). Fluxo: login -> vitrine + aba lateral (carrinho / meus pedidos)
let pedidoEmPagamento = null;
let totalCarrinho = 0;

const $ = id => document.getElementById(id);
function logado() { return !!Sessao.token && Sessao.perfil === 'CLIENTE'; }

// ---------- Login e cadastro ----------
function trocarFormLogin(qual) {
  $('form-entrar').classList.toggle('oculto', qual !== 'entrar');
  $('form-cadastro').classList.toggle('oculto', qual !== 'cadastro');
  $('aba-entrar').classList.toggle('ativa', qual === 'entrar');
  $('aba-cadastro').classList.toggle('ativa', qual === 'cadastro');
  aviso('msg-login', '');
}

async function entrar() {
  try {
    const r = await api('POST', '/api/login', { email: $('login-email').value, senha: $('login-senha').value });
    Sessao.salvar(r);
    iniciar();
  } catch (e) { aviso('msg-login', e.message, 'erro'); }
}

async function cadastrar() {
  try {
    await api('POST', '/api/cadastro', {
      nome: $('cad-nome').value, email: $('cad-email').value,
      senha: $('cad-senha').value, telefone: $('cad-tel').value });
    trocarFormLogin('entrar');
    $('login-email').value = $('cad-email').value;
    aviso('msg-login', 'Conta criada! Agora faça login.', 'ok');
  } catch (e) { aviso('msg-login', e.message, 'erro'); }
}

async function sair() {
  try { await api('POST', '/api/logout'); } catch (e) {}
  Sessao.limpar();
  fecharDrawer();
  $('login-senha').value = '';
  iniciar();
}

// ---------- Aba lateral ----------
function abrirDrawer(aba) {
  trocarAba(aba || 'carrinho');
  $('drawer').classList.add('aberto');
  $('overlay').classList.add('aberto');
}
function fecharDrawer() {
  $('drawer').classList.remove('aberto');
  $('overlay').classList.remove('aberto');
}
function trocarAba(aba) {
  const carrinho = aba === 'carrinho';
  $('aba-carrinho').classList.toggle('ativa', carrinho);
  $('aba-pedidos').classList.toggle('ativa', !carrinho);
  $('painel-carrinho').classList.toggle('oculto', !carrinho);
  $('rodape-carrinho').classList.toggle('oculto', !carrinho);
  $('painel-pedidos').classList.toggle('oculto', carrinho);
  if (carrinho) carregarCarrinho(); else carregarPedidos();
}

// ---------- Vitrine (US01, US02, US03) ----------
async function carregarCategorias() {
  const cats = await api('GET', '/api/categorias');
  $('filtro').innerHTML = '<option value="">Todas</option>' + cats.map(c => `<option value="${c.id}">${esc(c.nome)}</option>`).join('');
}

async function carregarVitrine() {
  const cat = $('filtro').value;
  try {
    const lista = await api('GET', '/api/cupcakes' + (cat ? '?categoria=' + cat : ''));
    const div = $('vitrine');
    if (!lista.length) { div.innerHTML = '<p>Nenhum produto disponível no momento</p>'; return; }
    div.innerHTML = lista.map(c => `
      <div class="card">
        <div class="img">${c.imagemUrl ? `<img src="${esc(c.imagemUrl)}" alt="">` : '🧁'}</div>
        ${c.destaque ? '<span class="destaque">Destaque</span>' : ''}
        <h3>${esc(c.nome)}</h3>
        <div class="tag">${esc(c.categorias)}</div>
        <p class="preco">${reais(c.preco)}</p>
        <div class="linha">
          <button class="sec" onclick="detalhes(${c.id})">Detalhes</button>
          <button onclick="adicionar(${c.id})">Adicionar</button>
        </div>
      </div>`).join('');
  } catch (e) { aviso('msg-vitrine', e.message, 'erro'); }
}

async function detalhes(id) {
  try {
    const c = await api('GET', '/api/cupcakes/' + id);
    const d = $('dlg-detalhe');
    d.innerHTML = `<h3>${esc(c.nome)}</h3>
      <div class="card"><div class="img">${c.imagemUrl ? `<img src="${esc(c.imagemUrl)}" alt="">` : '🧁'}</div></div>
      <p>${esc(c.descricao)}</p>
      <p><b>Ingredientes:</b> ${esc(c.ingredientes || '—')}</p>
      <p class="preco">${reais(c.preco)}</p>
      <div class="linha"><button onclick="adicionar(${c.id}); this.closest('dialog').close()">Adicionar ao carrinho</button>
      <button class="sec" onclick="this.closest('dialog').close()">Fechar</button></div>`;
    d.showModal();
  } catch (e) { aviso('msg-vitrine', e.message, 'erro'); }
}

// ---------- Carrinho (US04, US05, US06) ----------
async function adicionar(id) {
  try {
    await api('POST', '/api/carrinho/itens', { cupcakeId: id, quantidade: 1 });
    aviso('msg-vitrine', 'Item adicionado ao carrinho', 'ok');
    setTimeout(() => aviso('msg-vitrine', ''), 2500);
    carregarCarrinho();
  } catch (e) { aviso('msg-vitrine', e.message, 'erro'); }
}

async function remover(id) {
  try { await api('DELETE', '/api/carrinho/itens/' + id); carregarCarrinho(); }
  catch (e) { aviso('msg-carrinho', e.message, 'erro'); }
}

async function carregarCarrinho() {
  try {
    const c = await api('GET', '/api/carrinho');
    const qtd = c.itens.reduce((s, i) => s + i.quantidade, 0);
    totalCarrinho = Number(c.total);
    $('badge').textContent = qtd;
    $('total-carrinho').textContent = reais(c.total);
    $('btn-finalizar').disabled = c.itens.length === 0;
    $('carrinho').innerHTML = c.itens.length
      ? c.itens.map(i => `
        <div class="item-carrinho">
          <div><b>${esc(i.nome)}</b><br><small>${i.quantidade} x ${reais(i.preco)} = ${reais(i.subtotal)}</small></div>
          <button class="sec" onclick="remover(${i.cupcakeId})">Remover</button>
        </div>`).join('')
      : '<p>Seu carrinho está vazio.</p>';
  } catch (e) { aviso('msg-carrinho', e.message, 'erro'); }
}

// ---------- Finalizar pedido: endereço e confirmação (US07, US09) ----------
async function carregarEnderecos() {
  const lista = await api('GET', '/api/enderecos');
  $('sel-endereco').innerHTML = lista.length
    ? lista.map(e => `<option value="${e.id}">${esc(e.logradouro)}, ${esc(e.numero)} - ${esc(e.bairro)}, ${esc(e.cidade)}/${esc(e.uf)}</option>`).join('')
    : '<option value="">(cadastre um endereço)</option>';
  if (!lista.length) $('form-end').classList.remove('oculto');
}

async function abrirCheckout() {
  aviso('msg-checkout', '');
  try { await carregarEnderecos(); } catch (e) { aviso('msg-checkout', e.message, 'erro'); }
  $('dlg-checkout').showModal();
}

async function salvarEndereco() {
  const v = id => $(id).value;
  try {
    await api('POST', '/api/enderecos', { cep: v('e-cep'), logradouro: v('e-rua'), numero: v('e-num'),
      complemento: v('e-comp'), bairro: v('e-bairro'), cidade: v('e-cidade'), uf: v('e-uf') });
    $('form-end').classList.add('oculto');
    for (const id of ['e-cep', 'e-rua', 'e-num', 'e-comp', 'e-bairro', 'e-cidade', 'e-uf']) $(id).value = '';
    aviso('msg-checkout', 'Endereço salvo', 'ok');
    await carregarEnderecos();
    const sel = $('sel-endereco'); sel.selectedIndex = sel.options.length - 1;   // seleciona o recém-criado
  } catch (e) { aviso('msg-checkout', e.message, 'erro'); }
}

async function confirmarPedido() {
  const enderecoId = Number($('sel-endereco').value);
  if (!enderecoId) { aviso('msg-checkout', 'Selecione ou cadastre um endereço', 'erro'); return; }
  try {
    const r = await api('POST', '/api/pedidos', { enderecoId });
    $('dlg-checkout').close();
    carregarCarrinho(); carregarVitrine();
    trocarAba('pedidos');
    abrirPagamento(r.pedidoId);
  } catch (e) { aviso('msg-checkout', e.message, 'erro'); }
}

// ---------- Pagamento (US08, US10) ----------
function abrirPagamento(id) {
  pedidoEmPagamento = id;
  $('pg-pedido').textContent = '#' + id;
  $('pg-recusar').checked = false;
  aviso('msg-pagamento', '');
  $('dlg-pagamento').showModal();
}

async function pagar() {
  const metodo = document.querySelector('input[name=metodo]:checked').value;
  try {
    const r = await api('POST', `/api/pedidos/${pedidoEmPagamento}/pagamento`, { metodo, recusar: $('pg-recusar').checked });
    if (r.status === 'APROVADO') aviso('msg-pagamento', 'Pagamento aprovado! 🎉', 'ok');
    else aviso('msg-pagamento', 'Pagamento recusado. Tente novamente ou escolha outra forma.', 'erro');
    carregarPedidos();
  } catch (e) { aviso('msg-pagamento', e.message, 'erro'); }
}

// ---------- Meus pedidos (US11) ----------
async function carregarPedidos() {
  try {
    const lista = await api('GET', '/api/pedidos');
    $('pedidos').innerHTML = lista.length
      ? lista.map(p => `
        <div class="pedido-card">
          <div class="topo"><b>Pedido #${p.id}</b> <span class="status ${esc(p.status)}">${esc(p.status.replace('_', ' '))}</span></div>
          <small>${esc(p.dataPedido)}</small>
          <div>${reais(p.valorTotal)} · Pagamento: ${esc(p.statusPagamento || '—')}</div>
          ${p.status === 'AGUARDANDO_PAGAMENTO' ? `<button onclick="abrirPagamento(${p.id})">Pagar agora</button>` : ''}
        </div>`).join('')
      : '<p>Você ainda não fez pedidos.</p>';
  } catch (e) { aviso('msg-pedidos', e.message, 'erro'); }
}

// ---------- Início: só mostra a tela de login até o usuário entrar ----------
async function iniciar() {
  if (Sessao.token && Sessao.perfil === 'ADMINISTRADOR') { location.href = 'admin.html'; return; }
  let dentro = logado();
  if (dentro) {
    try { await api('GET', '/api/me'); }            // sessão ainda válida? (o servidor guarda sessões em memória)
    catch (e) { Sessao.limpar(); dentro = false; }
  }
  $('tela-login').classList.toggle('oculto', dentro);
  $('tela-loja').classList.toggle('oculto', !dentro);
  if (!dentro) return;
  $('ola').textContent = 'Olá, ' + Sessao.nome;
  await carregarCategorias();
  await Promise.all([carregarVitrine(), carregarCarrinho(), carregarPedidos()]);
}
iniciar();
setInterval(() => { if (logado() && $('drawer').classList.contains('aberto')) carregarPedidos(); }, 15000);
