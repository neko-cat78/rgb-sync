package com.rgbledsync

import android.content.Context
import android.hardware.ConsumerIrManager

interface IrController {
    fun transmit(necCode: Long)
    fun release()

    companion object {
        fun create(context: Context): IrController {
            val consumerIr = context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager
            if (consumerIr != null && consumerIr.hasIrEmitter()) {
                return BuiltInIrTransmitter(consumerIr)
            }
            return IrTransmitter()
        }
    }
}
