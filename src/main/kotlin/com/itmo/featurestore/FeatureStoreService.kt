package com.itmo.featurestore

import com.proto.api.Api
import com.proto.api.Api.PutResponse
import com.proto.api.FeatureStoreGrpc
import io.grpc.stub.StreamObserver
import com.itmo.featurestore.mapper.RedisRequestMapper

class FeatureStoreService : FeatureStoreGrpc.FeatureStoreImplBase() {
    override fun put(request: Api.PutRequest, responseObserver: StreamObserver<PutResponse>) {

        val mapper = RedisRequestMapper(request)
        val redisData = mapper.putRequestMapParser()
        println(redisData)
//        val jedis = Jedis("localhost", 6379)


//Формирование ответа на put
        val response = PutResponse.newBuilder().setWrittenEntities(request.entityKeysCount).setSuccess(true).build()

//Ответ от сервера клиенту
        responseObserver.onNext(response)
        responseObserver.onCompleted()

    }

    override fun get(request: Api.GetRequest?, responseObserver: StreamObserver<Api.GetResponse>?) {
        super.get(request, responseObserver)
    }
}