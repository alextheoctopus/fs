package com.itmo.featurestore

import io.grpc.Server
import io.grpc.ServerBuilder


class FeatureStoreServer(
    private val port: Int, private val server: Server =
        ServerBuilder.forPort(port)
            .addService(FeatureStoreService()).build()
) {
    fun start() {
        server.start()
        println("Server started")
    }

    fun awaitTermination() {
        server.awaitTermination()
    }
}

fun main() {
    val port = 9090
    val server = FeatureStoreServer(port)
    server.start()
    server.awaitTermination()
}