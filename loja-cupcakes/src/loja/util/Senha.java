package loja.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Hash de senha. SHA-256 é suficiente para o trabalho; em produção use bcrypt/argon2. */
public final class Senha {
    private Senha() {}

    public static String hash(String senha) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(senha.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(h);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
