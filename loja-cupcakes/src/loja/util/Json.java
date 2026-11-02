package loja.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Conversor JSON mínimo (sem bibliotecas externas). */
public final class Json {
    private Json() {}

    // ---------- objeto -> texto ----------
    public static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        escrever(sb, o);
        return sb.toString();
    }

    private static void escrever(StringBuilder sb, Object o) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map<?, ?> m) {
            sb.append('{');
            boolean primeiro = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                texto(sb, String.valueOf(e.getKey()));
                sb.append(':');
                escrever(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Iterable<?> it) {
            sb.append('[');
            boolean primeiro = true;
            for (Object x : it) {
                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(sb, x);
            }
            sb.append(']');
        } else {
            texto(sb, o.toString());
        }
    }

    private static void texto(StringBuilder sb, String s) {
        sb.append('"');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        sb.append('"');
    }

    // ---------- texto -> Map (apenas objeto simples, valores escalares) ----------
    public static Map<String, Object> lerObjeto(String json) {
        try {
            return lerObjetoInterno(json);
        } catch (IndexOutOfBoundsException e) {          // texto cortado no meio (ex.: {"a":)
            throw new IllegalArgumentException("JSON inválido");
        }
    }

    private static Map<String, Object> lerObjetoInterno(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        int[] p = {0};
        pular(json, p);
        if (json.charAt(p[0]) != '{') throw new IllegalArgumentException("JSON inválido");
        p[0]++;
        Map<String, Object> mapa = new LinkedHashMap<>();
        pular(json, p);
        if (json.charAt(p[0]) == '}') return mapa;
        while (true) {
            pular(json, p);
            String chave = lerTexto(json, p);
            pular(json, p);
            if (json.charAt(p[0]++) != ':') throw new IllegalArgumentException("JSON inválido");
            pular(json, p);
            mapa.put(chave, lerValor(json, p));
            pular(json, p);
            char c = json.charAt(p[0]++);
            if (c == '}') return mapa;
            if (c != ',') throw new IllegalArgumentException("JSON inválido");
        }
    }

    private static Object lerValor(String s, int[] p) {
        char c = s.charAt(p[0]);
        if (c == '"') return lerTexto(s, p);
        if (s.startsWith("true", p[0])) { p[0] += 4; return Boolean.TRUE; }
        if (s.startsWith("false", p[0])) { p[0] += 5; return Boolean.FALSE; }
        if (s.startsWith("null", p[0])) { p[0] += 4; return null; }
        int ini = p[0];
        while (p[0] < s.length() && "+-.0123456789eE".indexOf(s.charAt(p[0])) >= 0) p[0]++;
        if (ini == p[0]) throw new IllegalArgumentException("JSON inválido");
        return new java.math.BigDecimal(s.substring(ini, p[0]));
    }

    private static String lerTexto(String s, int[] p) {
        if (s.charAt(p[0]++) != '"') throw new IllegalArgumentException("JSON inválido");
        StringBuilder sb = new StringBuilder();
        while (true) {
            char c = s.charAt(p[0]++);
            if (c == '"') return sb.toString();
            if (c == '\\') {
                char e = s.charAt(p[0]++);
                switch (e) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case 'u' -> { sb.append((char) Integer.parseInt(s.substring(p[0], p[0] + 4), 16)); p[0] += 4; }
                    default -> sb.append(e);
                }
            } else {
                sb.append(c);
            }
        }
    }

    private static void pular(String s, int[] p) {
        while (p[0] < s.length() && Character.isWhitespace(s.charAt(p[0]))) p[0]++;
    }

    // ---------- atalhos para ler campos ----------
    public static String str(Map<String, Object> m, String k) {
        Object v = m.get(k);
        return v == null ? null : v.toString().trim();
    }

    public static long longo(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) throw new IllegalArgumentException("Campo obrigatório: " + k);
        try {
            return new java.math.BigDecimal(v.toString()).longValueExact();
        } catch (ArithmeticException e) {                // ex.: 1.5 em campo inteiro
            throw new IllegalArgumentException("O campo " + k + " deve ser um número inteiro");
        }
    }

    public static java.math.BigDecimal decimal(Map<String, Object> m, String k) {
        Object v = m.get(k);
        if (v == null) throw new IllegalArgumentException("Campo obrigatório: " + k);
        return new java.math.BigDecimal(v.toString());
    }

    public static boolean bool(Map<String, Object> m, String k, boolean padrao) {
        Object v = m.get(k);
        return v == null ? padrao : Boolean.parseBoolean(v.toString());
    }

    /** "1,2,3" -> [1,2,3] */
    public static List<Long> listaIds(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) return ids;
        for (String parte : csv.split(",")) ids.add(Long.parseLong(parte.trim()));
        return ids;
    }
}
