package org.example.project

import java.util.zip.CRC32

fun crc32Of(bytes: ByteArray, offset: Int, length: Int): Long {
    require(offset >= 0) { "offset must be >= 0" }
    require(length >= 0) { "length must be >= 0" }
    require(offset + length <= bytes.size) { "offset + length out of bounds" }

    val crc = CRC32()
    crc.update(bytes, offset, length)
    return crc.value
}