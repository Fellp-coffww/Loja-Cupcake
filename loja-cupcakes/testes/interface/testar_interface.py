"""
Testes automatizados da interface (front-end) com Playwright.

Requisitos:   pip install playwright    e    playwright install chromium
Como rodar:   1) suba o banco de TESTES com os dados iniciais e o sistema apontando para ele
                 (veja o README, seção "Testes");  2) python testes/interface/testar_interface.py [URL]

O script simula um usuário real no navegador e confere o que aparece na tela.
Ao final grava relatorios/interface.json e imprime um resumo.
ATENÇÃO: altera dados (cria pedidos, produtos). Use apenas com o banco de testes.
"""
import json, sys, time
from pathlib import Path
from playwright.sync_api import sync_playwright, expect

URL = sys.argv[1] if len(sys.argv) > 1 else "http://localhost:8080"
SAIDA = Path("relatorios"); SAIDA.mkdir(exist_ok=True)
resultados = []


def teste(codigo, historia, descricao):
    """Decorador: executa a função, registra aprovado/reprovado e tira print se falhar."""
    def deco(fn):
        t0 = time.time()
        try:
            fn()
            resultados.append(dict(codigo=codigo, historia=historia, descricao=descricao, resultado="APROVADO", erro=""))
        except Exception as e:  # noqa
            resultados.append(dict(codigo=codigo, historia=historia, descricao=descricao, resultado="REPROVADO",
                                   erro=str(e).splitlines()[0][:200]))
            try:
                pagina_atual[0].screenshot(path=str(SAIDA / f"falha_{codigo}.png"))
            except Exception:
                pass
        resultados[-1]["segundos"] = round(time.time() - t0, 1)
        print(f"{resultados[-1]['resultado']:9} {codigo}  {descricao}")
        return fn
    return deco


pagina_atual = [None]


def nova_pagina(browser):
    ctx = browser.new_context(viewport={"width": 1100, "height": 800})
    pg = ctx.new_page()
    pagina_atual[0] = pg
    return pg


def login(pg, email, senha="123456", destino="/"):
    pg.goto(URL + destino)
    pg.fill("#login-email", email); pg.fill("#login-senha", senha)
    pg.click("#form-entrar button")


def fechar_drawer(pg):
    if "aberto" in (pg.get_attribute("#drawer", "class") or ""):
        pg.click("#drawer >> text=✕"); pg.wait_for_timeout(350)


with sync_playwright() as p:
    browser = p.chromium.launch()

    # ------------------------------------------------------------------ visitante
    pg = nova_pagina(browser)

    @teste("UI01", "ACESSO", "Ao abrir, aparece somente a tela de login (loja e vitrine ocultas)")
    def _():
        pg.goto(URL + "/")
        expect(pg.locator("#tela-login")).to_be_visible()
        expect(pg.locator("#tela-loja")).to_be_hidden()
        expect(pg.locator("#vitrine")).to_be_hidden()

    @teste("UI02", "ACESSO", "Login com senha errada mostra mensagem de erro e continua no login")
    def _():
        pg.fill("#login-email", "maria@email.com"); pg.fill("#login-senha", "errada")
        pg.click("#form-entrar button")
        expect(pg.locator("#msg-login")).to_contain_text("inválidos")
        expect(pg.locator("#tela-loja")).to_be_hidden()

    @teste("UI03", "ACESSO", "Cadastro de conta nova volta ao login com o e-mail preenchido")
    def _():
        pg.click("#aba-cadastro")
        pg.fill("#cad-nome", "Teste Novo"); pg.fill("#cad-email", "novo@email.com"); pg.fill("#cad-senha", "123456")
        pg.click("#form-cadastro button")
        expect(pg.locator("#msg-login")).to_contain_text("Conta criada")
        expect(pg.locator("#login-email")).to_have_value("novo@email.com")

    @teste("UI04", "ACESSO", "Cadastro com senha curta mostra erro")
    def _():
        pg.click("#aba-cadastro")
        pg.fill("#cad-nome", "Outro"); pg.fill("#cad-email", "outro@email.com"); pg.fill("#cad-senha", "123")
        pg.click("#form-cadastro button")
        expect(pg.locator("#msg-login")).to_contain_text("6 caracteres")

    # ------------------------------------------------------------------ cliente Maria
    pg = nova_pagina(browser)

    @teste("UI05", "US01", "Após o login aparece a vitrine com 5 cupcakes e some a tela de login")
    def _():
        login(pg, "maria@email.com")
        expect(pg.locator(".card")).to_have_count(5)
        expect(pg.locator("#tela-login")).to_be_hidden()
        expect(pg.locator("#ola")).to_contain_text("Maria")

    @teste("UI06", "US01", "Produtos em destaque aparecem primeiro")
    def _():
        expect(pg.locator(".card").first).to_contain_text("Destaque")

    @teste("UI07", "US02", "Filtro 'Vegano' mostra 1 cupcake e 'Todas' volta aos 5")
    def _():
        pg.select_option("#filtro", label="Vegano")
        expect(pg.locator(".card")).to_have_count(1)
        expect(pg.locator(".card")).to_contain_text("Vegano de Cacau")
        pg.select_option("#filtro", value="")
        expect(pg.locator(".card")).to_have_count(5)

    @teste("UI08", "US03", "Detalhes abre janela com ingredientes e preço")
    def _():
        pg.locator(".card").nth(1).locator("text=Detalhes").click()
        expect(pg.locator("#dlg-detalhe")).to_be_visible()
        expect(pg.locator("#dlg-detalhe")).to_contain_text("Ingredientes")
        expect(pg.locator("#dlg-detalhe")).to_contain_text("R$")
        pg.click("#dlg-detalhe >> text=Fechar")

    @teste("UI09", "US04", "Adicionar mostra aviso e atualiza o contador do carrinho")
    def _():
        pg.locator(".card").nth(0).locator("text=Adicionar").click()
        expect(pg.locator("#msg-vitrine")).to_contain_text("adicionado")
        expect(pg.locator("#badge")).to_have_text("1")
        pg.locator(".card").nth(1).locator("text=Adicionar").click()
        pg.locator(".card").nth(1).locator("text=Adicionar").click()
        expect(pg.locator("#badge")).to_have_text("3")

    @teste("UI10", "US06", "Aba lateral abre o carrinho com 2 itens e total R$ 31,50")
    def _():
        pg.click("header >> text=Carrinho")
        expect(pg.locator("#drawer")).to_have_class("aberto")
        expect(pg.locator(".item-carrinho")).to_have_count(2)
        expect(pg.locator("#total-carrinho")).to_contain_text("31,50")
        expect(pg.locator("#btn-finalizar")).to_be_enabled()

    @teste("UI11", "US05", "Remover item atualiza lista, total e contador")
    def _():
        pg.locator(".item-carrinho").first.locator("text=Remover").click()
        expect(pg.locator(".item-carrinho")).to_have_count(1)
        expect(pg.locator("#badge")).to_have_text("2")
        expect(pg.locator("#total-carrinho")).to_contain_text("22,00")

    @teste("UI12", "US05", "Carrinho vazio mostra mensagem e desabilita Finalizar pedido")
    def _():
        pg.locator(".item-carrinho").first.locator("text=Remover").click()
        expect(pg.locator("#carrinho")).to_contain_text("vazio")
        expect(pg.locator("#btn-finalizar")).to_be_disabled()
        expect(pg.locator("#badge")).to_have_text("0")

    @teste("UI13", "ACESSO", "Clicar fora da aba lateral (ou no X) fecha o painel")
    def _():
        pg.click("#drawer >> text=✕")
        pg.wait_for_timeout(350)
        assert "aberto" not in (pg.get_attribute("#drawer", "class") or "")

    @teste("UI14", "US09", "Finalizar pedido: escolher endereço da Maria e confirmar abre o pagamento")
    def _():
        pg.locator(".card").nth(0).locator("text=Adicionar").click()
        pg.locator(".card").nth(1).locator("text=Adicionar").click()
        expect(pg.locator("#badge")).to_have_text("2")
        pg.click("header >> text=Carrinho")
        pg.click("#btn-finalizar")
        expect(pg.locator("#dlg-checkout")).to_be_visible()
        expect(pg.locator("#sel-endereco")).to_contain_text("Rua das Flores")
        pg.click("#dlg-checkout >> text=Confirmar pedido")
        expect(pg.locator("#dlg-pagamento")).to_be_visible()
        expect(pg.locator("#badge")).to_have_text("0")

    @teste("UI15", "US10", "Pagamento recusado avisa o cliente; nova tentativa é aprovada")
    def _():
        pg.check("#pg-recusar"); pg.click("#dlg-pagamento >> text=Pagar")
        expect(pg.locator("#msg-pagamento")).to_contain_text("recusado")
        pg.uncheck("#pg-recusar"); pg.click("#dlg-pagamento >> text=Pagar")
        expect(pg.locator("#msg-pagamento")).to_contain_text("aprovado")
        pg.click("#dlg-pagamento >> text=Fechar")

    @teste("UI16", "US11", "Meus pedidos mostra o pedido com status PAGO e pagamento APROVADO")
    def _():
        pg.click("#aba-pedidos")
        expect(pg.locator(".pedido-card")).to_have_count(1)
        expect(pg.locator(".pedido-card")).to_contain_text("PAGO")
        expect(pg.locator(".pedido-card")).to_contain_text("APROVADO")
        fechar_drawer(pg)

    @teste("UI17", "ACESSO", "Recarregar a página mantém o usuário logado")
    def _():
        pg.reload()
        expect(pg.locator("#tela-loja")).to_be_visible()
        expect(pg.locator("#tela-login")).to_be_hidden()

    @teste("UI18", "ACESSO", "Cliente logado não vê o painel do dono em admin.html")
    def _():
        pg.goto(URL + "/admin.html")
        expect(pg.locator("#painel")).to_be_hidden()
        expect(pg.locator("#sec-login")).to_be_visible()

    @teste("UI19", "ACESSO", "Sair devolve à tela de login")
    def _():
        pg.goto(URL + "/")
        pg.click("header >> text=Sair")
        expect(pg.locator("#tela-login")).to_be_visible()
        expect(pg.locator("#tela-loja")).to_be_hidden()

    # ------------------------------------------------------------------ cliente João (sem endereço)
    pg = nova_pagina(browser)

    @teste("UI20", "US07", "Sem endereço, a janela de finalização já abre o formulário de novo endereço")
    def _():
        login(pg, "joao@email.com")
        expect(pg.locator(".card")).to_have_count(5)
        pg.locator(".card").nth(3).locator("text=Adicionar").click()
        expect(pg.locator("#badge")).to_have_text("1")
        pg.click("header >> text=Carrinho"); pg.click("#btn-finalizar")
        expect(pg.locator("#form-end")).to_be_visible()

    @teste("UI21", "US07", "CEP inválido mostra erro e o endereço não é salvo")
    def _():
        for i, v in [("e-cep", "123"), ("e-rua", "Rua A"), ("e-num", "10"), ("e-bairro", "Centro"), ("e-cidade", "Goiânia"), ("e-uf", "GO")]:
            pg.fill("#" + i, v)
        pg.click("#form-end >> text=Salvar endereço")
        expect(pg.locator("#msg-checkout")).to_contain_text("CEP")

    @teste("UI22", "US07", "Endereço válido é salvo e já fica selecionado")
    def _():
        pg.fill("#e-cep", "74000-000")
        pg.click("#form-end >> text=Salvar endereço")
        expect(pg.locator("#msg-checkout")).to_contain_text("salvo")
        expect(pg.locator("#sel-endereco")).to_contain_text("Rua A, 10")

    @teste("UI23", "US09", "Pedido confirmado fica 'aguardando pagamento' com botão Pagar agora")
    def _():
        pg.click("#dlg-checkout >> text=Confirmar pedido")
        expect(pg.locator("#dlg-pagamento")).to_be_visible()
        pg.click("#dlg-pagamento >> text=Fechar")
        expect(pg.locator(".pedido-card")).to_contain_text("AGUARDANDO")
        expect(pg.locator(".pedido-card >> text=Pagar agora")).to_be_visible()

    # ------------------------------------------------------------------ dono
    pg = nova_pagina(browser)

    @teste("UI24", "ACESSO", "Administrador que entra pela tela de login vai direto ao painel do dono")
    def _():
        login(pg, "admin@cupcakes.com")
        pg.wait_for_url("**/admin.html")
        expect(pg.locator("#painel")).to_be_visible()

    @teste("UI25", "US15", "Dashboard mostra vendas, mais vendidos e estoque")
    def _():
        expect(pg.locator("#dashboard")).to_contain_text("Vendas por dia")
        expect(pg.locator("#dashboard")).to_contain_text("Mais vendidos")
        expect(pg.locator("#dashboard")).to_contain_text("Brigadeiro Gourmet")
        expect(pg.locator("#dashboard")).to_contain_text("BAIXO")

    @teste("UI26", "US14", "Dono muda o status de PAGO para EM_PREPARO")
    def _():
        linha = pg.locator("#pedidos tr", has_text="Maria Silva")
        linha.locator("select").select_option("EM_PREPARO")
        expect(pg.locator("#msg-pedidos")).to_contain_text("atualizado")

    @teste("UI27", "US14", "Pular etapa (EM_PREPARO para ENTREGUE) mostra a mensagem de erro")
    def _():
        linha = pg.locator("#pedidos tr", has_text="Maria Silva")
        linha.locator("select").select_option("ENTREGUE")
        expect(pg.locator("#msg-pedidos")).to_contain_text("inválida")

    @teste("UI28", "US12", "Dono cadastra produto novo e ele aparece na lista de produtos")
    def _():
        pg.fill("#p-nome", "Red Velvet"); pg.fill("#p-preco", "12.5"); pg.fill("#p-estoque", "10")
        pg.fill("#p-desc", "Massa vermelha"); pg.check(".cat >> nth=3")
        pg.click("#painel >> text=Salvar")
        expect(pg.locator("#msg-produto")).to_contain_text("salvo")
        expect(pg.locator("#produtos")).to_contain_text("Red Velvet")

    @teste("UI29", "US12", "Cadastro com preço vazio é barrado na tela")
    def _():
        pg.fill("#p-nome", "Sem Preço"); pg.fill("#p-desc", "x")
        pg.click("#painel >> text=Salvar")
        expect(pg.locator("#msg-produto")).to_contain_text("preço")
        pg.click("#painel >> text=Limpar")

    @teste("UI30", "US13", "Dono edita o estoque de um produto (repor estoque)")
    def _():
        pg.locator("#produtos tr", has_text="Limão").locator("text=Editar").click()
        pg.fill("#p-estoque", "24")
        pg.click("#painel >> text=Salvar")
        expect(pg.locator("#msg-produto")).to_contain_text("salvo")
        expect(pg.locator("#produtos tr", has_text="Limão")).to_contain_text("24")

    @teste("UI31", "SEGURANÇA", "Nome de produto com HTML aparece como texto (sem executar código)")
    def _():
        pg.fill("#p-nome", "<img src=x onerror=alert(1)>"); pg.fill("#p-preco", "5"); pg.fill("#p-desc", "teste")
        pg.click("#painel >> text=Salvar")
        expect(pg.locator("#msg-produto")).to_contain_text("salvo")
        # vitrine do cliente
        cliente = nova_pagina(browser)
        login(cliente, "maria@email.com")
        expect(cliente.locator(".card", has_text="<img src=x")).to_have_count(1)
        assert cliente.locator(".card img[src='x']").count() == 0
        pagina_atual[0] = pg


    # ---------------------------------------- correções da rodada de testes com colegas (D4, D5, D6)
    def editar_estoque(nome, valor):
        pg.reload(); pg.wait_for_selector("#produtos tr")
        pg.locator("#produtos tr", has_text=nome).locator("text=Editar").click()
        pg.fill("#p-estoque", str(valor))
        pg.click("#painel >> text=Salvar")
        expect(pg.locator("#msg-produto")).to_contain_text("salvo")

    def cartao(pagina, nome):
        return pagina.locator(".card", has_text=nome)

    @teste("UI32", "US04, RN09", "Produto esgotado aparece com a marca \"Esgotado\" e o botão de compra desabilitado (D4)")
    def _():
        editar_estoque("Baunilha", 0)
        c = nova_pagina(browser)
        login(c, "joao@email.com")
        card = cartao(c, "Baunilha")
        expect(card).to_contain_text("Esgotado")
        expect(card.get_by_role("button", name="Esgotado")).to_be_disabled()
        expect(card.locator("input.qtd")).to_have_count(0)
        expect(cartao(c, "Brigadeiro").locator("input.qtd")).to_have_count(1)   # os demais continuam à venda
        globals()["cliente2"] = c

    @teste("UI33", "US04", "Escolher a quantidade na vitrine adiciona várias unidades de uma vez (D5)")
    def _():
        c = globals()["cliente2"]; pagina_atual[0] = c
        card = cartao(c, "Brigadeiro")
        card.locator("input.qtd").fill("3")
        card.get_by_role("button", name="Adicionar", exact=True).click()
        expect(c.locator("#msg-vitrine")).to_contain_text("3 itens adicionados")
        expect(c.locator("#badge")).to_have_text("3")
        c.click("text=🛒 Carrinho"); c.wait_for_timeout(400)
        expect(c.locator("#carrinho input.qtd")).to_have_value("3")

    @teste("UI34", "US05", "No carrinho, os botões + e − e o campo numérico alteram a quantidade e o total (D5)")
    def _():
        c = globals()["cliente2"]; pagina_atual[0] = c
        c.get_by_label("Aumentar").click()
        expect(c.locator("#carrinho input.qtd")).to_have_value("4")
        expect(c.locator("#badge")).to_have_text("4")
        expect(c.locator("#total-carrinho")).to_contain_text("38,00")      # 4 x 9,50
        c.get_by_label("Diminuir").click()
        expect(c.locator("#carrinho input.qtd")).to_have_value("3")
        c.locator("#carrinho input.qtd").fill("5"); c.locator("#carrinho input.qtd").press("Tab")
        expect(c.locator("#total-carrinho")).to_contain_text("47,50")      # 5 x 9,50
        expect(c.locator("#badge")).to_have_text("5")

    @teste("UI35", "RN09", "Quantidade acima do estoque no carrinho é recusada com mensagem clara e o valor volta ao anterior (D4)")
    def _():
        c = globals()["cliente2"]; pagina_atual[0] = c
        c.locator("#carrinho input.qtd").fill("99"); c.locator("#carrinho input.qtd").press("Tab")
        expect(c.locator("#msg-carrinho")).to_contain_text("Estoque insuficiente")
        expect(c.locator("#carrinho input.qtd")).to_have_value("5")
        expect(c.locator("#badge")).to_have_text("5")

    @teste("UI36", "US04, RN09", "Adicionar mais do que o estoque pela vitrine mostra o erro e mantém o carrinho (D4)")
    def _():
        c = globals()["cliente2"]; pagina_atual[0] = c
        fechar_drawer(c)
        card = cartao(c, "Limão")                       # estoque = 24 (reposto em UI30)
        card.locator("input.qtd").fill("30")
        card.get_by_role("button", name="Adicionar", exact=True).click()
        expect(c.locator("#msg-vitrine")).to_contain_text("Estoque insuficiente")
        expect(c.locator("#badge")).to_have_text("5")

    @teste("UI37", "US15", "Dashboard do dono atualiza sozinho, sem recarregar a página (D6)")
    def _():
        adm = browser.new_context(viewport={"width": 1100, "height": 800}).new_page()
        pagina_atual[0] = adm
        adm.add_init_script("window.INTERVALO_DASHBOARD_MS = 1000;")   # no uso normal são 10 s
        login(adm, "admin@cupcakes.com")
        adm.wait_for_url("**/admin.html")
        adm.wait_for_selector("#dashboard table")
        expect(adm.locator("#dash-atualizado")).to_contain_text("Atualizado às")
        expect(adm.locator("#dashboard tr", has_text="Morango")).to_contain_text("OK")
        pagina_atual[0] = pg
        editar_estoque("Morango", 3)                     # outra sessão do dono altera o estoque
        pagina_atual[0] = adm
        expect(adm.locator("#dashboard tr", has_text="Morango")).to_contain_text("BAIXO", timeout=8000)

    @teste("UI38", "US15", "Botão \"Atualizar agora\" recarrega o dashboard sob demanda")
    def _():
        pg.reload(); pg.wait_for_selector("#dashboard table")
        antes = pg.locator("#dash-atualizado").inner_text()
        pg.wait_for_timeout(1100)
        pg.click("#btn-atualizar-dash")
        pg.wait_for_function("t => document.getElementById('dash-atualizado').innerText !== t", arg=antes)

    browser.close()

total = len(resultados)
ok = sum(r["resultado"] == "APROVADO" for r in resultados)
(SAIDA / "interface.json").write_text(json.dumps(resultados, ensure_ascii=False, indent=1), encoding="utf-8")
print(f"\nInterface: {ok}/{total} testes aprovados")
sys.exit(0 if ok == total else 1)
