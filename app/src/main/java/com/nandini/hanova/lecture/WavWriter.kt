package com.nandini.hanova.lecture

import java.io.File
import java.io.RandomAccessFile

/**
 * Writes 16 kHz mono PCM16 to a .wav file as it arrives (never holds the lecture in RAM).
 * The header sizes are patched on close(). Safe fallback: re-transcribe this file later
 * on a laptop with a bigger model if live captions missed something.
 */
class WavWriter(val file: File, private val sampleRate: Int = 16000) {
    private val raf = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var closed = false

    init {
        raf.setLength(0)
        raf.write(header(0))
    }

    @Synchronized
    fun write(pcm16: ByteArray) {
        if (closed) return
        raf.write(pcm16)
        dataBytes += pcm16.size
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        raf.seek(0)
        raf.write(header(dataBytes))
        raf.close()
    }

    private fun header(dataLen: Long): ByteArray {
        val b = ByteArray(44)
        fun str(at: Int, s: String) = s.toByteArray().copyInto(b, at)
        fun i32(at: Int, v: Long) { for (k in 0..3) b[at + k] = ((v shr (8 * k)) and 0xff).toByte() }
        fun i16(at: Int, v: Int) { b[at] = (v and 0xff).toByte(); b[at + 1] = ((v shr 8) and 0xff).toByte() }
        str(0, "RIFF"); i32(4, 36 + dataLen); str(8, "WAVE")
        str(12, "fmt "); i32(16, 16); i16(20, 1); i16(22, 1)
        i32(24, sampleRate.toLong()); i32(28, sampleRate * 2L); i16(32, 2); i16(34, 16)
        str(36, "data"); i32(40, dataLen)
        return b
    }
}
