package org.onflow.examples.java;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;

import com.google.common.io.BaseEncoding;
import org.onflow.sdk.AddressField;
import org.onflow.sdk.Crypto;
import org.onflow.sdk.Flow;
import org.onflow.sdk.FlowAccessApi;
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
import org.onflow.sdk.PrivateKey;
import org.onflow.sdk.SignatureAlgorithm;
import org.onflow.sdk.Signer;
import org.onflow.sdk.StringField;
import org.onflow.sdk.UFix64NumberField;

public final class App {

    private final FlowAccessApi accessAPI;
    private final PrivateKey privateKey;

    public App(String host, int port, String privateKeyHex) {
        this.accessAPI = Flow.newAccessApi(host, port);
        this.privateKey = Crypto.decodePrivateKey(privateKeyHex);
    }

    public FlowAddress createAccount(FlowAddress payerAddress, String publicKeyHex) {
        FlowAccountKey payerAccountKey = this.getAccountKey(payerAddress, 0);
        FlowAccountKey newAccountPublicKey = new FlowAccountKey(
                0,
                new FlowPublicKey(publicKeyHex),
                SignatureAlgorithm.ECDSA_P256,
                HashAlgorithm.SHA3_256,
                1000,
                0,
                false);

        FlowTransaction tx = new FlowTransaction(
                new FlowScript(loadScript("create_account.cdc")),
                Arrays.asList(new FlowArgument(new StringField(newAccountPublicKey.toString()))),
                this.getLatestBlockID(),
                100L,
                new FlowTransactionProposalKey(
                        payerAddress,
                        payerAccountKey.getId(),
                        payerAccountKey.getSequenceNumber()),
                payerAddress,
                Arrays.asList(payerAddress),
                new ArrayList<>(),
                new ArrayList<>());

        Signer signer = Crypto.getSigner(this.privateKey, payerAccountKey.getHashAlgo());
        tx = tx.addEnvelopeSignature(payerAddress, payerAccountKey.getId(), signer);

        FlowId txID = this.accessAPI.sendTransaction(tx);
        FlowTransactionResult txResult = this.waitForSeal(txID);

        return this.getAccountCreatedAddress(txResult);
    }

    public void transferTokens(FlowAddress senderAddress, FlowAddress recipientAddress, BigDecimal amount) throws Exception {
        // exit early
        if (amount.scale() != 8) {
            throw new Exception("FLOW amount must have exactly 8 decimal places of precision (e.g. 10.00000000)");
        }

        FlowAccountKey senderAccountKey = this.getAccountKey(senderAddress, 0);
        FlowTransaction tx = new FlowTransaction(
                new FlowScript(loadScript("transfer_flow.cdc")),
                Arrays.asList(
                        new FlowArgument(new UFix64NumberField(amount.toPlainString())),
                        new FlowArgument(new AddressField(recipientAddress.getBase16Value()))),
                this.getLatestBlockID(),
                100L,
                new FlowTransactionProposalKey(
                        senderAddress,
                        senderAccountKey.getId(),
                        senderAccountKey.getSequenceNumber()),
                senderAddress,
                Arrays.asList(senderAddress),
                new ArrayList<>(),
                new ArrayList<>());

        Signer signer = Crypto.getSigner(this.privateKey, senderAccountKey.getHashAlgo());
        tx = tx.addEnvelopeSignature(senderAddress, senderAccountKey.getId(), signer);

        FlowId txID = this.accessAPI.sendTransaction(tx);
        this.waitForSeal(txID);
    }

    public FlowAccount getAccount(FlowAddress address) {
        FlowAccount ret = this.accessAPI.getAccountAtLatestBlock(address);
        return ret;
    }

    public BigDecimal getAccountBalance(FlowAddress address) {
        FlowAccount account = this.getAccount(address);
        return account.getBalance();
    }

    private FlowId getLatestBlockID() {
        return this.accessAPI.getLatestBlockHeader().getId();
    }

    private FlowAccountKey getAccountKey(FlowAddress address, int keyIndex) {
        FlowAccount account = this.getAccount(address);
        return account.getKeys().get(keyIndex);
    }

    private FlowTransactionResult getTransactionResult(FlowId txID) {
        FlowTransactionResult result = this.accessAPI.getTransactionResultById(txID);
        return result;
    }

    private FlowTransactionResult waitForSeal(FlowId txID) {
        FlowTransactionResult txResult;

        while(true) {
            txResult = this.getTransactionResult(txID);
            if (txResult.getStatus().equals(FlowTransactionStatus.SEALED)) {
                return txResult;
            }

            try {
                Thread.sleep(1000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private FlowAddress getAccountCreatedAddress(FlowTransactionResult txResult) {
        String rez = txResult
                .getEvents()
                .get(0)
                .getEvent()
                .getValue()
                .getFields()[0]
                .getValue()
                .getValue().toString();
        return new FlowAddress(rez);
    }

    private byte[] loadScript(String name) {
        try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(name);) {
            return is.readAllBytes();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    private byte[] hexToBytes(String hex) {
        return BaseEncoding.base16().lowerCase().decode(hex);
    }
}
