package com.onepeloton.locust4k.messages

import org.eclipse.collections.api.map.primitive.MutableIntIntMap
import org.eclipse.collections.api.map.primitive.MutableLongIntMap
import org.msgpack.core.MessagePacker
import java.io.IOException

/**
 * Based on `com.github.myzhan.locust4j.message.Visitor`.
 */
class Visitor(private val packer: MessagePacker) {
    @Throws(IOException::class)
    fun visit(value: Any?) {
        when (value) {
            null -> {
                visitNull()
            }

            is String -> {
                visitString(value)
            }

            is Int -> {
                visitInt(value)
            }

            is Long -> {
                visitLong(value)
            }

            is Boolean -> {
                visitBool(value)
            }

            is Float -> {
                visitFloat(value)
            }

            is Double -> {
                visitDouble(value)
            }

            is Map<*, *> -> {
                visitMap(value)
            }

            is List<*> -> {
                visitList(value)
            }

            is MutableLongIntMap -> {
                visitMutableLongIntMap(value)
            }

            is MutableIntIntMap -> {
                visitMutableIntIntMap(value)
            }

            else -> {
                throw IOException("Cannot pack type unknown type: ${value.javaClass.getSimpleName()}")
            }
        }
    }

    @Throws(IOException::class)
    private fun visitNull() {
        packer.packNil()
    }

    @Throws(IOException::class)
    private fun visitString(value: Any) {
        packer.packString(value as String)
    }

    @Throws(IOException::class)
    private fun visitInt(value: Any) {
        packer.packInt((value as Int))
    }

    @Throws(IOException::class)
    private fun visitLong(value: Any) {
        packer.packLong((value as Long))
    }

    @Throws(IOException::class)
    private fun visitBool(value: Any) {
        packer.packBoolean((value as Boolean))
    }

    @Throws(IOException::class)
    private fun visitFloat(value: Any) {
        packer.packFloat((value as Float))
    }

    @Throws(IOException::class)
    private fun visitDouble(value: Any) {
        packer.packDouble((value as Double))
    }

    @Throws(IOException::class)
    private fun visitMap(value: Any) {
        val map = value as Map<*, *>
        packer.packMapHeader(map.size)
        for ((k, v) in map) {
            visitString(k!!)
            visit(v)
        }
    }

    @Throws(IOException::class)
    private fun visitList(value: Any) {
        val list = value as List<*>
        packer.packArrayHeader(list.size)
        for (`object` in list) {
            visit(`object`)
        }
    }

    @Throws(IOException::class)
    private fun visitMutableLongIntMap(value: Any) {
        val map = value as MutableLongIntMap
        packer.packMapHeader(map.size())
        map.forEachKeyValue { k, v ->
            packer.packLong(k)
            packer.packInt(v)
        }
    }

    @Throws(IOException::class)
    private fun visitMutableIntIntMap(value: Any) {
        val map = value as MutableIntIntMap
        packer.packMapHeader(map.size())
        map.forEachKeyValue { k, v ->
            packer.packInt(k)
            packer.packInt(v)
        }
    }
}
