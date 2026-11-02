-- =============================================================================
-- Loja Virtual de Cupcakes - Projeto físico do banco de dados
-- SGBD: PostgreSQL 14+ (testado no 16)
-- Projeto Integrador Transdisciplinar (PITE) - Engenharia de Software
-- Autor: Fellipe Augusto Gonçalves Magalhães
-- =============================================================================

-- Recria o schema do zero (apenas para ambiente de desenvolvimento)
DROP SCHEMA IF EXISTS loja CASCADE;
CREATE SCHEMA loja;
SET search_path TO loja;

-- -----------------------------------------------------------------------------
-- 1. DOMÍNIOS E TIPOS ENUMERADOS
-- -----------------------------------------------------------------------------
CREATE TYPE perfil_usuario     AS ENUM ('CLIENTE', 'ADMINISTRADOR');
CREATE TYPE status_carrinho    AS ENUM ('ABERTO', 'CONVERTIDO', 'ABANDONADO');
CREATE TYPE status_pedido      AS ENUM ('AGUARDANDO_PAGAMENTO', 'PAGO', 'EM_PREPARO',
                                        'ENVIADO', 'ENTREGUE', 'CANCELADO');
CREATE TYPE metodo_pagamento   AS ENUM ('PIX', 'CARTAO');
CREATE TYPE status_pagamento   AS ENUM ('PENDENTE', 'APROVADO', 'RECUSADO', 'ESTORNADO');

CREATE DOMAIN d_cep AS CHAR(8)      CHECK (VALUE ~ '^[0-9]{8}$');
CREATE DOMAIN d_uf  AS CHAR(2)      CHECK (VALUE ~ '^[A-Z]{2}$');
CREATE DOMAIN d_dinheiro AS NUMERIC(10,2) CHECK (VALUE >= 0);

-- -----------------------------------------------------------------------------
-- 2. TABELAS
-- -----------------------------------------------------------------------------

-- Classe Usuario (generalização de Cliente e Administrador)
CREATE TABLE usuario (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome           VARCHAR(100)  NOT NULL CHECK (length(trim(nome)) >= 2),
    email          VARCHAR(150)  NOT NULL,
    senha_hash     VARCHAR(255)  NOT NULL,          -- nunca armazenar senha em texto puro
    perfil         perfil_usuario NOT NULL DEFAULT 'CLIENTE',
    ativo          BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em      TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_email CHECK (email ~* '^[^@\s]+@[^@\s]+\.[^@\s]+$')
);

-- Classe Cliente (especialização 1:1 de Usuario)
CREATE TABLE cliente (
    usuario_id     BIGINT PRIMARY KEY REFERENCES usuario(id) ON DELETE CASCADE,
    telefone       VARCHAR(15) CHECK (telefone ~ '^[0-9]{10,15}$')
);

-- Atributo "endereco: String" do diagrama de classes, normalizado
CREATE TABLE endereco (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id     BIGINT       NOT NULL REFERENCES cliente(usuario_id) ON DELETE CASCADE,
    apelido        VARCHAR(30),                     -- ex.: "Casa", "Trabalho"
    cep            d_cep        NOT NULL,
    logradouro     VARCHAR(150) NOT NULL,
    numero         VARCHAR(10)  NOT NULL,
    complemento    VARCHAR(60),
    bairro         VARCHAR(80)  NOT NULL,
    cidade         VARCHAR(80)  NOT NULL,
    uf             d_uf         NOT NULL,
    principal      BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE
);
-- no máximo um endereço principal ativo por cliente
CREATE UNIQUE INDEX uq_endereco_principal
    ON endereco (cliente_id) WHERE principal AND ativo;

CREATE TABLE categoria (
    id             SMALLINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome           VARCHAR(50) NOT NULL,
    ativa          BOOLEAN     NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_categoria_nome UNIQUE (nome)
);

CREATE TABLE cupcake (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome           VARCHAR(100) NOT NULL,
    descricao      TEXT         NOT NULL,
    ingredientes   TEXT,
    preco          d_dinheiro   NOT NULL CHECK (preco > 0),
    imagem_url     VARCHAR(255),
    estoque        INTEGER      NOT NULL DEFAULT 0 CHECK (estoque >= 0),
    destaque       BOOLEAN      NOT NULL DEFAULT FALSE,
    ativo          BOOLEAN      NOT NULL DEFAULT TRUE,   -- RN01: exibir só ativos
    criado_em      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_cupcake_nome UNIQUE (nome)
);

-- Relação N:N: um cupcake pode ser "chocolate" e "vegano" ao mesmo tempo (US02)
CREATE TABLE cupcake_categoria (
    cupcake_id     BIGINT   NOT NULL REFERENCES cupcake(id)   ON DELETE CASCADE,
    categoria_id   SMALLINT NOT NULL REFERENCES categoria(id) ON DELETE RESTRICT,
    PRIMARY KEY (cupcake_id, categoria_id)
);

-- Classe Carrinho (persistido no banco: RNF04)
CREATE TABLE carrinho (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id     BIGINT          NOT NULL REFERENCES cliente(usuario_id) ON DELETE CASCADE,
    status         status_carrinho NOT NULL DEFAULT 'ABERTO',
    criado_em      TIMESTAMPTZ     NOT NULL DEFAULT now(),
    atualizado_em  TIMESTAMPTZ     NOT NULL DEFAULT now()
);
-- cada cliente tem no máximo 1 carrinho aberto
CREATE UNIQUE INDEX uq_carrinho_aberto ON carrinho (cliente_id) WHERE status = 'ABERTO';

CREATE TABLE item_carrinho (
    carrinho_id    BIGINT   NOT NULL REFERENCES carrinho(id) ON DELETE CASCADE,
    cupcake_id     BIGINT   NOT NULL REFERENCES cupcake(id)  ON DELETE RESTRICT,
    quantidade     SMALLINT NOT NULL CHECK (quantidade >= 1),   -- RN04 / RN05
    PRIMARY KEY (carrinho_id, cupcake_id)
);

-- Classe Pedido. O endereço é "congelado" (snapshot) no momento da compra.
CREATE TABLE pedido (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id         BIGINT        NOT NULL REFERENCES cliente(usuario_id) ON DELETE RESTRICT,
    status             status_pedido NOT NULL DEFAULT 'AGUARDANDO_PAGAMENTO',
    data_pedido        TIMESTAMPTZ   NOT NULL DEFAULT now(),
    entrega_cep        d_cep         NOT NULL,
    entrega_logradouro VARCHAR(150)  NOT NULL,
    entrega_numero     VARCHAR(10)   NOT NULL,
    entrega_complemento VARCHAR(60),
    entrega_bairro     VARCHAR(80)   NOT NULL,
    entrega_cidade     VARCHAR(80)   NOT NULL,
    entrega_uf         d_uf          NOT NULL,
    taxa_entrega       d_dinheiro    NOT NULL DEFAULT 0,
    valor_total        d_dinheiro    NOT NULL,
    atualizado_em      TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Preço unitário gravado no pedido: alterar o preço do cupcake não muda pedidos antigos
CREATE TABLE item_pedido (
    pedido_id      BIGINT     NOT NULL REFERENCES pedido(id)  ON DELETE CASCADE,
    cupcake_id     BIGINT     NOT NULL REFERENCES cupcake(id) ON DELETE RESTRICT,
    quantidade     SMALLINT   NOT NULL CHECK (quantidade >= 1),
    preco_unitario d_dinheiro NOT NULL CHECK (preco_unitario > 0),
    subtotal       NUMERIC(10,2) GENERATED ALWAYS AS (quantidade * preco_unitario) STORED,
    PRIMARY KEY (pedido_id, cupcake_id)
);

-- Classe Pagamento. 1 pedido -> N tentativas (CU10: pagamento recusado volta à escolha)
-- Dados de cartão NÃO são armazenados (RNF08): apenas o identificador do gateway.
CREATE TABLE pagamento (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id           BIGINT           NOT NULL REFERENCES pedido(id) ON DELETE RESTRICT,
    metodo              metodo_pagamento NOT NULL,
    status              status_pagamento NOT NULL DEFAULT 'PENDENTE',
    valor               d_dinheiro       NOT NULL CHECK (valor > 0),
    gateway_transacao_id VARCHAR(100),
    criado_em           TIMESTAMPTZ      NOT NULL DEFAULT now(),
    processado_em       TIMESTAMPTZ,
    CONSTRAINT uq_pagamento_gateway UNIQUE (gateway_transacao_id),
    CONSTRAINT ck_pagamento_processado
        CHECK (status = 'PENDENTE' OR processado_em IS NOT NULL)
);
-- no máximo um pagamento APROVADO por pedido
CREATE UNIQUE INDEX uq_pagamento_aprovado ON pagamento (pedido_id) WHERE status = 'APROVADO';

-- Fluxo permitido de status (RN14 - "fluxo correto") e histórico (auditoria / US11)
CREATE TABLE transicao_status_pedido (
    de             status_pedido NOT NULL,
    para           status_pedido NOT NULL,
    PRIMARY KEY (de, para)
);
INSERT INTO transicao_status_pedido (de, para) VALUES
    ('AGUARDANDO_PAGAMENTO', 'PAGO'),
    ('AGUARDANDO_PAGAMENTO', 'CANCELADO'),
    ('PAGO',                 'EM_PREPARO'),
    ('PAGO',                 'CANCELADO'),
    ('EM_PREPARO',           'ENVIADO'),
    ('EM_PREPARO',           'CANCELADO'),
    ('ENVIADO',              'ENTREGUE');

CREATE TABLE historico_status_pedido (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pedido_id      BIGINT        NOT NULL REFERENCES pedido(id) ON DELETE CASCADE,
    status_anterior status_pedido,
    status_novo    status_pedido NOT NULL,
    alterado_por   BIGINT        REFERENCES usuario(id) ON DELETE SET NULL,
    alterado_em    TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- -----------------------------------------------------------------------------
-- 3. ÍNDICES DE DESEMPENHO (RNF01, RNF02, RNF15)
-- -----------------------------------------------------------------------------
CREATE INDEX ix_cupcake_vitrine      ON cupcake (destaque DESC, nome) WHERE ativo;
CREATE INDEX ix_cupcake_categoria    ON cupcake_categoria (categoria_id, cupcake_id);
CREATE INDEX ix_endereco_cliente     ON endereco (cliente_id);
CREATE INDEX ix_pedido_cliente       ON pedido (cliente_id, data_pedido DESC);
CREATE INDEX ix_pedido_status_data   ON pedido (status, data_pedido);
CREATE INDEX ix_item_pedido_cupcake  ON item_pedido (cupcake_id);
CREATE INDEX ix_pagamento_pedido     ON pagamento (pedido_id);
CREATE INDEX ix_historico_pedido     ON historico_status_pedido (pedido_id, alterado_em);

-- -----------------------------------------------------------------------------
-- 4. TRIGGERS (regras de negócio no banco)
-- -----------------------------------------------------------------------------

-- 4.1 Atualiza "atualizado_em" automaticamente
CREATE FUNCTION fn_toca_atualizado_em() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.atualizado_em := now();
    RETURN NEW;
END $$;

CREATE TRIGGER tg_cupcake_atualizado  BEFORE UPDATE ON cupcake
    FOR EACH ROW EXECUTE FUNCTION fn_toca_atualizado_em();
CREATE TRIGGER tg_carrinho_atualizado BEFORE UPDATE ON carrinho
    FOR EACH ROW EXECUTE FUNCTION fn_toca_atualizado_em();
CREATE TRIGGER tg_pedido_atualizado   BEFORE UPDATE ON pedido
    FOR EACH ROW EXECUTE FUNCTION fn_toca_atualizado_em();

-- 4.2 RN14: valida a transição de status e grava histórico
CREATE FUNCTION fn_valida_status_pedido() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status IS DISTINCT FROM OLD.status THEN
        IF NOT EXISTS (SELECT 1 FROM transicao_status_pedido
                        WHERE de = OLD.status AND para = NEW.status) THEN
            RAISE EXCEPTION 'Transição de status inválida: % -> %', OLD.status, NEW.status
                USING ERRCODE = 'check_violation';
        END IF;
        INSERT INTO historico_status_pedido (pedido_id, status_anterior, status_novo, alterado_por)
        VALUES (NEW.id, OLD.status, NEW.status,
                NULLIF(current_setting('loja.usuario_id', true), '')::BIGINT);
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_pedido_status BEFORE UPDATE OF status ON pedido
    FOR EACH ROW EXECUTE FUNCTION fn_valida_status_pedido();

-- 4.3 Registra o status inicial do pedido no histórico
CREATE FUNCTION fn_historico_inicial() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO historico_status_pedido (pedido_id, status_anterior, status_novo, alterado_por)
    VALUES (NEW.id, NULL, NEW.status, NEW.cliente_id);
    RETURN NEW;
END $$;

CREATE TRIGGER tg_pedido_historico_inicial AFTER INSERT ON pedido
    FOR EACH ROW EXECUTE FUNCTION fn_historico_inicial();

-- 4.4 Pagamento aprovado => pedido passa para PAGO (US10 / RN10)
CREATE FUNCTION fn_pagamento_aprovado() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.status = 'APROVADO' AND OLD.status IS DISTINCT FROM 'APROVADO' THEN
        UPDATE pedido SET status = 'PAGO'
         WHERE id = NEW.pedido_id AND status = 'AGUARDANDO_PAGAMENTO';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_pagamento_aprovado AFTER UPDATE OF status ON pagamento
    FOR EACH ROW EXECUTE FUNCTION fn_pagamento_aprovado();

-- 4.5 Só administradores podem ter o perfil usado para alterar produtos/pedidos
--     (garantia mínima no banco: cliente_id só pode referenciar usuário CLIENTE)
CREATE FUNCTION fn_cliente_deve_ser_perfil_cliente() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM usuario WHERE id = NEW.usuario_id AND perfil = 'CLIENTE') THEN
        RAISE EXCEPTION 'Usuário % não possui perfil CLIENTE', NEW.usuario_id
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END $$;

CREATE TRIGGER tg_cliente_perfil BEFORE INSERT OR UPDATE ON cliente
    FOR EACH ROW EXECUTE FUNCTION fn_cliente_deve_ser_perfil_cliente();

-- -----------------------------------------------------------------------------
-- 5. FUNÇÃO TRANSACIONAL: CONFIRMAR PEDIDO (CU09 / US09)
--    Converte o carrinho aberto do cliente em pedido, copia preços e endereço,
--    baixa o estoque e fecha o carrinho. Tudo ou nada.
-- -----------------------------------------------------------------------------
CREATE FUNCTION fn_confirmar_pedido(p_cliente_id BIGINT,
                                    p_endereco_id BIGINT,
                                    p_taxa_entrega NUMERIC DEFAULT 0)
RETURNS BIGINT LANGUAGE plpgsql AS $$
DECLARE
    v_carrinho_id BIGINT;
    v_pedido_id   BIGINT;
    v_total       NUMERIC(10,2);
BEGIN
    SELECT id INTO v_carrinho_id
      FROM carrinho WHERE cliente_id = p_cliente_id AND status = 'ABERTO' FOR UPDATE;
    IF v_carrinho_id IS NULL THEN
        RAISE EXCEPTION 'Cliente % não possui carrinho aberto', p_cliente_id;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM item_carrinho WHERE carrinho_id = v_carrinho_id) THEN
        RAISE EXCEPTION 'Carrinho vazio';
    END IF;

    -- bloqueia os cupcakes e valida disponibilidade (RN09)
    PERFORM 1 FROM cupcake c
      JOIN item_carrinho i ON i.cupcake_id = c.id
     WHERE i.carrinho_id = v_carrinho_id FOR UPDATE OF c;

    IF EXISTS (SELECT 1 FROM item_carrinho i JOIN cupcake c ON c.id = i.cupcake_id
                WHERE i.carrinho_id = v_carrinho_id
                  AND (NOT c.ativo OR c.estoque < i.quantidade)) THEN
        RAISE EXCEPTION 'Há item inativo ou sem estoque suficiente no carrinho';
    END IF;

    SELECT SUM(i.quantidade * c.preco) INTO v_total
      FROM item_carrinho i JOIN cupcake c ON c.id = i.cupcake_id
     WHERE i.carrinho_id = v_carrinho_id;

    INSERT INTO pedido (cliente_id, entrega_cep, entrega_logradouro, entrega_numero,
                        entrega_complemento, entrega_bairro, entrega_cidade, entrega_uf,
                        taxa_entrega, valor_total)
    SELECT e.cliente_id, e.cep, e.logradouro, e.numero, e.complemento,
           e.bairro, e.cidade, e.uf, p_taxa_entrega, v_total + p_taxa_entrega
      FROM endereco e
     WHERE e.id = p_endereco_id AND e.cliente_id = p_cliente_id AND e.ativo
    RETURNING id INTO v_pedido_id;

    IF v_pedido_id IS NULL THEN
        RAISE EXCEPTION 'Endereço % inválido para o cliente %', p_endereco_id, p_cliente_id;
    END IF;

    INSERT INTO item_pedido (pedido_id, cupcake_id, quantidade, preco_unitario)
    SELECT v_pedido_id, i.cupcake_id, i.quantidade, c.preco
      FROM item_carrinho i JOIN cupcake c ON c.id = i.cupcake_id
     WHERE i.carrinho_id = v_carrinho_id;

    UPDATE cupcake c SET estoque = c.estoque - i.quantidade
      FROM item_carrinho i
     WHERE i.carrinho_id = v_carrinho_id AND c.id = i.cupcake_id;

    UPDATE carrinho SET status = 'CONVERTIDO' WHERE id = v_carrinho_id;

    RETURN v_pedido_id;
END $$;

-- -----------------------------------------------------------------------------
-- 6. VIEWS (consultas prontas para a API e para o dashboard - US01, US15)
--    A classe "Relatorio" do diagrama não vira tabela: é gerada por consulta.
-- -----------------------------------------------------------------------------

-- Vitrine (RN01: somente ativos; ordenada por destaque)
CREATE VIEW vw_vitrine AS
SELECT c.id, c.nome, c.preco, c.imagem_url, c.destaque,
       COALESCE(string_agg(cat.nome, ', ' ORDER BY cat.nome), '') AS categorias
  FROM cupcake c
  LEFT JOIN cupcake_categoria cc ON cc.cupcake_id = c.id
  LEFT JOIN categoria cat        ON cat.id = cc.categoria_id AND cat.ativa
 WHERE c.ativo
 GROUP BY c.id
 ORDER BY c.destaque DESC, c.nome;

-- Carrinho com subtotais e total (US06)
CREATE VIEW vw_carrinho_detalhado AS
SELECT ca.id AS carrinho_id, ca.cliente_id, c.id AS cupcake_id, c.nome,
       i.quantidade, c.preco, (i.quantidade * c.preco) AS subtotal,
       SUM(i.quantidade * c.preco) OVER (PARTITION BY ca.id) AS total
  FROM carrinho ca
  JOIN item_carrinho i ON i.carrinho_id = ca.id
  JOIN cupcake c       ON c.id = i.cupcake_id
 WHERE ca.status = 'ABERTO';

-- Vendas por dia (considera pedidos pagos ou além, exceto cancelados)
CREATE VIEW vw_vendas_diarias AS
SELECT date_trunc('day', data_pedido)::date AS dia,
       COUNT(*)                 AS qtd_pedidos,
       SUM(valor_total)         AS faturamento,
       ROUND(AVG(valor_total),2) AS ticket_medio
  FROM pedido
 WHERE status NOT IN ('AGUARDANDO_PAGAMENTO', 'CANCELADO')
 GROUP BY 1
 ORDER BY 1 DESC;

-- Ranking de produtos mais vendidos
CREATE VIEW vw_produtos_mais_vendidos AS
SELECT c.id, c.nome, SUM(ip.quantidade) AS unidades_vendidas, SUM(ip.subtotal) AS receita
  FROM item_pedido ip
  JOIN pedido p  ON p.id = ip.pedido_id
  JOIN cupcake c ON c.id = ip.cupcake_id
 WHERE p.status NOT IN ('AGUARDANDO_PAGAMENTO', 'CANCELADO')
 GROUP BY c.id, c.nome
 ORDER BY unidades_vendidas DESC;

-- Relatório de estoque (gerarEstoque())
CREATE VIEW vw_estoque AS
SELECT id, nome, estoque, ativo,
       CASE WHEN estoque = 0 THEN 'ESGOTADO'
            WHEN estoque <= 5 THEN 'BAIXO'
            ELSE 'OK' END AS situacao
  FROM cupcake
 ORDER BY estoque, nome;

-- Pedidos para acompanhamento (US11, US14)
CREATE VIEW vw_pedidos_resumo AS
SELECT p.id, p.cliente_id, u.nome AS cliente, p.status, p.data_pedido, p.valor_total,
       (SELECT pg.status FROM pagamento pg WHERE pg.pedido_id = p.id
         ORDER BY pg.criado_em DESC LIMIT 1) AS status_pagamento
  FROM pedido p JOIN usuario u ON u.id = p.cliente_id;

-- -----------------------------------------------------------------------------
-- 7. PAPÉIS E PERMISSÕES (princípio do menor privilégio - RNF08, RNF12)
-- -----------------------------------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'app_loja') THEN
        CREATE ROLE app_loja NOLOGIN;
    END IF;
END $$;
GRANT USAGE ON SCHEMA loja TO app_loja;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA loja TO app_loja;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA loja TO app_loja;
REVOKE DELETE ON pedido, item_pedido, pagamento, historico_status_pedido FROM app_loja;
-- pedidos e pagamentos nunca são apagados: apenas cancelados/estornados

-- -----------------------------------------------------------------------------
-- 8. DADOS INICIAIS (carga para desenvolvimento e testes)
-- -----------------------------------------------------------------------------
INSERT INTO usuario (nome, email, senha_hash, perfil) VALUES
    ('Dona da Loja', 'admin@cupcakes.com', encode(sha256('123456'::bytea),'hex'), 'ADMINISTRADOR'),
    ('Maria Silva',  'maria@email.com',    encode(sha256('123456'::bytea),'hex'), 'CLIENTE'),
    ('João Souza',   'joao@email.com',     encode(sha256('123456'::bytea),'hex'),  'CLIENTE');

INSERT INTO cliente (usuario_id, telefone)
    SELECT id, '62999990000' FROM usuario WHERE email = 'maria@email.com';
INSERT INTO cliente (usuario_id, telefone)
    SELECT id, '62988880000' FROM usuario WHERE email = 'joao@email.com';

INSERT INTO endereco (cliente_id, apelido, cep, logradouro, numero, bairro, cidade, uf, principal)
    SELECT id, 'Casa', '74000000', 'Rua das Flores', '123', 'Centro', 'Goiânia', 'GO', TRUE
      FROM usuario WHERE email = 'maria@email.com';

INSERT INTO categoria (nome) VALUES ('Chocolate'), ('Vegano'), ('Sem lactose'), ('Frutas'), ('Tradicional');

INSERT INTO cupcake (nome, descricao, ingredientes, preco, estoque, destaque) VALUES
    ('Brigadeiro Gourmet', 'Massa de chocolate com cobertura de brigadeiro', 'Farinha, cacau, ovos, leite condensado, granulado', 9.50, 30, TRUE),
    ('Vegano de Cacau',    'Cupcake vegano de cacau com ganache',            'Farinha, cacau 70%, óleo de coco, leite vegetal', 11.00, 20, TRUE),
    ('Morango Cremoso',    'Massa branca com creme de morango',              'Farinha, ovos, morango, creme de leite',          10.00, 15, FALSE),
    ('Baunilha Clássico',  'Massa de baunilha com buttercream',              'Farinha, ovos, baunilha, manteiga',                8.00, 40, FALSE),
    ('Limão sem Lactose',  'Massa cítrica sem lactose',                      'Farinha, limão, ovos, óleo vegetal',               9.00,  4, FALSE);

INSERT INTO cupcake_categoria (cupcake_id, categoria_id)
    SELECT c.id, k.id FROM (VALUES
        ('Brigadeiro Gourmet','Chocolate'),
        ('Vegano de Cacau','Chocolate'), ('Vegano de Cacau','Vegano'), ('Vegano de Cacau','Sem lactose'),
        ('Morango Cremoso','Frutas'),
        ('Baunilha Clássico','Tradicional'),
        ('Limão sem Lactose','Frutas'), ('Limão sem Lactose','Sem lactose')
    ) AS v(cup, cat)
    JOIN cupcake c ON c.nome = v.cup
    JOIN categoria k ON k.nome = v.cat;
