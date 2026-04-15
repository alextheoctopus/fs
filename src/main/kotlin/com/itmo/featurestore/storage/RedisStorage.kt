package com.itmo.featurestore.storage

import com.proto.api.Api.EntityRecordRedis
import com.proto.api.Api.RedisPayload
import redis.clients.jedis.Jedis

class RedisStorage(
    private val host: String = "localhost",
    private val port: Int = 6379
) {

    fun getPayloads(keys: List<String>): List<RedisPayload?> {
        if (keys.isEmpty()) return emptyList()

        Jedis(host, port).use { jedis ->
            val binaryKeys: Array<ByteArray> = keys
                .map { it.toByteArray() }
                .toTypedArray()

            val values: List<ByteArray?> = jedis.mget(*binaryKeys)

            return values.map { bytes ->
                if (bytes == null) null else RedisPayload.parseFrom(bytes)
            }
        }
    }

    fun save(record: EntityRecordRedis) {
        Jedis(host, port).use { jedis ->
            jedis.set(record.key.toByteArray(), record.value.toByteArray())
        }
    }

    fun saveAll(records: List<EntityRecordRedis>) {
        if (records.isEmpty()) return

        Jedis(host, port).use { jedis ->
            val pipeline = jedis.pipelined()
            for (record in records) {
                pipeline.set(record.key.toByteArray(), record.value.toByteArray())
            }
            pipeline.sync()
        }
    }

    fun getRaw(key: String): ByteArray? {
        return Jedis(host, port).use { jedis ->
            jedis.get(key.toByteArray())
        }
    }

    fun getPayload(key: String): RedisPayload? {
        val bytes = getRaw(key) ?: return null
        return RedisPayload.parseFrom(bytes)
    }

    fun exists(key: String): Boolean {
        return Jedis(host, port).use { jedis ->
            jedis.exists(key.toByteArray())
        }
    }

    fun delete(key: String): Long {
        return Jedis(host, port).use { jedis ->
            jedis.del(key.toByteArray())
        }
    }
}