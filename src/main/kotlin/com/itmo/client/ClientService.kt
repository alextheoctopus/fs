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
        val request = GetRequest.newBuilder().addAllEntityKeys(mutableListOf("patient1", "patient2")).addAllFeatures(
            mutableListOf("age", "height")
        ).build()
        val response = stub.get(request)
        println(response.entityKeysCount)
        channel.shutdown()
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
}

fun main() {
    val clientService = ClientService()
    clientService.get()

}