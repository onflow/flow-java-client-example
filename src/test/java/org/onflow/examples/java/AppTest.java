package org.onflow.examples.java;

import org.junit.jupiter.api.Test;
import org.onflow.sdk.FlowAddress;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    public static final String SERVICE_PRIVATE_KEY_HEX = "b2bee95fcf0afab690c2f61d8787de0dc9cb2a64eee797d11f6ae8bc29d5f92b";
    public static final String USER_PRIVATE_KEY_HEX = "16285d55d0f6eb0de3978afd4f406f3796cc750dcd6e5877ba0da9837acf8dc8c30cc098334b8e0a5b5ee0ff6c5e5813178b88a9035f97417e869e9fd58dd7eb";
    public static final String USER_PUBLIC_KEY_HEX = "199e54b7031b6d96f881b94ef3582deca90f7a2c7d180899193a22712be264bdbce5de5eac3e8afaedd75c48f620395f093af75eee20b617a7bdbae08e4fcb41";

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
