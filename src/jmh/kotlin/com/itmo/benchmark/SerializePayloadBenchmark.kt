package com.itmo.benchmark

import com.proto.api.Api
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 20, time = 8)
@Fork(3)
open class SerializePayloadBenchmark {

    @State(Scope.Thread)
    open class BenchmarkState {

        @Param("1", "10", "100")
        lateinit var featureCount: String

        lateinit var features: Map<String, Api.FeatureTypeSingle>

        @Setup(Level.Trial)
        fun setup() {
            val count = featureCount.toInt()

            val map = mutableMapOf<String, Api.FeatureTypeSingle>()

            for (i in 1..count) {
                map["f$i"] = Api.FeatureTypeSingle.newBuilder()
                    .setIntValue(i)
                    .build()
            }

            features = map
        }
    }

//    @Benchmark
//    fun serialize(state: BenchmarkState): ByteArray {
//        val payload = Api.RedisPayload.newBuilder()
//            .putAllFeatures(state.features)
//            .build()
//
//        return payload.toByteArray()
//    }
}
