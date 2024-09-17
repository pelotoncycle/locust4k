package com.onepeloton.locust4k.messages

import org.msgpack.core.MessageFormat
import org.msgpack.core.MessagePack
import org.msgpack.core.MessageUnpacker
import org.msgpack.value.ValueType
import java.io.IOException

/**
 * Based on `com.github.myzhan.locust4j.message.Message`.
 */
class Message {
    val type: LocustMessageType
    val data: Map<String, Any?>?
    val version: Int
    val nodeId: String?

    constructor(type: LocustMessageType, nodeId: String, data: Map<String, Any?>? = null, version: Int = -1) {
        this.type = type
        this.nodeId = nodeId
        this.data = data
        this.version = version
    }

    constructor(bytes: ByteArray) {
        version = 0

        val unpacker = MessagePack.newDefaultUnpacker(bytes)
        unpacker.unpackArrayHeader()
        type = LocustMessageType.valueOfLowerCase(unpacker.unpackString())
        data = if (unpacker.nextFormat != MessageFormat.NIL) {
            unpackMap(unpacker)
        } else {
            unpacker.unpackNil()
            null
        }
        nodeId = if (unpacker.nextFormat != MessageFormat.NIL) {
            unpacker.unpackString()
        } else {
            unpacker.unpackNil()
            null
        }
        unpacker.close()
    }

    @get:Throws(IOException::class)
    val bytes: ByteArray
        get() {
            val packer = MessagePack.newDefaultBufferPacker()
            val visitor = Visitor(packer)
            // a message contains three fields, (type & data & nodeId)
            packer.packArrayHeader(3)

            // pack the first field
            packer.packString(type.lowerCase)

            // pack the second field
            if (LocustMessageType.CLIENT_READY == type) {
                if (version == -1) {
                    packer.packInt(version)
                } else {
                    packer.packNil()
                }
            } else {
                if (data != null) {
                    packer.packMapHeader(data.size)
                    for ((key, value) in data) {
                        packer.packString(key)
                        visitor.visit(value)
                    }
                } else {
                    packer.packNil()
                }
            }

            // pack the third field
            packer.packString(nodeId)
            val bytes = packer.toByteArray()
            packer.close()
            return bytes
        }

    override fun toString(): String = "$nodeId-$type-$data"

    companion object {
        @Throws(IOException::class)
        fun unpackMap(unpacker: MessageUnpacker): Map<String, Any?> {
            var mapSize = unpacker.unpackMapHeader()
            val result: MutableMap<String, Any?> = HashMap(6)
            while (mapSize > 0) {
                var key: String? = null
                // unpack key
                if (unpacker.nextFormat == MessageFormat.NIL) {
                    unpacker.unpackNil()
                } else {
                    key = unpacker.unpackString()
                }
                // unpack value
                val messageFormat = unpacker.nextFormat
                var value: Any?
                when (messageFormat.valueType) {
                    ValueType.BOOLEAN -> value = unpacker.unpackBoolean()
                    ValueType.FLOAT -> value = unpacker.unpackFloat()
                    ValueType.INTEGER -> value = unpacker.unpackInt()
                    ValueType.NIL -> {
                        value = null
                        unpacker.unpackNil()
                    }

                    ValueType.STRING -> value = unpacker.unpackString()
                    ValueType.MAP -> value = unpackMap(unpacker)
                    ValueType.ARRAY -> {
                        val size = unpacker.unpackArrayHeader()
                        value = ArrayList<Any?>(size)
                        var index = 0
                        while (index < size) {
                            value.add(unpacker.unpackString())
                            ++index
                        }
                    }

                    else -> throw IOException("Message received unsupported type: ${messageFormat.valueType}")
                }
                if (null != key) {
                    result[key] = value
                }
                mapSize--
            }
            return result
        }
    }
}
