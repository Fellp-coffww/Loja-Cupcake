package loja.dao;

import loja.model.Usuario;
import loja.util.Db;
import java.sql.*;

public class UsuarioDao {

    public Usuario autenticar(String email, String senhaHash) throws SQLException {
        String sql = "SELECT id, nome, email, perfil FROM usuario WHERE email = ? AND senha_hash = ? AND ativo";
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            ps.setString(2, senhaHash);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Usuario(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4)) : null;
            }
        }
    }

    /** Cria usuário + cliente na mesma transação. Retorna o id. */
    public long cadastrarCliente(String nome, String email, String senhaHash, String telefone) throws SQLException {
        try (Connection c = Db.conectar()) {
            c.setAutoCommit(false);
            try {
                long id;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO usuario (nome, email, senha_hash, perfil) VALUES (?,?,?,'CLIENTE') RETURNING id")) {
                    ps.setString(1, nome); ps.setString(2, email); ps.setString(3, senhaHash);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); id = rs.getLong(1); }
                }
                try (PreparedStatement ps = c.prepareStatement("INSERT INTO cliente (usuario_id, telefone) VALUES (?,?)")) {
                    ps.setLong(1, id);
                    ps.setString(2, telefone == null || telefone.isBlank() ? null : telefone);
                    ps.executeUpdate();
                }
                c.commit();
                return id;
            } catch (SQLException e) {
                c.rollback();
                throw e;
            }
        }
    }
}
