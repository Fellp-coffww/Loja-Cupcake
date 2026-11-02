package loja;

import loja.util.Db;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;

/**
 * Prepara o banco de TESTES: recria o esquema "loja" com os dados iniciais antes de cada teste.
 *
 * ATENÇÃO: o script apaga e recria o esquema. Por segurança, só roda em banco cujo nome contém "test".
 * Configuração (variáveis de ambiente): TEST_DB_URL, TEST_DB_USER, TEST_DB_PASSWORD.
 */
public final class BancoDeTestes {
    private static final String URL_PADRAO = "jdbc:postgresql://localhost:5432/loja_cupcakes_test?currentSchema=loja";

    private BancoDeTestes() {}

    public static void preparar() throws Exception {
        String url = env("TEST_DB_URL", URL_PADRAO);
        if (!url.toLowerCase().contains("test")) {
            throw new IllegalStateException("Por segurança, os testes só rodam em um banco cujo nome contém 'test': " + url);
        }
        System.setProperty("db.url", url);
        System.setProperty("db.user", env("TEST_DB_USER", "postgres"));
        System.setProperty("db.password", env("TEST_DB_PASSWORD", "postgres"));

        String script = Files.readString(Path.of("sql", "schema_loja_cupcakes.sql"));
        try (Connection c = Db.conectar(); Statement st = c.createStatement()) {
            st.execute(script);
        }
    }

    private static String env(String nome, String padrao) {
        String v = System.getenv(nome);
        return (v == null || v.isBlank()) ? padrao : v;
    }

    // ---------- atalhos de consulta usados pelos testes ----------

    public static Object valor(String sql, Object... args) throws SQLException {
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getObject(1) : null; }
        }
    }

    public static long contar(String sql, Object... args) throws SQLException {
        Object v = valor(sql, args);
        return v == null ? 0 : ((Number) v).longValue();
    }

    public static void executar(String sql, Object... args) throws SQLException {
        try (Connection c = Db.conectar(); PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            ps.executeUpdate();
        }
    }

    public static long idUsuario(String email) throws SQLException {
        return contar("SELECT id FROM usuario WHERE email = ?", email);
    }

    public static long idCupcake(String nome) throws SQLException {
        return contar("SELECT id FROM cupcake WHERE nome = ?", nome);
    }

    // Constantes dos dados iniciais
    public static final String MARIA = "maria@email.com";
    public static final String JOAO = "joao@email.com";
    public static final String ADMIN = "admin@cupcakes.com";
    public static final String BRIGADEIRO = "Brigadeiro Gourmet";   // R$ 9,50 - estoque 30 - destaque
    public static final String VEGANO = "Vegano de Cacau";           // R$ 11,00 - estoque 20 - destaque
    public static final String MORANGO = "Morango Cremoso";          // R$ 10,00 - estoque 15
    public static final String BAUNILHA = "Baunilha Clássico";       // R$ 8,00 - estoque 40
    public static final String LIMAO = "Limão sem Lactose";          // R$ 9,00 - estoque 4
}
