package org.onflow.examples.java;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;


import org.onflow.sdk.AddressField;
import org.onflow.sdk.Crypto;
import org.onflow.sdk.FlowAccessApi;
import org.onflow.sdk.Flow;
import org.onflow.sdk.FlowAccount;
import org.onflow.sdk.FlowAccountKey;
import org.onflow.sdk.FlowAddress;
import org.onflow.sdk.FlowArgument;
import org.onflow.sdk.FlowId;
import org.onflow.sdk.FlowPublicKey;
import org.onflow.sdk.FlowScript;
import org.onflow.sdk.FlowTransaction;
import org.onflow.sdk.FlowTransactionProposalKey;
import org.onflow.sdk.FlowTransactionResult;
import org.onflow.sdk.FlowTransactionStatus;
import org.onflow.sdk.HashAlgorithm;
import org.onflow.sdk.NumberField;
import org.onflow.sdk.PrivateKey;
import org.onflow.sdk.SignatureAlgorithm;
import org.onflow.sdk.Signer;
import org.onflow.sdk.StringField;

public class App {

    private PrivateKey privateKey;
    private FlowAccessApi api;

    private static final FlowAddress SERVICE_ACCOUNT_ADDRESS = new FlowAddress("f8d6e0586b0a20c7");
    private static final String SERVICE_ACCOUNT_PRIVATE_KEY = "b2bee95fcf0afab690c2f61d8787de0dc9cb2a64eee797d11f6ae8bc29d5f92b";

    public App(String host, int port, String privateKeyHex) {
        this.api = this.getAccessAPI(host, port);
        this.privateKey = this.getPrivateKey(privateKeyHex);
    }

    FlowAccessApi getAccessAPI(String host, int port) {
        return Flow.INSTANCE.newAccessApi(host, port, false, Flow.DEFAULT_USER_AGENT);
    }

    private PrivateKey getPrivateKey(String privateKeyHex) {
        return Crypto.INSTANCE.decodePrivateKey(privateKeyHex, SignatureAlgorithm.ECDSA_P256);
    }

    public FlowAccount getAccount(FlowAddress address) {
        return api.getAccountAtLatestBlock(address);
    }

    public BigDecimal getAccountBalance(FlowAddress address) {
        var account = getAccount(address);
        return BigDecimal.valueOf(account.getBalance());
    }

    private FlowAccountKey getAccountKey(FlowAddress address, int keyIndex) {
        FlowAccount account = getAccount(address);
        return account.getKeys().get(keyIndex);
    }

    private FlowId getLatestBlockID() {
        return api.getLatestBlock(true).getId();
    }

    private FlowId sendTransaction(FlowTransaction tx, FlowAddress signerAddress, Signer signer) {
        tx.addPayloadSignature(signerAddress, 0, signer);
        tx.addEnvelopeSignature(signerAddress, 0, signer);
        return api.sendTransaction(tx);
    }

    private FlowTransactionResult getTransactionResult(FlowId txID) throws Exception {
        return api.getTransactionResultById(txID);
    }

    private FlowTransactionResult waitForSeal(FlowId txID) throws Exception {
        while (true) {
            FlowTransactionResult transactionResult = getTransactionResult(txID);
            if (transactionResult.getStatus().equals(FlowTransactionStatus.SEALED)) {
                return transactionResult;
            }
        }
    }

    private FlowAddress getAccountCreatedFlowAddress(FlowTransactionResult txResult) {
        var eventPayload = txResult.getEvents().get(0).getPayload();


        /*
        JSONObject obj = new JSONObject(eventPayload.toStringUtf8());

        String FlowAddressHex = obj
                .getJSONObject("value")
                .getJSONArray("fields")
                .getJSONObject(0)
                .getJSONObject("value")
                .getString("value");

        return new FlowAddress(FlowAddressHex.substring(2)); */

        return null;
    }

    private byte[] loadTransaction(String name) {
        InputStream is = getClass().getClassLoader().getResourceAsStream(name);
        try {
            return is.readAllBytes();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public FlowAddress createAccount(FlowAddress payerFlowAddress, String publicKeyHex) throws Exception {

        var script = this.loadTransaction("create_account.cdc");
        var latestBlockId = api.getLatestBlock(true);

        // service account
        var address = SERVICE_ACCOUNT_ADDRESS;
        var account = api.getAccountAtLatestBlock(address);
        var privateKey = Crypto.INSTANCE.decodePrivateKey(
                SERVICE_ACCOUNT_PRIVATE_KEY,
                SignatureAlgorithm.ECDSA_P256);

        if (account == null) {
            return null;
        }

        var newPubKey = new FlowPublicKey(null);
        var accountKey = new FlowAccountKey(123,
                newPubKey,
                SignatureAlgorithm.ECDSA_P256,
                HashAlgorithm.SHA2_256,
                1,
                0,
                false);

        var tx = new FlowTransaction(
                new FlowScript(script),
                Arrays.asList(new FlowArgument(new StringField(newPubKey.getStringValue()))),
                latestBlockId.getId(),
                50L,
                new FlowTransactionProposalKey(address, accountKey.getId(), Long.valueOf(accountKey.getSequenceNumber())),
                address,
                Arrays.asList(address),
                new ArrayList<>(),
                new ArrayList<>());

        // sign the transaction
        var signer = Crypto.INSTANCE.getSigner(privateKey, HashAlgorithm.SHA2_256);
        tx = tx.addPayloadSignature(address, 0, signer);
        tx = tx.addEnvelopeSignature(address, 0, signer);

        FlowId flowId = api.sendTransaction(tx);

        FlowTransactionResult result = this.waitForSeal(flowId);
        return this.getAccountCreatedFlowAddress(result);
    }

    public void transferTokens(FlowAddress senderFlowAddress, FlowAddress recipientFlowAddress, BigDecimal amount) throws Exception {

        if (amount.scale() != 8) {
            throw new Exception("FLOW amount must have exactly 8 decimal places of precision (e.g. 10.00000000)");
        }

        var senderAccountKey = this.getAccountKey(senderFlowAddress, 0);
        var privateKey = Crypto.INSTANCE.decodePrivateKey(
                SERVICE_ACCOUNT_PRIVATE_KEY,
                SignatureAlgorithm.ECDSA_P256);

        var latestBlockID = this.getLatestBlockID();

        var script = this.loadTransaction("transfer_flow.cdc");

        var authorizers = new ArrayList<FlowAddress>();
        authorizers.add(senderFlowAddress);


        var amountField = new FlowArgument(new NumberField("UFix64", amount.toPlainString()));
        var addressField = new FlowArgument(new AddressField(recipientFlowAddress.getStringValue()));
        var args = Arrays.asList(amountField, addressField);

        var tx = new FlowTransaction(
                new FlowScript(script),
                args,
                latestBlockID,
                100L,
                new FlowTransactionProposalKey(
                        senderFlowAddress,
                        senderAccountKey.getId(),
                        Long.valueOf(senderAccountKey.getSequenceNumber())),
                senderFlowAddress,
                Arrays.asList(senderFlowAddress),
                new ArrayList<>(),
                new ArrayList<>());

        var signer = Crypto.INSTANCE.getSigner(privateKey, HashAlgorithm.SHA2_256);
        tx = tx.addPayloadSignature(senderFlowAddress, 0, signer);
        tx = tx.addEnvelopeSignature(senderFlowAddress, 0, signer);

        FlowId flowId = api.sendTransaction(tx);

        // wait for transaction to be sealed
        this.waitForSeal(flowId);
    }

    public static void main(String[] args) { }
}
