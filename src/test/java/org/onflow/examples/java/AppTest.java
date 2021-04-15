package org.onflow.examples.java;

import org.junit.jupiter.api.Test;
import org.onflow.sdk.FlowAddress;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    public static final String SERVICE_PRIVATE_KEY_HEX = "a2f983853e61b3e27d94b7bf3d7094dd756aead2a813dd5cf738e1da56fa9c17";
    public static final String USER_PUBLIC_KEY_HEX = "09c883f8c036bcf2607607379fd332374bdd2420990810d89b7af4fdf019e3e0423b07a0103388e62ac4775586f268ce3cb90acd688a6ccaab6d4035303e6acf";

    private FlowAddress serviceAccountAddress = new FlowAddress("f8d6e0586b0a20c7");

    @Test
    void createAccount() throws Exception {

        App app = new App("localhost", 3569, SERVICE_PRIVATE_KEY_HEX);

        // service account address
        var payer = serviceAccountAddress;

        FlowAddress account = app.createAccount(payer, USER_PUBLIC_KEY_HEX);
        assertTrue(account != null);
    }

    @Test
    void transferTokens() throws Exception {

        App app = new App("localhost", 3569, SERVICE_PRIVATE_KEY_HEX);

        // service account address
        var sender = serviceAccountAddress;
        var recipient = app.createAccount(sender, USER_PUBLIC_KEY_HEX);

        // FLOW amounts always have 8 decimal places
        var amount = new BigDecimal("10.00000001");

        var balance1 = app.getAccountBalance(recipient);

        app.transferTokens(sender, recipient, amount);

        var balance2 = app.getAccountBalance(recipient);

        assertEquals(balance1.add(amount), balance2);
    }

    @Test
    void getAccountBalance() throws Exception {

        App app = new App("localhost", 3569, SERVICE_PRIVATE_KEY_HEX);
        var balance = app.getAccountBalance(serviceAccountAddress);

        System.out.println(balance);
    }
}
