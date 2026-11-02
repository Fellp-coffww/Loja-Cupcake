package loja.dao;

import java.math.BigDecimal;

/** Gateway de pagamento SIMULADO (no lugar de Stripe/Cielo). Aprova sempre, salvo se pedido o contrário. */
final class GatewayFake {
    private GatewayFake() {}

    static boolean processar(String metodo, BigDecimal valor, boolean simularRecusa) {
        return !simularRecusa;
    }
}
