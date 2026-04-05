package com.itmo.featurestore.mapper

import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteStringUtf8
import com.proto.api.Api
import com.proto.api.Api.EntityRecordRedis

class RedisRequestMapper(request: Api.PutRequest) {

    private val entityKeys = request.entityKeysList
    private val entitiesCount = request.entityKeysCount;
    private val columnsMap = request.columnsMap
    private val entityFeatures = columnsMap.keys

    fun putRequestMapParser(): MutableList<EntityRecordRedis> {


        var result = mutableListOf<EntityRecordRedis>()
        for (i in 0..<entitiesCount) {


            var id = entityKeys[i]
            val key = "entity:$id"
            var features: ByteArray = byteArrayOf()
            for (feature in entityFeatures) {
                if (columnsMap[feature] == null) {
                    println("Feature is not found in columnsMap. Skip"); continue
                }

                if (columnsMap[feature]!!.valuesList.isEmpty()) {
                    println("Values for feature are not provided. Skip"); continue
                }

                val byFeature = columnsMap[feature]?.valuesList?.get(0)

                val case = byFeature?.valuesCase

                val value = when (case) {
                    Api.FeatureType.ValuesCase.STRING_VALUES -> {
                        val values = byFeature.stringValues.valuesList
                        if (i < values.size) values[i] else "missing".toByteArray()
                    }

                    Api.FeatureType.ValuesCase.INT_VALUES -> {
                        val values = byFeature.intValues.valuesList
                        if (i < values.size) values[i] else "missing".toByteArray()
                    }

                    Api.FeatureType.ValuesCase.LONG_VALUES -> {
                        val values = byFeature.longValues.valuesList
                        if (i < values.size) values[i] else "missing".toByteArray()
                    }

                    Api.FeatureType.ValuesCase.FLOAT_VALUES -> {
                        val values = byFeature.floatValues.valuesList
                        if (i < values.size) values[i] else "missing".toByteArray()
                    }

                    Api.FeatureType.ValuesCase.DOUBLE_VALUES -> {
                        val values = byFeature.doubleValues.valuesList
                        if (i < values.size) values[i] else "missing".toByteArray()
                    }

                    Api.FeatureType.ValuesCase.BOOL_VALUES -> {
                        val values = byFeature.boolValues.valuesList
                        if (i < values.size) values[i] else "missing".toByteArray()
                    }

                    Api.FeatureType.ValuesCase.BYTES_VALUES -> {
                        val values = byFeature.bytesValues.valuesList
                        if (i < values.size) values[i].toByteArray() else "missing".toByteArray()
                    }

                    Api.FeatureType.ValuesCase.VALUES_NOT_SET -> {
                        println("Type is not set for feature $feature. Skip")
                        continue
                    }

                    null -> {
                        println("Feature object is null for $feature. Skip")
                        continue
                    }
                }


            }
//            result.add(EntityRecordRedis.newBuilder().setKey(key).setValue(ByteString.copyFromUtf8(features)).build())
        }
        return result
    }

//    fun createEntityRecordRedis(key: String, value: Any): EntityRecordRedis {
//
//
////        return EntityRecordRedis.newBuilder().setKey(key)
//    }

}