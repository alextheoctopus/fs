package com.itmo.benchmark

import com.itmo.featurestore.storage.RedisStorage
import com.proto.api.Api
import com.proto.api.Api.EntityRecordRedis
import com.proto.api.Api.FeatureTypeSingle
import com.proto.api.Api.RedisPayload
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object StorageBenchmark {
    //Режимы бенча (saveAll,getPayloads)
    enum class Mode {
        SAVE, GET
    }

    //Кофигурация запуска
    data class Config(
        val mode: Mode,
        val entityCount: Int,
        val featureCount: Int,
        val concurrency: Int,//кол-во параллельных потоков
        val warmupSeconds: Long = 10,
        val measureSeconds: Long = 30
    )
    /*get 1000 10 50 → мерить GET, батч из 1000 entities, по 10 фич на entity, 50 параллельных потоков*/
    //Результат измерения
    data class BenchResult(
        val count: Long,
        val rps: Double,
        val avgMs: Double,
        val minMs: Double,
        val maxMs: Double,
        val p50Ms: Double,
        val p95Ms: Double,
        val p99Ms: Double
    )

    @JvmStatic
    fun main(args: Array<String>) {
        /*1. Входные параметры*/
        val config = parseArgs(args)

        val storage = RedisStorage()
        /*2. Генерация тестовых данных */

        val records = generateRecords(config.entityCount, config.featureCount)
        val keys = records.map { it.key.removePrefix("entity:") }
        val redisKeys = keys.map { "entity:$it" }

        if (config.mode == Mode.GET) {
            println("Preloading data for GET benchmark...")
            storage.saveAll(records)
        }

        println(
            "Warmup: mode=${config.mode}, entities=${config.entityCount}, " +
                    "features=${config.featureCount}, c=${config.concurrency}, " +
                    "warmup=${config.warmupSeconds}s"
        )

        when (config.mode) {
            Mode.SAVE -> runTimed(
                seconds = config.warmupSeconds,
                concurrency = config.concurrency
            ) {
                storage.saveAll(records)
            }

            Mode.GET -> runTimed(
                seconds = config.warmupSeconds,
                concurrency = config.concurrency
            ) {
                val payloads = storage.getPayloads(redisKeys)
                if (payloads.size != redisKeys.size) {
                    error("Unexpected payload count: ${payloads.size}")
                }
            }
        }

        println(
            "Measure: mode=${config.mode}, entities=${config.entityCount}, " +
                    "features=${config.featureCount}, c=${config.concurrency}, " +
                    "duration=${config.measureSeconds}s"
        )

        val result = when (config.mode) {
            Mode.SAVE -> measureTimed(
                seconds = config.measureSeconds,
                concurrency = config.concurrency
            ) {
                storage.saveAll(records)
            }

            Mode.GET -> measureTimed(
                seconds = config.measureSeconds,
                concurrency = config.concurrency
            ) {
                val payloads = storage.getPayloads(redisKeys)
                if (payloads.size != redisKeys.size) {
                    error("Unexpected payload count: ${payloads.size}")
                }
            }
        }

        printResult(config, result)
    }

    private fun parseArgs(args: Array<String>): Config {
        if (args.size < 4) {
            error(
                "Usage: <save|get> <entityCount> <featureCount> <concurrency> " +
                        "[warmupSeconds] [measureSeconds]"
            )
        }

        val mode = when (args[0].lowercase()) {
            "save" -> Mode.SAVE
            "get" -> Mode.GET
            else -> error("Mode must be 'save' or 'get'")
        }

        return Config(
            mode = mode,
            entityCount = args[1].toInt(),
            featureCount = args[2].toInt(),
            concurrency = args[3].toInt(),
            warmupSeconds = args.getOrNull(4)?.toLong() ?: 10,
            measureSeconds = args.getOrNull(5)?.toLong() ?: 30
        )
    }

    private fun generateRecords(
        entityCount: Int,
        featureCount: Int
    ): List<EntityRecordRedis> {
        val result = ArrayList<EntityRecordRedis>(entityCount)

        for (entityIndex in 1..entityCount) {
            val valuesPayload = mutableMapOf<String, FeatureTypeSingle>()

            for (featureIndex in 1..featureCount) {
                valuesPayload["f$featureIndex"] = FeatureTypeSingle.newBuilder()
                    .setIntValue(entityIndex * 100 + featureIndex)
                    .build()
            }

            val redisPayload = RedisPayload.newBuilder()
                .putAllFeatures(valuesPayload)
                .build()
                .toByteString()

            result += EntityRecordRedis.newBuilder()
                .setKey("entity:patient$entityIndex")
                .setValue(redisPayload)
                .build()
        }

        return result
    }

    private fun runTimed(
        seconds: Long,
        concurrency: Int,
        block: () -> Unit
    ) {
        val pool = Executors.newFixedThreadPool(concurrency)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)

        val tasks = (1..concurrency).map {
            Callable {
                while (System.nanoTime() < deadline) {
                    block()
                }
            }
        }

        pool.invokeAll(tasks)
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)
    }

    private fun measureTimed(
        seconds: Long,
        concurrency: Int,
        block: () -> Unit
    ): BenchResult {
        val pool = Executors.newFixedThreadPool(concurrency)
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(seconds)

        val tasks = (1..concurrency).map {
            Callable {
                val latencies = ArrayList<Long>()
                while (System.nanoTime() < deadline) {
                    val start = System.nanoTime()
                    block()
                    val end = System.nanoTime()
                    latencies += (end - start)
                }
                latencies
            }
        }

        val futures = pool.invokeAll(tasks)
        pool.shutdown()
        pool.awaitTermination(1, TimeUnit.MINUTES)

        val allLatenciesNs = futures.flatMap { it.get() }.sorted()
        val count = allLatenciesNs.size.toLong()

        require(count > 0) { "No measurements collected" }

        val avgNs = allLatenciesNs.average()
        val minNs = allLatenciesNs.first().toDouble()
        val maxNs = allLatenciesNs.last().toDouble()

        return BenchResult(
            count = count,
            rps = count.toDouble() / seconds.toDouble(),
            avgMs = nsToMs(avgNs),
            minMs = nsToMs(minNs),
            maxMs = nsToMs(maxNs),
            p50Ms = percentileMs(allLatenciesNs, 50.0),
            p95Ms = percentileMs(allLatenciesNs, 95.0),
            p99Ms = percentileMs(allLatenciesNs, 99.0)
        )
    }

    private fun percentileMs(valuesNs: List<Long>, percentile: Double): Double {
        require(valuesNs.isNotEmpty()) { "Empty latency list" }
        val rank = ceil((percentile / 100.0) * valuesNs.size).toInt().coerceAtLeast(1)
        val index = (rank - 1).coerceIn(valuesNs.indices)
        return nsToMs(valuesNs[index].toDouble())
    }

    private fun nsToMs(ns: Double): Double = ns / 1_000_000.0

    private fun printResult(config: Config, result: BenchResult) {
        println()
        println("CUSTOM STORAGE BENCHMARK RESULT")
        println("mode=${config.mode}")
        println("entityCount=${config.entityCount}")
        println("featureCount=${config.featureCount}")
        println("concurrency=${config.concurrency}")
        println("count=${result.count}")
        println("rps=${"%.2f".format(result.rps)}")
        println("avgMs=${"%.2f".format(result.avgMs)}")
        println("minMs=${"%.2f".format(result.minMs)}")
        println("maxMs=${"%.2f".format(result.maxMs)}")
        println("p50Ms=${"%.2f".format(result.p50Ms)}")
        println("p95Ms=${"%.2f".format(result.p95Ms)}")
        println("p99Ms=${"%.2f".format(result.p99Ms)}")
    }
}