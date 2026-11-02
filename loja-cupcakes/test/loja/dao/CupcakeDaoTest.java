package loja.dao;

import static loja.BancoDeTestes.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import loja.BancoDeTestes;
import loja.model.Cupcake;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CupcakeDaoTest {
    private final CupcakeDao dao = new CupcakeDao();

    @BeforeEach void banco() throws Exception { BancoDeTestes.preparar(); }

    @Test @DisplayName("[US01, RN01] Vitrine lista somente produtos ativos")
    void vitrineSoAtivos() throws Exception {
        assertEquals(5, dao.listarAtivos(null).size());
        executar("UPDATE cupcake SET ativo = FALSE WHERE nome = ?", MORANGO);
        List<Cupcake> vitrine = dao.listarAtivos(null);
        assertEquals(4, vitrine.size());
        assertTrue(vitrine.stream().allMatch(Cupcake::ativo));
        assertTrue(vitrine.stream().noneMatch(c -> c.nome().equals(MORANGO)));
    }

    @Test @DisplayName("[US01] Vitrine mostra os produtos em destaque primeiro")
    void destaquePrimeiro() throws Exception {
        List<Cupcake> vitrine = dao.listarAtivos(null);
        assertTrue(vitrine.get(0).destaque());
        assertTrue(vitrine.get(1).destaque());
        assertFalse(vitrine.get(2).destaque());
    }

    @Test @DisplayName("[US02] Filtro por categoria retorna apenas produtos da categoria")
    void filtroCategoria() throws Exception {
        long vegano = contar("SELECT id FROM categoria WHERE nome = 'Vegano'");
        long semLactose = contar("SELECT id FROM categoria WHERE nome = 'Sem lactose'");
        List<Cupcake> veganos = dao.listarAtivos(vegano);
        assertEquals(1, veganos.size());
        assertEquals(VEGANO, veganos.get(0).nome());
        assertEquals(2, dao.listarAtivos(semLactose).size());
    }

    @Test @DisplayName("[US02, RN02] Categoria inexistente retorna lista vazia")
    void categoriaInexistente() throws Exception {
        assertTrue(dao.listarAtivos(9999L).isEmpty());
    }

    @Test @DisplayName("[US02] Filtro não mostra produto inativo da categoria")
    void filtroIgnoraInativos() throws Exception {
        executar("UPDATE cupcake SET ativo = FALSE WHERE nome = ?", VEGANO);
        long vegano = contar("SELECT id FROM categoria WHERE nome = 'Vegano'");
        assertTrue(dao.listarAtivos(vegano).isEmpty());
    }

    @Test @DisplayName("[US03] Detalhes trazem descrição, ingredientes, preço e categorias")
    void detalhes() throws Exception {
        Cupcake c = dao.buscarPorId(idCupcake(VEGANO));
        assertEquals(VEGANO, c.nome());
        assertEquals(new BigDecimal("11.00"), c.preco());
        assertNotNull(c.descricao());
        assertTrue(c.ingredientes().contains("cacau"));
        assertEquals("Chocolate, Sem lactose, Vegano", c.categorias());
    }

    @Test @DisplayName("[US03] Produto inexistente retorna nulo")
    void detalhesInexistente() throws Exception {
        assertNull(dao.buscarPorId(99999));
    }

    @Test @DisplayName("[US12] Cadastrar produto grava dados e categorias")
    void cadastrar() throws Exception {
        long id = dao.salvar(null, "Red Velvet", "Massa vermelha", "Farinha, cacau", new BigDecimal("12.50"),
                null, 10, true, true, List.of(1L, 5L));
        Cupcake c = dao.buscarPorId(id);
        assertEquals("Red Velvet", c.nome());
        assertEquals(10, c.estoque());
        assertEquals("Chocolate, Tradicional", c.categorias());
        assertEquals(6, dao.listarAtivos(null).size());
    }

    @Test @DisplayName("[US12, RN12] Nome de produto repetido é rejeitado")
    void nomeDuplicado() {
        SQLException e = assertThrows(SQLException.class, () ->
            dao.salvar(null, BRIGADEIRO, "x", null, new BigDecimal("5"), null, 1, false, true, List.of()));
        assertEquals("23505", e.getSQLState());
    }

    @Test @DisplayName("[US12, RN12] Preço zero e estoque negativo são rejeitados pelo banco")
    void valoresInvalidos() throws Exception {
        SQLException preco = assertThrows(SQLException.class, () ->
            dao.salvar(null, "Zero", "x", null, BigDecimal.ZERO, null, 1, false, true, List.of()));
        assertEquals("23514", preco.getSQLState());
        SQLException estoque = assertThrows(SQLException.class, () ->
            dao.salvar(null, "Negativo", "x", null, new BigDecimal("5"), null, -1, false, true, List.of()));
        assertEquals("23514", estoque.getSQLState());
        assertEquals(5, dao.listarTodos().size());   // nada foi gravado
    }

    @Test @DisplayName("[US12] Falha ao gravar categoria desfaz o cadastro inteiro (transação)")
    void cadastroTransacional() throws Exception {
        assertThrows(SQLException.class, () ->
            dao.salvar(null, "Fantasma", "x", null, new BigDecimal("5"), null, 1, false, true, List.of(9999L)));
        assertEquals(0, contar("SELECT count(*) FROM cupcake WHERE nome = 'Fantasma'"));
    }

    @Test @DisplayName("[US13, RN03] Editar produto altera preço, estoque e categorias (dado atualizado)")
    void editar() throws Exception {
        long id = idCupcake(BAUNILHA);
        dao.salvar(id, BAUNILHA, "Nova descrição", null, new BigDecimal("8.50"), null, 55, false, true, List.of(1L));
        Cupcake c = dao.buscarPorId(id);
        assertEquals(new BigDecimal("8.50"), c.preco());
        assertEquals(55, c.estoque());
        assertEquals("Chocolate", c.categorias());
    }

    @Test @DisplayName("[US13] Produto inativado sai da vitrine mas continua na lista do dono")
    void inativar() throws Exception {
        long id = idCupcake(MORANGO);
        dao.salvar(id, MORANGO, "d", null, new BigDecimal("10"), null, 15, false, false, List.of());
        assertEquals(4, dao.listarAtivos(null).size());
        assertEquals(5, dao.listarTodos().size());
    }

    @Test @DisplayName("[US13, RN13] Editar produto inexistente gera erro")
    void editarInexistente() {
        assertThrows(IllegalArgumentException.class, () ->
            dao.salvar(99999L, "X", "x", null, new BigDecimal("5"), null, 1, false, true, List.of()));
    }

    @Test @DisplayName("[US02] Lista de categorias vem ordenada por nome")
    void categorias() throws Exception {
        var cats = dao.listarCategorias();
        assertEquals(5, cats.size());
        assertEquals("Chocolate", cats.get(0).get("nome"));
        assertEquals("Vegano", cats.get(4).get("nome"));
    }
}
