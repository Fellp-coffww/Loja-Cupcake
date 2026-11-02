package loja.dao;

import loja.model.Cupcake;
import loja.util.Db;
import java.sql.*;
import java.util.*;

public class CupcakeDao {

    private static final String SELECT =
        "SELECT c.id, c.nome, c.descricao, c.ingredientes, c.preco, c.imagem_url, c.estoque, c.destaque, c.ativo, " +
        "COALESCE((SELECT string_agg(k.nome, ', ' ORDER BY k.nome) FROM cupcake_categoria cc " +
        "          JOIN categoria k ON k.id = cc.categoria_id WHERE cc.cupcake_id = c.id), '') AS categorias " +
        "FROM cupcake c ";

    private Cupcake mapear(ResultSet rs) throws SQLException {
        return new Cupcake(rs.getLong("id"), rs.getString("nome"), rs.getString("descricao"),
                rs.getString("ingredientes"), rs.getBigDecimal("preco"), rs.getString("imagem_url"),
                rs.getInt("estoque"), rs.getBoolean("destaque"), rs.getBoolean("ativo"), rs.getString("categorias"));
    }

    /** Vitrine (RN01: só ativos; ordena por destaque). categoriaId opcional. */
    public List<Cupcake> listarAtivos(Long categoriaId) throws SQLException {
        String sql = SELECT + "WHERE c.ativo " +
            (categoriaId != null ? "AND EXISTS (SELECT 1 FROM cupcake_categoria x WHERE x.cupcake_id = c.id AND x.categoria_id = ?) " : "") +
            "ORDER BY c.destaque DESC, c.nome";
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (categoriaId != null) ps.setLong(1, categoriaId);
            return lista(ps);
        }
    }

    public List<Cupcake> listarTodos() throws SQLException {
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(SELECT + "ORDER BY c.nome")) {
            return lista(ps);
        }
    }

    public Cupcake buscarPorId(long id) throws SQLException {
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(SELECT + "WHERE c.id = ?")) {
            ps.setLong(1, id);
            List<Cupcake> r = lista(ps);
            return r.isEmpty() ? null : r.get(0);
        }
    }

    public List<Map<String, Object>> listarCategorias() throws SQLException {
        List<Map<String, Object>> r = new ArrayList<>();
        try (Connection c = Db.conectar(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, nome FROM categoria WHERE ativa ORDER BY nome")) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", rs.getLong(1)); m.put("nome", rs.getString(2));
                r.add(m);
            }
        }
        return r;
    }

    /** Cria (id == null) ou atualiza um cupcake e suas categorias. */
    public long salvar(Long id, String nome, String descricao, String ingredientes, java.math.BigDecimal preco,
                       String imagemUrl, int estoque, boolean destaque, boolean ativo, List<Long> categorias)
            throws SQLException {
        try (Connection c = Db.conectar()) {
            c.setAutoCommit(false);
            try {
                long cid;
                if (id == null) {
                    String sql = "INSERT INTO cupcake (nome, descricao, ingredientes, preco, imagem_url, estoque, destaque, ativo) " +
                                 "VALUES (?,?,?,?,?,?,?,?) RETURNING id";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        preencher(ps, nome, descricao, ingredientes, preco, imagemUrl, estoque, destaque, ativo);
                        try (ResultSet rs = ps.executeQuery()) { rs.next(); cid = rs.getLong(1); }
                    }
                } else {
                    String sql = "UPDATE cupcake SET nome=?, descricao=?, ingredientes=?, preco=?, imagem_url=?, estoque=?, " +
                                 "destaque=?, ativo=? WHERE id=?";
                    try (PreparedStatement ps = c.prepareStatement(sql)) {
                        preencher(ps, nome, descricao, ingredientes, preco, imagemUrl, estoque, destaque, ativo);
                        ps.setLong(9, id);
                        if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Produto não encontrado");  // RN13
                    }
                    cid = id;
                }
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM cupcake_categoria WHERE cupcake_id = ?")) {
                    ps.setLong(1, cid); ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO cupcake_categoria (cupcake_id, categoria_id) VALUES (?,?)")) {
                    for (Long cat : categorias) { ps.setLong(1, cid); ps.setLong(2, cat); ps.addBatch(); }
                    ps.executeBatch();
                }
                c.commit();
                return cid;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    private void preencher(PreparedStatement ps, String nome, String descricao, String ingredientes,
                           java.math.BigDecimal preco, String imagemUrl, int estoque, boolean destaque, boolean ativo)
            throws SQLException {
        ps.setString(1, nome); ps.setString(2, descricao); ps.setString(3, ingredientes);
        ps.setBigDecimal(4, preco); ps.setString(5, imagemUrl); ps.setInt(6, estoque);
        ps.setBoolean(7, destaque); ps.setBoolean(8, ativo);
    }

    private List<Cupcake> lista(PreparedStatement ps) throws SQLException {
        List<Cupcake> r = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) { while (rs.next()) r.add(mapear(rs)); }
        return r;
    }
}
