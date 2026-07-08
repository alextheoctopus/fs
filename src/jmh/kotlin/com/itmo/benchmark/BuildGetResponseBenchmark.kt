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
@Warmup(iterations = 3, time = 10)
@Measurement(iterations = 5, time = 10)
@Fork(1)
open class BuildGetResponseBenchmark {

    @State(Scope.Thread)
    open class BenchmarkState {

        @Param("1x1", "1x10", "10x1", "10x10", "1000x10", "1000x100")
        lateinit var scenario: String

        lateinit var mapper: RedisRequestMapper
        lateinit var request: GetRequest
        lateinit var payloads: MutableMap<String, RedisPayload>

        @Setup(Level.Trial)
        fun setup() {
            mapper = RedisRequestMapper()

            val parts = scenario.split("x")
            val entities = parts[0].toInt()
            val features = parts[1].toInt()

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
