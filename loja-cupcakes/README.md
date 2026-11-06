# Loja Virtual de Cupcakes (PITE II)

Repositório: <https://github.com/Fellp-coffww/Loja-Cupcake>

Aplicação web simples em **Java puro** (sem Spring ou outro framework) com padrão **MVC**,
banco **PostgreSQL** e front-end em HTML/CSS/JavaScript puro.

## Requisitos
- JDK 17 ou superior
- PostgreSQL 14 ou superior
- O driver JDBC já está em `lib/postgresql-42.7.2.jar`

## Como rodar

1. Crie o banco e carregue o esquema (isso também insere dados de demonstração):

   ```
   createdb -U postgres loja_cupcakes
   psql -U postgres -d loja_cupcakes -f sql/schema_loja_cupcakes.sql
   ```

2. Compile: `./compilar.sh` (Linux/Mac) ou `compilar.bat` (Windows).

3. Execute: `./executar.sh` ou `executar.bat`, e abra <http://localhost:8080>.

Se a senha do seu PostgreSQL não for `postgres`, defina antes de executar:

```
export DB_PASSWORD=sua_senha          (Windows: set DB_PASSWORD=sua_senha)
```

Outras variáveis: `DB_URL` (padrão `jdbc:postgresql://localhost:5432/loja_cupcakes?currentSchema=loja`),
`DB_USER` (padrão `postgres`) e `PORT` (padrão `8080`).

## Contas de demonstração (senha `123456`)
| Perfil | E-mail |
|---|---|
| Cliente | maria@email.com, joao@email.com |
| Administrador | admin@cupcakes.com (painel em `/admin.html`) |

## Estrutura (MVC)

```
src/loja/
  Main.java              inicia o servidor HTTP do JDK e registra as rotas
  model/                 M - registros simples: Cupcake, Pedido, Endereco, ItemCarrinho, Usuario
  dao/                   M - acesso ao banco via JDBC (um DAO por assunto)
  controller/            C - tratam as requisições /api/... e devolvem JSON
  util/                  apoio: conexão, JSON, sessão, senha, respostas HTTP
public/                  V - telas: index.html (cliente), admin.html (dono), JS e CSS
sql/                     esquema, regras e dados de demonstração do banco
test/                    testes automatizados de back-end e banco (JUnit 5)
testes/interface/        testes automatizados de interface (Playwright, Python)
docs/                    manual de uso, documento do banco, revisão UML e documento de testes
lib/                     driver JDBC do PostgreSQL e executor do JUnit
```

Fluxo de uma requisição: navegador (View) → `fetch('/api/...')` → Controller → DAO → PostgreSQL → JSON → View.

## Rotas da API

| Método e rota | Quem | História |
|---|---|---|
| `GET /api/cupcakes[?categoria=ID]` | público | US01, US02 |
| `GET /api/cupcakes/{id}` | público | US03 |
| `GET /api/categorias` | público | US02 |
| `POST /api/cadastro`, `POST /api/login`, `POST /api/logout` | público | acesso |
| `GET /api/carrinho` | cliente | US06 |
| `POST /api/carrinho/itens` `{cupcakeId, quantidade}` | cliente | US04 |
| `PUT /api/carrinho/itens/{cupcakeId}` `{quantidade}` | cliente | US05 |
| `DELETE /api/carrinho/itens/{cupcakeId}` | cliente | US05 |
| `GET/POST /api/enderecos` | cliente | US07 |
| `POST /api/pedidos` `{enderecoId}` | cliente | US09 |
| `POST /api/pedidos/{id}/pagamento` `{metodo}` | cliente | US08, US10 |
| `GET /api/pedidos` | cliente | US11 |
| `GET/POST /api/admin/cupcakes`, `PUT /api/admin/cupcakes/{id}` | dono | US12, US13 |
| `GET /api/admin/pedidos`, `PUT /api/admin/pedidos/{id}/status` | dono | US14 |
| `GET /api/admin/dashboard` | dono | US15 |

As rotas protegidas exigem o cabeçalho `Authorization: Bearer <token>` (devolvido pelo login).

## Onde ficam as regras de negócio
A maior parte está no banco (ver seção "Projeto de Banco de Dados" do documento do PITE):
quantidade mínima, fluxo de status do pedido (RN14), baixa de estoque, histórico e total do pedido.
Os controllers validam o formato dos dados e devolvem mensagens claras ao usuário.

## Simplificações (para o trabalho)
- O gateway de pagamento é **simulado** (`dao/GatewayFake.java`). Na tela de pagamento há uma opção
  para simular recusa e demonstrar o fluxo alternativo do CU10.
- Sessões ficam em memória (reiniciar o servidor desloga todos).
- Senha com SHA-256. Em produção, usar bcrypt ou argon2.
- Acompanhamento do pedido por *polling* (a cada 15 s), sem WebSocket.
- O CEP não é consultado no ViaCEP; o cliente digita o endereço.

## Testes

São 131 testes de back-end e banco (JUnit 5) e 31 de interface (Playwright). Resultados e rastreabilidade
com as histórias de usuário estão em `docs/testes/Documento_de_Testes.pdf`.

**Back-end e banco** (precisa de um banco de testes separado; o nome precisa conter `test`):

```
docker exec -it loja-db psql -U postgres -c "CREATE DATABASE loja_cupcakes_test"
./testar.sh        (Windows: testar.bat)
```

Se o banco usar outra senha ou porta, defina `TEST_DB_URL`, `TEST_DB_USER` e `TEST_DB_PASSWORD`.
Os testes apagam e recriam o esquema `loja` do banco de testes a cada caso.

**Interface** (uma vez: `pip install playwright` e `playwright install chromium`):

1. Recrie o banco de testes com os dados iniciais (`sql/schema_loja_cupcakes.sql`).
2. Suba o sistema apontando para ele (`DB_URL=jdbc:postgresql://localhost:5432/loja_cupcakes_test?currentSchema=loja`).
3. `python testes/interface/testar_interface.py http://localhost:8080`

## Próximos passos sugeridos
- Consulta de CEP (ViaCEP) e gateway real em modo sandbox.
