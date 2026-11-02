package loja.dao;

import loja.model.Pedido;
import loja.util.Db;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public class PedidoDao {

    /** CU09: converte o carrinho aberto em pedido (função transacional do banco). */
    public long confirmar(long clienteId, long enderecoId, BigDecimal taxaEntrega) throws SQLException {
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement("SELECT fn_confirmar_pedido(?,?,?)")) {
            ps.setLong(1, clienteId); ps.setLong(2, enderecoId); ps.setBigDecimal(3, taxaEntrega);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    public List<Pedido> listarDoCliente(long clienteId) throws SQLException {
        return consultar("WHERE cliente_id = ?", clienteId);
    }

    public List<Pedido> listarTodos() throws SQLException {
        return consultar("", null);
    }

    private List<Pedido> consultar(String where, Long clienteId) throws SQLException {
        String sql = "SELECT id, cliente, status, to_char(data_pedido, 'DD/MM/YYYY HH24:MI'), valor_total, status_pagamento " +
                     "FROM vw_pedidos_resumo " + where + " ORDER BY id DESC";
        List<Pedido> r = new ArrayList<>();
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            if (clienteId != null) ps.setLong(1, clienteId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) r.add(new Pedido(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getBigDecimal(5), rs.getString(6)));
            }
        }
        return r;
    }

    /**
     * CU10: registra a tentativa de pagamento e consulta o gateway (simulado).
     * Se aprovado, o trigger do banco muda o pedido para PAGO.
     */
    public String pagar(long clienteId, long pedidoId, String metodo, boolean simularRecusa) throws SQLException {
        try (Connection c = Db.conectar()) {
            c.setAutoCommit(false);
            try {
                BigDecimal valor;
                try (PreparedStatement ps = c.prepareStatement(
                        "SELECT valor_total FROM pedido WHERE id = ? AND cliente_id = ? AND status = 'AGUARDANDO_PAGAMENTO' FOR UPDATE")) {
                    ps.setLong(1, pedidoId); ps.setLong(2, clienteId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Pedido não encontrado ou já pago");
                        valor = rs.getBigDecimal(1);
                    }
                }
                long pagamentoId;
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO pagamento (pedido_id, metodo, valor) VALUES (?, ?::metodo_pagamento, ?) RETURNING id")) {
                    ps.setLong(1, pedidoId); ps.setString(2, metodo); ps.setBigDecimal(3, valor);
                    try (ResultSet rs = ps.executeQuery()) { rs.next(); pagamentoId = rs.getLong(1); }
                }
                boolean aprovado = GatewayFake.processar(metodo, valor, simularRecusa);
                String status = aprovado ? "APROVADO" : "RECUSADO";
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE pagamento SET status = ?::status_pagamento, processado_em = now(), gateway_transacao_id = ? WHERE id = ?")) {
                    ps.setString(1, status);
                    ps.setString(2, "SIM-" + UUID.randomUUID());
                    ps.setLong(3, pagamentoId);
                    ps.executeUpdate();
                }
                c.commit();
                return status;
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** CU14: o dono altera o status. A validação do fluxo (RN14) é feita por trigger no banco. */
    public void atualizarStatus(long adminId, long pedidoId, String novoStatus) throws SQLException {
        try (Connection c = Db.conectar()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement ps = c.prepareStatement("SELECT set_config('loja.usuario_id', ?, true)")) {
                    ps.setString(1, String.valueOf(adminId));
                    ps.execute();
                }
                try (PreparedStatement ps = c.prepareStatement("UPDATE pedido SET status = ?::status_pedido WHERE id = ?")) {
                    ps.setString(1, novoStatus); ps.setLong(2, pedidoId);
                    if (ps.executeUpdate() == 0) throw new IllegalArgumentException("Pedido não encontrado");
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /** CU15: dados do dashboard a partir das views do banco. */
    public Map<String, Object> dashboard() throws SQLException {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("vendasDiarias", tabela("SELECT dia::text, qtd_pedidos, faturamento, ticket_medio FROM vw_vendas_diarias LIMIT 30",
                "dia", "pedidos", "faturamento", "ticketMedio"));
        r.put("maisVendidos", tabela("SELECT nome, unidades_vendidas, receita FROM vw_produtos_mais_vendidos LIMIT 10",
                "nome", "unidades", "receita"));
        r.put("estoque", tabela("SELECT nome, estoque, situacao FROM vw_estoque WHERE ativo", "nome", "estoque", "situacao"));
        return r;
    }

    private List<Map<String, Object>> tabela(String sql, String... nomes) throws SQLException {
        List<Map<String, Object>> linhas = new ArrayList<>();
        try (Connection c = Db.conectar(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 0; i < nomes.length; i++) m.put(nomes[i], rs.getObject(i + 1));
                linhas.add(m);
            }
        }
        return linhas;
    }
}
