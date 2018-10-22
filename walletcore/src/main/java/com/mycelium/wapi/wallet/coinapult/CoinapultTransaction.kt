package com.mycelium.wapi.wallet.coinapult

import com.google.common.base.Optional
import com.mrd.bitlib.util.Sha256Hash
import com.mycelium.wapi.wallet.ConfirmationRiskProfileLocal
import com.mycelium.wapi.wallet.GenericTransaction
import com.mycelium.wapi.wallet.coins.GenericAssetInfo
import com.mycelium.wapi.wallet.coins.Value
import java.io.Serializable


class CoinapultTransaction(val _hash: Sha256Hash, val value: Value, val incoming: Boolean, val completeTime: Long
                           , val state: String, val time: Long) : GenericTransaction, Serializable {
    override fun getType(): GenericAssetInfo = value.getType()

    override fun getHash(): Sha256Hash = _hash

    override fun getHashAsString(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getHashBytes(): ByteArray {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDepthInBlocks(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setDepthInBlocks(depthInBlocks: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAppearedAtChainHeight(): Int = 0

    override fun setAppearedAtChainHeight(appearedAtChainHeight: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getTimestamp(): Long = time

    override fun setTimestamp(timestamp: Int) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isQueuedOutgoing(): Boolean = false

    override fun getConfirmationRiskProfile(): Optional<ConfirmationRiskProfileLocal> = Optional.absent()

    override fun getFee(): Value {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getInputs(): MutableList<GenericTransaction.GenericInput> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getOutputs(): MutableList<GenericTransaction.GenericOutput> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSent(): Value = if (!isIncoming) value else Value.zeroValue(value.getType())

    override fun getReceived(): Value = if (isIncoming) value else Value.zeroValue(value.getType())

    override fun isIncoming(): Boolean = incoming

    override fun getRawSize(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
