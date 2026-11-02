package loja.dao;

import static loja.BancoDeTestes.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.util.List;
import loja.BancoDeTestes;
import loja.model.Endereco;
import loja.model.Usuario;
import loja.util.Senha;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UsuarioEEnderecoDaoTest {
    private final UsuarioDao usuarios = new UsuarioDao();
    private final EnderecoDao enderecos = new EnderecoDao();

    @BeforeEach void banco() throws Exception { BancoDeTestes.preparar(); }

    // ---------------- Usuário ----------------
    @Test @DisplayName("[ACESSO] Login de cliente com senha correta")
    void loginCliente() throws Exception {
        Usuario u = usuarios.autenticar(MARIA, Senha.hash("123456"));
        assertNotNull(u);
        assertEquals("CLIENTE", u.perfil());
        assertEquals("Maria Silva", u.nome());
    }

    @Test @DisplayName("[ACESSO] Login de administrador devolve o perfil ADMINISTRADOR")
    void loginAdmin() throws Exception {
        assertEquals("ADMINISTRADOR", usuarios.autenticar(ADMIN, Senha.hash("123456")).perfil());
    }

    @Test @DisplayName("[ACESSO] Senha errada ou e-mail desconhecido não autentica")
    void loginInvalido() throws Exception {
        assertNull(usuarios.autenticar(MARIA, Senha.hash("errada")));
        assertNull(usuarios.autenticar("ninguem@email.com", Senha.hash("123456")));
    }

    @Test @DisplayName("[ACESSO] Usuário inativo não consegue entrar")
    void loginInativo() throws Exception {
        executar("UPDATE usuario SET ativo = FALSE WHERE email = ?", JOAO);
        assertNull(usuarios.autenticar(JOAO, Senha.hash("123456")));
    }

    @Test @DisplayName("[ACESSO] Cadastro cria usuário e cliente e permite login")
    void cadastro() throws Exception {
        long id = usuarios.cadastrarCliente("Ana Lima", "ana@email.com", Senha.hash("segredo1"), "62999998888");
        assertEquals(1, contar("SELECT count(*) FROM cliente WHERE usuario_id = ?", id));
        assertEquals("CLIENTE", usuarios.autenticar("ana@email.com", Senha.hash("segredo1")).perfil());
    }

    @Test @DisplayName("[ACESSO] E-mail repetido é rejeitado e não deixa registro órfão")
    void emailDuplicado() throws Exception {
        long antes = contar("SELECT count(*) FROM usuario");
        SQLException e = assertThrows(SQLException.class, () ->
            usuarios.cadastrarCliente("Outra Maria", MARIA, Senha.hash("x12345"), null));
        assertEquals("23505", e.getSQLState());
        assertEquals(antes, contar("SELECT count(*) FROM usuario"));
    }

    @Test @DisplayName("[ACESSO] E-mail com formato inválido é rejeitado pelo banco")
    void emailInvalido() {
        SQLException e = assertThrows(SQLException.class, () ->
            usuarios.cadastrarCliente("Fulano", "isto-nao-e-email", Senha.hash("x12345"), null));
        assertEquals("23514", e.getSQLState());
    }

    @Test @DisplayName("[ACESSO] Senha fica gravada apenas como hash")
    void senhaComoHash() throws Exception {
        usuarios.cadastrarCliente("Ana Lima", "ana@email.com", Senha.hash("segredo1"), null);
        String gravado = (String) valor("SELECT senha_hash FROM usuario WHERE email = 'ana@email.com'");
        assertNotEquals("segredo1", gravado);
        assertEquals(64, gravado.length());
    }

    // ---------------- Endereço ----------------
    @Test @DisplayName("[US07] Lista os endereços do cliente")
    void listarEnderecos() throws Exception {
        List<Endereco> lista = enderecos.listar(idUsuario(MARIA));
        assertEquals(1, lista.size());
        assertEquals("74000000", lista.get(0).cep());
        assertTrue(enderecos.listar(idUsuario(JOAO)).isEmpty());
    }

    @Test @DisplayName("[US07] Salva novo endereço válido")
    void inserirEndereco() throws Exception {
        long id = enderecos.inserir(idUsuario(JOAO), "Casa", "74110010", "Av. Goiás", "500", null, "Setor Central", "Goiânia", "GO");
        assertTrue(id > 0);
        assertEquals("Av. Goiás", enderecos.listar(idUsuario(JOAO)).get(0).logradouro());
    }

    @Test @DisplayName("[US07, RN07] CEP inválido e UF em minúsculas são rejeitados pelo banco")
    void enderecoInvalido() throws Exception {
        long joao = idUsuario(JOAO);
        assertThrows(SQLException.class, () -> enderecos.inserir(joao, null, "123", "Rua", "1", null, "Bairro", "Cidade", "GO"));
        assertThrows(SQLException.class, () -> enderecos.inserir(joao, null, "74110010", "Rua", "1", null, "Bairro", "Cidade", "go"));
        assertTrue(enderecos.listar(joao).isEmpty());
    }

    @Test @DisplayName("[US07, RN07] Campos obrigatórios do endereço não podem faltar")
    void enderecoObrigatorios() throws Exception {
        long joao = idUsuario(JOAO);
        SQLException e = assertThrows(SQLException.class, () ->
            enderecos.inserir(joao, null, "74110010", null, "1", null, "Bairro", "Cidade", "GO"));
        assertEquals("23502", e.getSQLState());
    }

    @Test @DisplayName("[US07] Endereço inativo não é listado")
    void enderecoInativo() throws Exception {
        executar("UPDATE endereco SET ativo = FALSE WHERE cliente_id = ?", idUsuario(MARIA));
        assertTrue(enderecos.listar(idUsuario(MARIA)).isEmpty());
    }
}
