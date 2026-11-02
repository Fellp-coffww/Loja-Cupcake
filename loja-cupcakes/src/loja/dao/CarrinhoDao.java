package loja.dao;

import loja.model.ItemCarrinho;
import loja.util.Db;
import java.sql.*;
import java.util.*;

public class CarrinhoDao {

    /** Garante que o cliente tenha um carrinho ABERTO e devolve o id. */
    private long carrinhoAberto(Connection c, long clienteId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO carrinho (cliente_id) VALUES (?) ON CONFLICT (cliente_id) WHERE status = 'ABERTO' DO NOTHING")) {
            ps.setLong(1, clienteId);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement("SELECT id FROM carrinho WHERE cliente_id = ? AND status = 'ABERTO'")) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    public void adicionar(long clienteId, long cupcakeId, int quantidade) throws SQLException {
        try (Connection c = Db.conectar()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT 1 FROM cupcake WHERE id = ? AND ativo")) {
                    ps.setLong(1, cupcakeId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Produto indisponível");
                    }
                }
                long carrinhoId = carrinhoAberto(c, clienteId);
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO item_carrinho (carrinho_id, cupcake_id, quantidade) VALUES (?,?,?) " +
                        "ON CONFLICT (carrinho_id, cupcake_id) DO UPDATE SET quantidade = item_carrinho.quantidade + EXCLUDED.quantidade")) {
                    ps.setLong(1, carrinhoId); ps.setLong(2, cupcakeId); ps.setInt(3, quantidade);
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public void remover(long clienteId, long cupcakeId) throws SQLException {
        String sql = "DELETE FROM item_carrinho WHERE cupcake_id = ? AND carrinho_id = " +
                     "(SELECT id FROM carrinho WHERE cliente_id = ? AND status = 'ABERTO')";
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, cupcakeId); ps.setLong(2, clienteId);
            ps.executeUpdate();
        }
    }

    public List<ItemCarrinho> listar(long clienteId) throws SQLException {
        String sql = "SELECT cupcake_id, nome, quantidade, preco, subtotal FROM vw_carrinho_detalhado " +
                     "WHERE cliente_id = ? ORDER BY nome";
        List<ItemCarrinho> r = new ArrayList<>();
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.add(new ItemCarrinho(rs.getLong(1), rs.getString(2), rs.getInt(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5)));
            }
        }
        return r;
    }
}
