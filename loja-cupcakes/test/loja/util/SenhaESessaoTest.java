package loja.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SenhaESessaoTest {

    @Test @DisplayName("[UTIL] Hash SHA-256 de '123456' confere com o valor conhecido")
    void hashConhecido() {
        assertEquals("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92", Senha.hash("123456"));
    }

    @Test @DisplayName("[UTIL] Hash é determinístico, tem 64 caracteres e muda com a senha")
    void hashPropriedades() {
        assertEquals(Senha.hash("abc"), Senha.hash("abc"));
        assertNotEquals(Senha.hash("abc"), Senha.hash("abd"));
        assertEquals(64, Senha.hash("qualquer").length());
    }

    @Test @DisplayName("[UTIL] Sessão: cria, busca e encerra")
    void sessao() {
        String token = Sessao.criar(new Sessao.Dados(7, "Ana", "CLIENTE"));
        assertEquals("Ana", Sessao.buscar(token).nome());
        assertEquals(7, Sessao.buscar(token).usuarioId());
        Sessao.encerrar(token);
        assertNull(Sessao.buscar(token));
    }

    @Test @DisplayName("[UTIL] Sessão: token nulo ou desconhecido não autentica")
    void sessaoInvalida() {
        assertNull(Sessao.buscar(null));
        assertNull(Sessao.buscar("token-que-nao-existe"));
        Sessao.encerrar(null);   // não deve lançar exceção
    }

    @Test @DisplayName("[UTIL] Sessão: tokens diferentes para logins diferentes")
    void tokensUnicos() {
        String a = Sessao.criar(new Sessao.Dados(1, "A", "CLIENTE"));
        String b = Sessao.criar(new Sessao.Dados(1, "A", "CLIENTE"));
        assertNotEquals(a, b);
    }
}
