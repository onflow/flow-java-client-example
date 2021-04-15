
package org.onflow.examples.java;

import org.onflow.examples.java.App;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class AppTest {

    public static final String userPrivateKeyHex = "c5e1ebe04e10d687a4468502992965f155c7197fa15eb5b39ce15b4ae7c0a626";
    public static final String userPublicKeyHex = "199e54b7031b6d96f881b94ef3582deca90f7a2c7d180899193a22712be264bdbce5de5eac3e8afaedd75c48f620395f093af75eee20b617a7bdbae08e4fcb41";

    /*
    @Test
    void createAccount() throws Exception {
        InitCrypto();

        App app = new App("localhost", 3569, servicePrivateKeyHex);

        // service account address
        var payer = serviceAccountAddress;

        app.createAccount(payer, userPublicKeyHex);
    }

    @Test
    void transferTokens() throws Exception {
        InitCrypto();

        App app = new App("localhost", 3569, servicePrivateKeyHex);

        // service account address
        var sender = serviceAccountAddress;
        var recipient = app.createAccount(sender, userPublicKeyHex);

        // FLOW amounts always have 8 decimal places
        var amount = new BigDecimal("10.00000001");

        var balance1 = app.getAccountBalance(recipient);

        app.transferTokens(sender, recipient, amount);

        var balance2 = app.getAccountBalance(recipient);

        assertEquals(balance1.add(amount), balance2);
    }

    @Test
    void getAccountBalance() throws Exception {
        InitCrypto();

        App app = new App("localhost", 3569, servicePrivateKeyHex);
        var balance = app.getAccountBalance(serviceAccountAddress);

        System.out.println(balance);
    } */
}