package com.itmo.benchmark

import com.proto.api.Api
import com.proto.api.Api.FeatureColumn
import com.proto.api.Api.GetRequest
import com.proto.api.Api.IntColumn
import com.proto.api.Api.PutRequest
import com.proto.api.FeatureStoreGrpc
import io.grpc.ManagedChannelBuilder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.ceil

object FullGetBenchmark {
    data class Config(
        val entityCount: Int,
        val featureCount: Int,
        val concurrency: Int,
        val warmupSeconds: Long = 10,
        val measureSeconds: Long = 30,
        val host: String = "localhost",
        val port: Int = 9090
    )

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
        val config = parseArgs(args)

        val channel = ManagedChannelBuilder
            .forAddress(config.host, config.port)
            .usePlaintext()
            .build()

        try {
            val stub = FeatureStoreGrpc.newBlockingStub(channel)
            val putRequest = buildPutRequest(config.entityCount, config.featureCount)
            val getRequest = buildGetRequest(config.entityCount, config.featureCount)

            println("Preloading data through gRPC Put...")
            val putResponse = stub.put(putRequest)
            println("Preload completed. Written entities = ${putResponse.writtenEntities}")

            println(
                "Warmup FULL GET: entities=${config.entityCount}, " +
                    "features=${config.featureCount}, c=${config.concurrency}, " +
                    "warmup=${config.warmupSeconds}s"
            )

            runTimed(config.warmupSeconds, config.concurrency) {
                val response = stub.get(getRequest)
                checkResponse(response.entityKeysCount, config.entityCount)
            }

            println(
                "Measure FULL GET: entities=${config.entityCount}, " +
                    "features=${config.featureCount}, c=${config.concurrency}, " +
                    "duration=${config.measureSeconds}s"
            )

            val result = measureTimed(config.measureSeconds, config.concurrency) {
                val response = stub.get(getRequest)
                checkResponse(response.entityKeysCount, config.entityCount)
            }

            printResult(config, result)
        } finally {
            channel.shutdown()
            channel.awaitTermination(5, TimeUnit.SECONDS)
        }
    }

    private fun parseArgs(args: Array<String>): Config {
        if (args.size < 3) {
            error(
                "Usage: <entityCount> <featureCount> <concurrency> " +
                    "[warmupSeconds] [measureSeconds] [host] [port]"
            )
        }

        return Config(
            entityCount = args[0].toInt(),
            featureCount = args[1].toInt(),
            concurrency = args[2].toInt(),
            warmupSeconds = args.getOrNull(3)?.toLong() ?: 10,
            measureSeconds = args.getOrNull(4)?.toLong() ?: 30,
            host = args.getOrNull(5) ?: "localhost",
            port = args.getOrNull(6)?.toInt() ?: 9090
        )
    }

    private fun buildPutRequest(entityCount: Int, featureCount: Int): PutRequest {
        val entityKeys = (1..entityCount).map { "patient$it" }
        val columns = mutableMapOf<String, FeatureColumn>()

        for (featureIndex in 1..featureCount) {
            val values = (1..entityCount).map { entityIndex ->
                entityIndex * 100 + featureIndex
            }

            columns["f$featureIndex"] = FeatureColumn.newBuilder()
                .addValues(
                    Api.FeatureType.newBuilder()
                        .setIntValues(
                            IntColumn.newBuilder()
                                .addAllValues(values)
                                .build()
                        )
                        .build()
                )
                .build()
        }

        return PutRequest.newBuilder()
            .addAllEntityKeys(entityKeys)
            .putAllColumns(columns)
            .build()
    }

    private fun buildGetRequest(entityCount: Int, featureCount: Int): GetRequest {
        return GetRequest.newBuilder()
            .addAllEntityKeys((1..entityCount).map { "patient$it" })
            .addAllFeatures((1..featureCount).map { "f$it" })
            .build()
    }

    private fun checkResponse(actualEntityCount: Int, expectedEntityCount: Int) {
        if (actualEntityCount != expectedEntityCount) {
            error("Unexpected entity count: $actualEntityCount, expected: $expectedEntityCount")
        }
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
        println("FULL GET BENCHMARK RESULT")
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
