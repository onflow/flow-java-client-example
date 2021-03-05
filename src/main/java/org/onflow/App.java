package org.onflow;

import static org.onflow.sdk.KeysKt.InitCrypto;
import com.google.protobuf.ByteString;
import org.bouncycastle.util.encoders.Hex;
import java.math.BigInteger;
import io.grpc.ManagedChannelBuilder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

import org.onflow.protobuf.entities.TransactionOuterClass;
import org.onflow.sdk.*;
import org.onflow.protobuf.access.Access;
import org.onflow.protobuf.access.AccessAPIGrpc;
import org.onflow.protobuf.entities.TransactionOuterClass.Transaction;

class App {

    public static void main(String[] args) {

        InitCrypto();

        var payerAddress = new Address(Hex.decode("f8d6e0586b0a20c7"));

        PrivateKey payerPrivateKey = new ECDSAp256_SHA3_256PrivateKey(
                new BigInteger("ceff2bd777f3b5c81d7edfd191c99239cb9c56fc64946741339a55fd094586c9", 16)
        );

        var managedChannel = ManagedChannelBuilder.forAddress(
                "localhost",
                3569
        ).usePlaintext().build();

        var accessAPI = AccessAPIGrpc.newBlockingStub(managedChannel);

        var getAccountRequest = Access.GetAccountRequest.newBuilder().setAddress(
                ByteString.copyFrom(payerAddress.getBytes())
        ).build();

        var accountResponse = accessAPI.getAccount(getAccountRequest);

        var accountKey = accountResponse.getAccount().getKeys(0);

        var getLatestBlockRequest = Access.GetLatestBlockHeaderRequest.newBuilder().setIsSealed(true).build();

        var latestBlock = accessAPI.getLatestBlockHeader(getLatestBlockRequest);
        var latestBlockID = new Identifier(latestBlock.getBlock().getId().toByteArray());

        byte[] script = "transaction { prepare(signer: AuthAccount) { let account = AuthAccount(payer: signer) } }".getBytes(StandardCharsets.UTF_8);

        var authorizers = new ArrayList<Address>();
        authorizers.add(payerAddress);

        var tx = new org.onflow.sdk.Transaction(
                script,
                new ArrayList<>(),
                latestBlockID,
                100,
                new ProposalKey(
                        payerAddress,
                        accountKey.getIndex(),
                        accountKey.getSequenceNumber()
                ),
                payerAddress,
                authorizers,
                new ArrayList<>(),
                new ArrayList<>()
        );

        byte[] rawPayerSignature = payerPrivateKey.Sign(tx.envelopCanonicalForm());

        var payerSignature = new TransactionSignature(
                payerAddress,
                0,
                accountKey.getIndex(),
                rawPayerSignature
        );

        System.out.println(tx);

        var sendTransactionRequest = Access.SendTransactionRequest.newBuilder().setTransaction(
                Transaction.newBuilder()
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
                        )
        ).build();

        var sendTransactionResponse = accessAPI.sendTransaction(sendTransactionRequest);

        Access.TransactionResultResponse txResult;

        while (true) {
            txResult = accessAPI.getTransactionResult(
                    Access.GetTransactionRequest.newBuilder().setId(sendTransactionResponse.getId()).build()
            );

            if (txResult.getStatus() == TransactionOuterClass.TransactionStatus.SEALED) {
                break;
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        System.out.println(txResult.getEventsList());
    }
}