package loja.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Conexão com o PostgreSQL.
 * Ordem de configuração: propriedade do Java (-Ddb.url) > variável de ambiente (DB_URL) > padrão.
 * As propriedades são usadas pelos testes automatizados para apontar para o banco de testes.
 */
public final class Db {
    private Db() {}

    public static Connection conectar() throws SQLException {
        return DriverManager.getConnection(
                config("db.url", "DB_URL", "jdbc:postgresql://localhost:5432/loja_cupcakes?currentSchema=loja"),
                config("db.user", "DB_USER", "postgres"),
                config("db.password", "DB_PASSWORD", "postgres"));
    }

    private static String config(String propriedade, String ambiente, String padrao) {
        String v = System.getProperty(propriedade);
        if (v == null || v.isBlank()) v = System.getenv(ambiente);
        return (v == null || v.isBlank()) ? padrao : v;
    }
}
