package com.itmo.benchmark

import com.itmo.featurestore.mapper.RedisRequestMapper
import com.proto.api.Api
import com.proto.api.Api.FeatureColumn
import com.proto.api.Api.IntColumn
import com.proto.api.Api.PutRequest
import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 10, time = 2)
@Measurement(iterations = 20, time = 5)
@Fork(3)
//bзмеряет putRequestMapParser() из RedisRequestMapper
open class PutRequestMapperBenchmark {

    @State(Scope.Thread)
    open class BenchmarkState {

        @Param("1", "10", "100", "1000")
        lateinit var entityCount: String

        @Param("2", "10")
        lateinit var featureCount: String

        lateinit var mapper: RedisRequestMapper
        lateinit var request: PutRequest

        @Setup(Level.Trial)
        fun setup() {
            mapper = RedisRequestMapper()

            val entities = entityCount.toInt()
            val features = featureCount.toInt()

            val entityKeys = (1..entities).map { "patient$it" }
            val columns = mutableMapOf<String, FeatureColumn>()

            for (f in 1..features) {
                val featureName = "f$f"
                val values = (1..entities).map { e -> e * 1000 + f }

                val column = FeatureColumn.newBuilder()
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

                columns[featureName] = column
            }

            request = PutRequest.newBuilder()
                .addAllEntityKeys(entityKeys)
                .putAllColumns(columns)
                .build()
        }
    }

    @Benchmark
    fun mapPutRequest(state: BenchmarkState): MutableList<Api.EntityRecordRedis> {
        return state.mapper.putRequestMapParser(state.request)
    }
}