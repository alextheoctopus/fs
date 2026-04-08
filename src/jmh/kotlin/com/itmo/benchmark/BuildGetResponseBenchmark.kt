package com.itmo.benchmark

import com.itmo.featurestore.mapper.RedisRequestMapper
import com.proto.api.Api
import com.proto.api.Api.GetRequest
import com.proto.api.Api.GetResponse
import com.proto.api.Api.RedisPayload
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 20, time = 8)
@Fork(3)
open class BuildGetResponseBenchmark {

    @State(Scope.Thread)
    open class BenchmarkState {

        @Param("1000")
        lateinit var entityCount: String

        @Param("10")
        lateinit var featureCount: String

        lateinit var mapper: RedisRequestMapper
        lateinit var request: GetRequest
        lateinit var payloads: MutableMap<String, RedisPayload>

        @Setup(Level.Trial)
        fun setup() {
            mapper = RedisRequestMapper()

            val entities = entityCount.toInt()
            val features = featureCount.toInt()

            val featureNames = (1..features).map { "f$it" }

            request = GetRequest.newBuilder()
                .addAllEntityKeys((1..entities).map { "patient$it" })
                .addAllFeatures(featureNames)
                .build()

            payloads = mutableMapOf()

            for (i in 1..entities) {
                val payloadBuilder = Api.RedisPayload.newBuilder()

                for (f in 1..features) {
                    payloadBuilder.putFeatures(
                        "f$f",
                        Api.FeatureTypeSingle.newBuilder()
                            .setIntValue(i * 1000 + f)
                            .build()
                    )
                }

                payloads["patient$i"] = payloadBuilder.build()
            }
        }
    }

    @Benchmark
    fun buildResponse(state: BenchmarkState): GetResponse {
        return state.mapper.buildGetResponse(state.request, state.payloads)
    }
}