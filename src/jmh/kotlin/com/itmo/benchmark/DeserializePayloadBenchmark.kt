package com.itmo.benchmark

import com.proto.api.Api
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 20, time = 8)
@Fork(3)
open class DeserializePayloadBenchmark {

    @State(Scope.Thread)
    open class BenchmarkState {

        @Param("1", "10", "100")
        lateinit var featureCount: String

        lateinit var bytes: ByteArray

        @Setup(Level.Trial)
        fun setup() {
            val features = featureCount.toInt()

            val payloadBuilder = Api.RedisPayload.newBuilder()

            for (i in 1..features) {
                payloadBuilder.putFeatures(
                    "f$i",
                    Api.FeatureTypeSingle.newBuilder()
                        .setIntValue(i)
                        .build()
                )
            }

            //
            bytes = payloadBuilder.build().toByteArray()
        }
    }

//    @Benchmark
//    fun deserialize(state: BenchmarkState): Api.RedisPayload {
//        return Api.RedisPayload.parseFrom(state.bytes)
//    }
}