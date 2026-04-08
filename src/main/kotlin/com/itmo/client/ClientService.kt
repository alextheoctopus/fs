package com.itmo.client

import com.itmo.featurestore.FeatureStoreService
import com.proto.api.Api
import com.proto.api.Api.FeatureColumn
import com.proto.api.Api.GetRequest
import com.proto.api.Api.IntColumn
import com.proto.api.Api.PutRequest
import com.proto.api.FeatureStoreGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder

class ClientService {
    private val channel = ManagedChannelBuilder
        .forAddress("localhost", 9090)
        .usePlaintext()
        .build()

    private val stub = FeatureStoreGrpc.newBlockingStub(channel)

    fun get() {
        val request = GetRequest.newBuilder()
            .addAllEntityKeys(mutableListOf("patient1", "patient2"))
            .addAllFeatures(mutableListOf("f1", "f2"))
            .build()

        val response = stub.get(request)
        println(response)
    }

    fun put(/*сюда можно аргументы, которые entities,features, values*/) {


        val request = PutRequest.newBuilder()
            .addAllEntityKeys(mutableListOf("patient1", "patient2", "patient3"))
            .putAllColumns(
                mutableMapOf(
                    Pair(
                        "age",
                        FeatureColumn.newBuilder()
                            .addAllValues(
                                mutableListOf(
                                    Api.FeatureType.newBuilder().setIntValues(
                                        IntColumn.newBuilder().addAllValues(
                                            mutableListOf(13, 22, 101)
                                        )
                                    ).build()
                                )
                            )
                            .build()
                    ),
                    Pair(
                        "height",
                        FeatureColumn.newBuilder()
                            .addAllValues(
                                mutableListOf(
                                    Api.FeatureType.newBuilder().setIntValues(
                                        IntColumn.newBuilder().addAllValues(
                                            mutableListOf(168, 170, 165)
                                        )
                                    ).build()
                                )
                            )
                            .build()
                    ),
                    Pair(
                        "empty_values",
                        FeatureColumn.newBuilder()
                            .addAllValues(
                                mutableListOf(
                                    Api.FeatureType.newBuilder().setIntValues(
                                        IntColumn.newBuilder().addAllValues(
                                            mutableListOf(168, 170)
                                        )
                                    ).build()
                                )
                            )
                            .build()
                    )
                )
            ).build()
        val response = stub.put(request)

        println(response.writtenEntities)

        channel.shutdown()

    }

    fun preload(entityCount: Int, featureCount: Int) {
        val entityKeys = (1..entityCount).map { "patient$it" }

        val columns = mutableMapOf<String, FeatureColumn>()

        for (featureIndex in 1..featureCount) {
            val featureName = "f$featureIndex"

            val values = (1..entityCount).map { entityIndex ->
                entityIndex * 100 + featureIndex
            }

            val column = FeatureColumn.newBuilder()
                .addAllValues(
                    mutableListOf(
                        Api.FeatureType.newBuilder()
                            .setIntValues(
                                IntColumn.newBuilder()
                                    .addAllValues(values)
                                    .build()
                            )
                            .build()
                    )
                )
                .build()

            columns[featureName] = column
        }

        val request = PutRequest.newBuilder()
            .addAllEntityKeys(entityKeys)
            .putAllColumns(columns)
            .build()

        val response = stub.put(request)
        println("Preload completed. Written entities = ${response.writtenEntities}")
    }

    fun shutdown() {
        channel.shutdown()
    }
}

fun main() {
    val clientService = ClientService()
    clientService.get()
    clientService.shutdown()
}