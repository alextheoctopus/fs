package com.itmo.featurestore

import com.proto.api.Api
import com.proto.api.Api.PutResponse
import com.proto.api.FeatureStoreGrpc
import com.itmo.featurestore.mapper.RedisRequestMapper
import com.itmo.featurestore.storage.RedisStorage
import io.grpc.stub.StreamObserver

class FeatureStoreService : FeatureStoreGrpc.FeatureStoreImplBase() {

    private val redisStorage = RedisStorage()

    override fun put(request: Api.PutRequest, responseObserver: StreamObserver<PutResponse>) {
        val mapper = RedisRequestMapper()
        val records = mapper.putRequestMapParser(request)

        redisStorage.saveAll(records)

        val response = PutResponse.newBuilder()
            .setWrittenEntities(records.size)
            .setSuccess(true)
            .build()

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }

    override fun get(
        request: Api.GetRequest,
        responseObserver: StreamObserver<Api.GetResponse>
    ) {
        val mapper = RedisRequestMapper()
        val records = mapper.getRequestMapParser(request)
        for (key in records) println(redisStorage.getPayload(key))
        val response = Api.GetResponse.newBuilder()
            .addAllEntityKeys(records)
//            .putAllColumns()
            .build()
        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
}