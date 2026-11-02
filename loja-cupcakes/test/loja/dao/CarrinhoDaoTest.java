package loja.dao;

import static loja.BancoDeTestes.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import loja.BancoDeTestes;
import loja.model.ItemCarrinho;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CarrinhoDaoTest {
    private final CarrinhoDao dao = new CarrinhoDao();
    private long maria, joao;

    @BeforeEach void banco() throws Exception {
        BancoDeTestes.preparar();
        maria = idUsuario(MARIA);
        joao = idUsuario(JOAO);
    }

    @Test @DisplayName("[US04] Adicionar item cria o carrinho aberto e grava a quantidade")
    void adicionar() throws Exception {
        dao.adicionar(maria, idCupcake(BRIGADEIRO), 2);
        List<ItemCarrinho> itens = dao.listar(maria);
        assertEquals(1, itens.size());
        assertEquals(2, itens.get(0).quantidade());
        assertEquals(1, contar("SELECT count(*) FROM carrinho WHERE cliente_id = ? AND status = 'ABERTO'", maria));
    }

    @Test @DisplayName("[US04] Adicionar o mesmo produto de novo soma as quantidades")
    void somaQuantidade() throws Exception {
        long id = idCupcake(BRIGADEIRO);
        dao.adicionar(maria, id, 2);
        dao.adicionar(maria, id, 3);
        List<ItemCarrinho> itens = dao.listar(maria);
        assertEquals(1, itens.size());
        assertEquals(5, itens.get(0).quantidade());
        assertEquals(1, contar("SELECT count(*) FROM carrinho WHERE cliente_id = ?", maria));
    }

    @Test @DisplayName("[US04, RN04] Quantidade zero é rejeitada e nada é gravado")
    void quantidadeZero() throws Exception {
        SQLException e = assertThrows(SQLException.class, () -> dao.adicionar(maria, idCupcake(BRIGADEIRO), 0));
        assertEquals("23514", e.getSQLState());
        assertTrue(dao.listar(maria).isEmpty());
        assertEquals(0, contar("SELECT count(*) FROM carrinho WHERE cliente_id = ?", maria));
    }

    @Test @DisplayName("[US04, RN05] Quantidade negativa é rejeitada")
    void quantidadeNegativa() throws Exception {
        assertThrows(SQLException.class, () -> dao.adicionar(maria, idCupcake(BRIGADEIRO), -3));
        assertTrue(dao.listar(maria).isEmpty());
    }

    @Test @DisplayName("[US04] Produto inativo não pode ser adicionado")
    void produtoInativo() throws Exception {
        executar("UPDATE cupcake SET ativo = FALSE WHERE nome = ?", MORANGO);
        assertThrows(IllegalArgumentException.class, () -> dao.adicionar(maria, idCupcake(MORANGO), 1));
    }

    @Test @DisplayName("[US04] Produto inexistente não pode ser adicionado")
    void produtoInexistente() {
        assertThrows(IllegalArgumentException.class, () -> dao.adicionar(maria, 99999, 1));
    }

    @Test @DisplayName("[US05] Remover item tira o produto e o total é recalculado")
    void remover() throws Exception {
        dao.adicionar(maria, idCupcake(BRIGADEIRO), 2);
        dao.adicionar(maria, idCupcake(VEGANO), 1);
        dao.remover(maria, idCupcake(BRIGADEIRO));
        List<ItemCarrinho> itens = dao.listar(maria);
        assertEquals(1, itens.size());
        assertEquals(VEGANO, itens.get(0).nome());
        assertEquals(new BigDecimal("11.00"), itens.get(0).subtotal());
    }

    @Test @DisplayName("[US05] Remover item que não está no carrinho não gera erro")
    void removerAusente() throws Exception {
        assertDoesNotThrow(() -> dao.remover(maria, idCupcake(BRIGADEIRO)));
        assertDoesNotThrow(() -> dao.remover(maria, 99999));
    }

    @Test @DisplayName("[US06, RN06] Carrinho calcula subtotal por item")
    void subtotal() throws Exception {
        dao.adicionar(maria, idCupcake(BRIGADEIRO), 2);
        dao.adicionar(maria, idCupcake(VEGANO), 1);
        List<ItemCarrinho> itens = dao.listar(maria);
        BigDecimal total = itens.stream().map(ItemCarrinho::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(new BigDecimal("19.00"), itens.stream().filter(i -> i.nome().equals(BRIGADEIRO)).findFirst().get().subtotal());
        assertEquals(new BigDecimal("30.00"), total);
    }

    @Test @DisplayName("[US06] Carrinho de um cliente não aparece para outro")
    void isolamento() throws Exception {
        dao.adicionar(maria, idCupcake(BRIGADEIRO), 1);
        assertEquals(1, dao.listar(maria).size());
        assertTrue(dao.listar(joao).isEmpty());
    }

    @Test @DisplayName("[US06] Carrinho vazio retorna lista vazia")
    void vazio() throws Exception {
        assertTrue(dao.listar(maria).isEmpty());
    }
}
