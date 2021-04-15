package org.onflow.examples.kotlin

import java.math.BigDecimal
import org.onflow.sdk.AddressField
import org.onflow.sdk.Crypto
import org.onflow.sdk.Flow
import org.onflow.sdk.FlowAccount
import org.onflow.sdk.FlowAccountKey
import org.onflow.sdk.FlowAddress
import org.onflow.sdk.FlowArgument
import org.onflow.sdk.FlowId
import org.onflow.sdk.FlowPublicKey
import org.onflow.sdk.FlowScript
import org.onflow.sdk.FlowTransaction
import org.onflow.sdk.FlowTransactionProposalKey
import org.onflow.sdk.FlowTransactionResult
import org.onflow.sdk.FlowTransactionStatus
import org.onflow.sdk.HashAlgorithm
import org.onflow.sdk.SignatureAlgorithm
import org.onflow.sdk.StringField
import org.onflow.sdk.UFix64NumberField
import org.onflow.sdk.bytesToHex

internal class App(host: String, port: Int, privateKeyHex: String) {

    private val accessAPI = Flow.newAccessApi(host, port)
    private val privateKey = Crypto.decodePrivateKey(privateKeyHex)

    private val latestBlockID: FlowId get() = accessAPI.getLatestBlockHeader().id

    fun getAccount(address: FlowAddress): FlowAccount = accessAPI.getAccountAtLatestBlock(address)!!

    fun getAccountBalance(address: FlowAddress): BigDecimal {
        val account = getAccount(address)
        return account.balance
    }

    private fun getAccountKey(address: FlowAddress, keyIndex: Int): FlowAccountKey {
        val account = getAccount(address)
        return account.keys[keyIndex]
    }

    private fun getTransactionResult(txID: FlowId): FlowTransactionResult {
        val txResult = accessAPI.getTransactionResultById(txID)!!
        if (txResult.errorMessage.isNotEmpty()) {
            throw Exception(txResult.errorMessage)
        }
        return txResult
    }

    private fun waitForSeal(txID: FlowId): FlowTransactionResult {
        var txResult: FlowTransactionResult
        while (true) {
            txResult = getTransactionResult(txID)
            if (txResult.status == FlowTransactionStatus.SEALED) {
                return txResult
            }
            Thread.sleep(1000)
        }
    }

    private fun getAccountCreatedAddress(txResult: FlowTransactionResult): FlowAddress {
        val addressHex = txResult
            .events[0]
            .event
            .value!!
            .fields[0]
            .value
            .value as String
        return FlowAddress(addressHex.substring(2))
    }

    private fun loadScript(name: String): ByteArray
        = javaClass.classLoader.getResourceAsStream(name)!!.use { it.readAllBytes() }

    fun createAccount(payerAddress: FlowAddress, publicKeyHex: String): FlowAddress {

        // find payer account
        val payerAccountKey = getAccountKey(payerAddress, 0)

        // create a public key for the new account
        val newAccountPublicKey = FlowAccountKey(
            publicKey = FlowPublicKey(publicKeyHex),
            signAlgo = SignatureAlgorithm.ECDSA_P256,
            hashAlgo = HashAlgorithm.SHA3_256,
            weight = 1000
        )

        // create transaction
        var tx = FlowTransaction(
            script = FlowScript(loadScript("create_account.cdc")),
            arguments = listOf(
                FlowArgument(StringField(newAccountPublicKey.encoded.bytesToHex()))
            ),
            referenceBlockId = latestBlockID,
            gasLimit = 100,
            proposalKey = FlowTransactionProposalKey(
                address = payerAddress,
                keyIndex = payerAccountKey.id,
                sequenceNumber = payerAccountKey.sequenceNumber.toLong()
            ),
            payerAddress = payerAddress,
            authorizers = listOf(
                payerAddress
            )
        )

        // sign the transaction
        val signer = Crypto.getSigner(privateKey, payerAccountKey.hashAlgo)
        tx = tx.addEnvelopeSignature(payerAddress, payerAccountKey.id,  signer)

        // send the transaction
        val txID = accessAPI.sendTransaction(tx)

        // wait for transaction to be sealed
        val txResult = waitForSeal(txID)
        return getAccountCreatedAddress(txResult)
    }

    fun transferTokens(senderAddress: FlowAddress, recipientAddress: FlowAddress, amount: BigDecimal) {
        if (amount.scale() != 8) {
            throw Exception("FLOW amount must have exactly 8 decimal places of precision (e.g. 10.00000000)")
        }
        val senderAccountKey = getAccountKey(senderAddress, 0)

        // create transaction
        var tx = FlowTransaction(
            script = FlowScript(loadScript("transfer_flow.cdc")),
            arguments = listOf(
                FlowArgument(UFix64NumberField(amount.toPlainString())),
                FlowArgument(AddressField(recipientAddress.base16Value))
            ),
            referenceBlockId = latestBlockID,
            gasLimit = 100,
            proposalKey = FlowTransactionProposalKey(
                address = senderAddress,
                keyIndex = senderAccountKey.id,
                sequenceNumber = senderAccountKey.sequenceNumber.toLong()
            ),
            payerAddress = senderAddress,
            authorizers = listOf(
                senderAddress
            )
        )

        // sign the transaction
        val signer = Crypto.getSigner(privateKey, senderAccountKey.hashAlgo)
        tx = tx.addEnvelopeSignature(senderAddress, senderAccountKey.id,  signer)

        // send the transaction
        val txID = accessAPI.sendTransaction(tx)

        // wait for transaction to be sealed
        val txResult = waitForSeal(txID)
    }
}
