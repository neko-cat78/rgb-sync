package com.rgbledsync

import android.hardware.ConsumerIrManager

class BuiltInIrTransmitter(private val consumerIr: ConsumerIrManager) : IrController {

    companion object {
        private const val CARRIER_FREQ = 38000
        private const val LEADER_ON  = 9000
        private const val LEADER_OFF = 4500
        private const val BIT_ON     = 562
        private const val BIT_OFF_0  = 562
        private const val BIT_OFF_1  = 1687
        private const val STOP       = 562
    }

    override fun transmit(necCode: Long) {
        val pattern = buildNecPattern(necCode)
        consumerIr.transmit(CARRIER_FREQ, pattern)
    }

    private fun buildNecPattern(code: Long): IntArray {
        val list = mutableListOf<Int>()
        list.add(LEADER_ON)
        list.add(LEADER_OFF)

        for (i in 0 until 32) {
            list.add(BIT_ON)
            list.add(if (((code shr i) and 1L) == 1L) BIT_OFF_1 else BIT_OFF_0)
        }

        list.add(STOP)
        return list.toIntArray()
    }

    override fun release() {
    }
}
