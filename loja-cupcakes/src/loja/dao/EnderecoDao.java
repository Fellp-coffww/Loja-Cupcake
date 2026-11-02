package loja.dao;

import loja.model.Endereco;
import loja.util.Db;
import java.sql.*;
import java.util.*;

public class EnderecoDao {

    public List<Endereco> listar(long clienteId) throws SQLException {
        String sql = "SELECT id, apelido, cep, logradouro, numero, complemento, bairro, cidade, uf " +
                     "FROM endereco WHERE cliente_id = ? AND ativo ORDER BY principal DESC, id";
        List<Endereco> r = new ArrayList<>();
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.add(new Endereco(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9)));
            }
        }
        return r;
    }

    public long inserir(long clienteId, String apelido, String cep, String logradouro, String numero,
                        String complemento, String bairro, String cidade, String uf) throws SQLException {
        String sql = "INSERT INTO endereco (cliente_id, apelido, cep, logradouro, numero, complemento, bairro, cidade, uf) " +
                     "VALUES (?,?,?,?,?,?,?,?,?) RETURNING id";
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId); ps.setString(2, apelido); ps.setString(3, cep); ps.setString(4, logradouro);
            ps.setString(5, numero); ps.setString(6, complemento); ps.setString(7, bairro);
            ps.setString(8, cidade); ps.setString(9, uf);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }
}
