package org.onflow;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannelBuilder;
import org.bouncycastle.util.encoders.Hex;
import org.onflow.protobuf.access.Access;
import org.onflow.protobuf.access.AccessAPIGrpc;
import org.onflow.protobuf.entities.AccountOuterClass;
import org.onflow.protobuf.entities.TransactionOuterClass;
import org.onflow.protobuf.entities.TransactionOuterClass.Transaction;
import org.onflow.sdk.*;
import org.json.JSONObject;
import org.ethereum.util.RLP;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.onflow.sdk.KeysKt.InitCrypto;

class App {

    private AccessAPIGrpc.AccessAPIBlockingStub accessAPI;
    private PrivateKey privateKey;

    public App(String host, int port, String privateKeyHex) {
        this.accessAPI = this.getAccessAPI(host, port);
        this.privateKey = this.getPrivateKey(privateKeyHex);
    }

    private AccessAPIGrpc.AccessAPIBlockingStub getAccessAPI(String host, int port) {
        var managedChannel = ManagedChannelBuilder.forAddress(
                host,
                port
        ).usePlaintext().build();

        return AccessAPIGrpc.newBlockingStub(managedChannel);
    }

    private PrivateKey getPrivateKey(String privateKeyHex) {
        return new ECDSAp256_SHA3_256PrivateKey(new BigInteger(privateKeyHex, 16));
    }

    private AccountOuterClass.AccountKey getAccountKey(Address address, int keyIndex) {
        var getAccountRequest = Access.GetAccountRequest.newBuilder().setAddress(
                ByteString.copyFrom(address.getBytes())
        ).build();

        var accountResponse = this.accessAPI.getAccount(getAccountRequest);

        return accountResponse.getAccount().getKeys(keyIndex);
    }

    private Identifier getLatestBlockID() {
        var getLatestBlockRequest = Access.GetLatestBlockHeaderRequest
                .newBuilder().setIsSealed(true).build();
        var latestBlock = this.accessAPI.getLatestBlockHeader(getLatestBlockRequest);
        return  new Identifier(latestBlock.getBlock().getId().toByteArray());
    }

    private TransactionSignature signTransaction(Address address, int keyIndex, byte[] message) {
        byte[] rawSignature = this.privateKey.Sign(message);

        return new TransactionSignature(
                address,
                0,
                keyIndex,
                rawSignature
        );
    }

    private Identifier sendTransaction(org.onflow.sdk.Transaction tx, TransactionSignature payerSignature) {
        var transactionRequest = Transaction.newBuilder()
                .setScript(ByteString.copyFrom(tx.getScript()))
                .setReferenceBlockId(ByteString.copyFrom(tx.getReferenceBlockID().getBytes()))
                .setGasLimit(tx.getGasLimit())
                .setProposalKey(
                        Transaction.ProposalKey.newBuilder()
                                .setAddress(ByteString.copyFrom(tx.getProposalKey().getAddress().getBytes()))
                                .setKeyId((int)tx.getProposalKey().getKeyIndex())
                                .setSequenceNumber((int)tx.getProposalKey().getSequenceNumber())
                )
                .setPayer(ByteString.copyFrom(tx.getPayer().getBytes()))
                .addAuthorizers(ByteString.copyFrom(tx.getPayer().getBytes()))
                .addEnvelopeSignatures(
                        Transaction.Signature.newBuilder()
                                .setAddress(ByteString.copyFrom(payerSignature.getAddress().getBytes()))
                                .setKeyId(payerSignature.getKeyIndex())
                                .setSignature(ByteString.copyFrom(payerSignature.getSignature()))
                );

        for (byte[] arg : tx.getArguments()) {
            transactionRequest.addArguments(ByteString.copyFrom(arg));
        }

        var sendTransactionRequest = Access.SendTransactionRequest
                .newBuilder().setTransaction(transactionRequest).build();

        var sendTransactionResponse = accessAPI.sendTransaction(sendTransactionRequest);

        return new Identifier(sendTransactionResponse.getId().toByteArray());
    }

    private Access.TransactionResultResponse getTransactionResult(Identifier txID) throws Exception {
        var txResult = this.accessAPI.getTransactionResult(
                Access.GetTransactionRequest.newBuilder().setId(ByteString.copyFrom(txID.getBytes())).build()
        );

        if (txResult.getErrorMessage() != "") {
            throw new Exception(txResult.getErrorMessage());
        }

        return txResult;
    }

    private Access.TransactionResultResponse waitForSeal(Identifier txID) throws Exception {
        Access.TransactionResultResponse txResult;

        while (true) {
            txResult = this.getTransactionResult(txID);

            if (txResult.getStatus() == TransactionOuterClass.TransactionStatus.SEALED) {
               return txResult;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private Address getAccountCreatedAddress(Access.TransactionResultResponse txResult) {
        var eventPayload = txResult.getEventsList().get(0).getPayload();

        JSONObject obj = new JSONObject(eventPayload.toStringUtf8());

        String addressHex = obj
                .getJSONObject("value")
                .getJSONArray("fields")
                .getJSONObject(0)
                .getJSONObject("value")
                .getString("value");

        return new Address(addressHex.substring(2));
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

    public Address createAccount(Address payerAddress, String publicKeyHex) throws Exception {

        var payerAccountKey = this.getAccountKey(payerAddress, 0);

        var latestBlockID = this.getLatestBlockID();

        var script = this.loadTransaction("create_account.cdc");

        var authorizers = new ArrayList<Address>();
        authorizers.add(payerAddress);

        Object[] accountKey = {
                Hex.decode(publicKeyHex),
                2,
                3,
                1000,
        };

        var encodedAccountKey = RLP.encode(accountKey);

        var publicKeyJSON = new JSONObject();
        publicKeyJSON.put("type", "String");
        publicKeyJSON.put("value", Hex.toHexString(encodedAccountKey));

        var args = new ArrayList<byte[]>();
        args.add(publicKeyJSON.toString().getBytes(StandardCharsets.UTF_8));

        var tx = new org.onflow.sdk.Transaction(
                script,
                args,
                latestBlockID,
                100,
                new ProposalKey(
                        payerAddress,
                        payerAccountKey.getIndex(),
                        payerAccountKey.getSequenceNumber()
                ),
                payerAddress,
                authorizers,
                new ArrayList<>(),
                new ArrayList<>()
        );

        var payerSignature = this.signTransaction(
            payerAddress, payerAccountKey.getIndex(), tx.envelopCanonicalForm()
        );

        var txID = this.sendTransaction(tx, payerSignature);

        // wait for transaction to be sealed
        var txResult = this.waitForSeal(txID);

        return this.getAccountCreatedAddress(txResult);
    }

    public void transferTokens(Address senderAddress, Address recipientAddress, String amount) throws Exception {

        var senderAccountKey = this.getAccountKey(senderAddress, 0);

        var latestBlockID = this.getLatestBlockID();

        var script = this.loadTransaction("transfer_flow.cdc");

        var authorizers = new ArrayList<Address>();
        authorizers.add(senderAddress);

        var amountJSON = new JSONObject();
        amountJSON.put("type", "UFix64");
        amountJSON.put("value", amount);

        var recipientJSON = new JSONObject();
        recipientJSON.put("type", "Address");
        recipientJSON.put("value", "0x"+Hex.toHexString(recipientAddress.getBytes()));

        var args = new ArrayList<byte[]>();
        args.add(amountJSON.toString().getBytes(StandardCharsets.UTF_8));
        args.add(recipientJSON.toString().getBytes(StandardCharsets.UTF_8));

        var tx = new org.onflow.sdk.Transaction(
                script,
                args,
                latestBlockID,
                100,
                new ProposalKey(
                        senderAddress,
                        senderAccountKey.getIndex(),
                        senderAccountKey.getSequenceNumber()
                ),
                senderAddress,
                authorizers,
                new ArrayList<>(),
                new ArrayList<>()
        );

        var payerSignature = this.signTransaction(
                senderAddress, senderAccountKey.getIndex(), tx.envelopCanonicalForm()
        );

        var txID = this.sendTransaction(tx, payerSignature);

        // wait for transaction to be sealed
        this.waitForSeal(txID);
    }

    public static void main(String[] args) { }
}