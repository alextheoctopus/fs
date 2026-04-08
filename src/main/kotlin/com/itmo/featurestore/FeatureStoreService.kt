package com.itmo.featurestore

import com.google.apps.card.v1.Columns.Column
import com.proto.api.Api
import com.proto.api.Api.PutResponse
import com.proto.api.FeatureStoreGrpc
import com.itmo.featurestore.mapper.RedisRequestMapper
import com.itmo.featurestore.storage.RedisStorage
import com.proto.api.Api.EntityRecordRedis
import com.proto.api.Api.FeatureColumn
import com.proto.api.Api.GetResponse
import com.proto.api.Api.RedisPayload
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
        responseObserver: StreamObserver<GetResponse>
    ) {

        val mapper = RedisRequestMapper()
        val response = mapper.getRequestKeysParser(request)

        responseObserver.onNext(response)
        responseObserver.onCompleted()
    }
}