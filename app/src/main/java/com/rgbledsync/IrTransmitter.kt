package com.rgbledsync

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.roundToInt

class IrTransmitter : IrController {

    companion object {
        private const val SAMPLE_RATE = 192000
        private const val CARRIER_FREQ = 38000.0
        private const val CARRIER_PERIOD_US = 1_000_000.0 / CARRIER_FREQ

        private const val LEADER_ON_US  = 9000
        private const val LEADER_OFF_US = 4500
        private const val BIT_ON_US     = 562
        private const val BIT_OFF_0_US  = 562
        private const val BIT_OFF_1_US  = 1687
        private const val STOP_US       = 562
    }

    private var audioTrack: AudioTrack? = null

    @Synchronized
    override fun transmit(necCode: Long) {
        release()
        val buffer = generateNecBuffer(necCode)
        val track = buildTrack(buffer)
        track.write(buffer, 0, buffer.size)
        track.play()
        audioTrack = track
        val durationMs = (buffer.size.toLong() * 1000 / SAMPLE_RATE)
        try {
            Thread.sleep(durationMs + 50)
        } catch (_: InterruptedException) {}
        release()
    }

    private fun buildTrack(buffer: ShortArray): AudioTrack {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val fmt = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        return AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(fmt)
            .setBufferSizeInBytes(buffer.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
    }

    @Synchronized
    override fun release() {
        audioTrack?.let {
            if (it.playState == AudioTrack.PLAYSTATE_PLAYING) {
                it.stop()
            }
            it.release()
        }
        audioTrack = null
    }

    private fun generateNecBuffer(code: Long): ShortArray {
        val usToSamples = { us: Int -> (us.toLong() * SAMPLE_RATE / 1_000_000L).toInt() }

        val leaderLen   = usToSamples(LEADER_ON_US) + usToSamples(LEADER_OFF_US)
        var dataLen = 0
        for (i in 0 until 32) {
            dataLen += usToSamples(BIT_ON_US)
            dataLen += if ((code shr i and 1L) == 1L)
                usToSamples(BIT_OFF_1_US) else usToSamples(BIT_OFF_0_US)
        }
        val stopLen = usToSamples(STOP_US)

        val totalSamples = leaderLen + dataLen + stopLen
        val buf = ShortArray(totalSamples)

        val carrierPeriodSamples = (CARRIER_PERIOD_US * SAMPLE_RATE / 1_000_000.0).roundToInt()
        val halfCarrierSamples = carrierPeriodSamples / 2

        var pos = 0

        fun emitCarrier(durationUs: Int) {
            val samples = usToSamples(durationUs)
            for (i in 0 until samples) {
                buf[pos + i] = if ((i % carrierPeriodSamples) < halfCarrierSamples)
                    Short.MAX_VALUE else Short.MIN_VALUE
            }
            pos += samples
        }

        fun emitSilence(durationUs: Int) {
            pos += usToSamples(durationUs)
        }

        emitCarrier(LEADER_ON_US)
        emitSilence(LEADER_OFF_US)

        for (i in 0 until 32) {
            emitCarrier(BIT_ON_US)
            if (((code shr i) and 1L) == 1L) {
                emitSilence(BIT_OFF_1_US)
            } else {
                emitSilence(BIT_OFF_0_US)
            }
        }

        emitCarrier(STOP_US)

        return buf
    }
}
