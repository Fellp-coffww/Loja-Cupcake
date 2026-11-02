package loja.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JsonTest {

    @Test @DisplayName("[UTIL] Serializa mapa com texto, número e booleano")
    void serializaMapa() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", "Ana"); m.put("idade", 30); m.put("ativo", true);
        assertEquals("{\"nome\":\"Ana\",\"idade\":30,\"ativo\":true}", Json.toJson(m));
    }

    @Test @DisplayName("[UTIL] Escapa aspas, barra invertida e quebra de linha")
    void escapaCaracteres() {
        assertEquals("\"a\\\"b\\\\c\\nd\"", Json.toJson("a\"b\\c\nd"));
    }

    @Test @DisplayName("[UTIL] Serializa nulo e lista de mapas")
    void serializaNuloELista() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("x", null);
        assertEquals("[{\"x\":null},{\"x\":null}]", Json.toJson(List.of(m, m)));
    }

    @Test @DisplayName("[UTIL] Lê objeto simples com tipos misturados")
    void leObjeto() {
        Map<String, Object> m = Json.lerObjeto("{\"nome\":\"Ana\",\"idade\":30,\"ativo\":true,\"x\":null,\"p\":12.5}");
        assertEquals("Ana", m.get("nome"));
        assertEquals(30L, Json.longo(m, "idade"));
        assertTrue(Json.bool(m, "ativo", false));
        assertNull(m.get("x"));
        assertEquals(new java.math.BigDecimal("12.5"), Json.decimal(m, "p"));
    }

    @Test @DisplayName("[UTIL] Lê acentos e sequência \\u")
    void leAcentos() {
        Map<String, Object> m = Json.lerObjeto("{\"a\":\"Goiânia\",\"b\":\"caf\\u00e9\"}");
        assertEquals("Goiânia", m.get("a"));
        assertEquals("café", m.get("b"));
    }

    @Test @DisplayName("[UTIL] Corpo vazio vira objeto vazio")
    void corpoVazio() {
        assertTrue(Json.lerObjeto("").isEmpty());
        assertTrue(Json.lerObjeto(null).isEmpty());
        assertTrue(Json.lerObjeto("{}").isEmpty());
    }

    @Test @DisplayName("[UTIL] JSON malformado gera IllegalArgumentException")
    void jsonInvalido() {
        for (String ruim : new String[] {"[1,2]", "{abc}", "{\"a\":", "{\"a\" 1}", "{\"a\":1", "{\"a\":\"x"}) {
            assertThrows(IllegalArgumentException.class, () -> Json.lerObjeto(ruim), "entrada: " + ruim);
        }
    }

    @Test @DisplayName("[UTIL] Campo numérico obrigatório ausente gera IllegalArgumentException")
    void campoObrigatorio() {
        assertThrows(IllegalArgumentException.class, () -> Json.longo(Map.of(), "x"));
        assertThrows(IllegalArgumentException.class, () -> Json.decimal(Map.of(), "x"));
    }

    @Test @DisplayName("[UTIL] Número decimal em campo inteiro gera IllegalArgumentException")
    void decimalEmInteiro() {
        assertThrows(IllegalArgumentException.class, () -> Json.longo(Map.of("q", new java.math.BigDecimal("1.5")), "q"));
    }

    @Test @DisplayName("[UTIL] Converte lista de ids separada por vírgula")
    void listaIds() {
        assertEquals(List.of(1L, 2L, 3L), Json.listaIds("1, 2,3"));
        assertTrue(Json.listaIds("").isEmpty());
        assertTrue(Json.listaIds(null).isEmpty());
    }
}
