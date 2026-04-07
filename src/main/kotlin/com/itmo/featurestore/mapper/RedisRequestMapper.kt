package com.itmo.featurestore.mapper

import com.google.protobuf.ByteString
import com.proto.api.Api
import com.proto.api.Api.EntityRecordRedis
import com.proto.api.Api.FeatureTypeSingle
import com.proto.api.Api.GetRequest
import com.proto.api.Api.PutRequest
import com.proto.api.Api.RedisPayload

class RedisRequestMapper() {

    fun getResonseMapParser(request: Api.GetRequest) {

    }

    fun getRequestMapParser(request: GetRequest): MutableList<String> {
        val result: MutableList<String> = mutableListOf()
        val keysList = request.entityKeysList;
        println(keysList)

        for (i in 0..<keysList.size) {
            if (keysList[i].isEmpty()) {
                println("Key on index=$i is empty")
                continue
            }
            result.add("entity:${keysList[i]}")//Как записано в Redis
        }
        return result
    }

    fun putRequestMapParser(request: Api.PutRequest): MutableList<EntityRecordRedis> {
        val entityKeys = request.entityKeysList
        val entitiesCount = request.entityKeysCount
        val columnsMap = request.columnsMap
        val entityFeatures = columnsMap.keys
        val result: MutableList<EntityRecordRedis> = mutableListOf()


        for (i in 0..<entitiesCount) {
            val valuesPayload: MutableMap<String, FeatureTypeSingle> = mutableMapOf()
            for (feature in entityFeatures) {
                if (columnsMap[feature] == null) {
                    println("Feature is not found in columnsMap. Skip")
                    continue
                }

                if (columnsMap[feature]!!.valuesList.isEmpty()) {
                    println("Values for feature are not provided. Skip")
                    continue
                }

                val byFeature = columnsMap[feature]?.valuesList?.get(0)
                val case = byFeature?.valuesCase

                when (case) {
                    Api.FeatureType.ValuesCase.STRING_VALUES -> {
                        val values = byFeature.stringValues.valuesList
                        if (i < values.size) {
                            valuesPayload[feature] = FeatureTypeSingle.newBuilder().setStringValue(values[i]).build()
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.INT_VALUES -> {
                        val values = byFeature.intValues.valuesList
                        if (i < values.size) {
                            valuesPayload[feature] = FeatureTypeSingle.newBuilder().setIntValue(values[i]).build()
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.LONG_VALUES -> {
                        val values = byFeature.longValues.valuesList
                        if (i < values.size) {
                            valuesPayload[feature] = FeatureTypeSingle.newBuilder().setLongValue(values[i]).build()
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.FLOAT_VALUES -> {
                        val values = byFeature.floatValues.valuesList
                        if (i < values.size) {
                            valuesPayload[feature] = FeatureTypeSingle.newBuilder().setFloatValue(values[i]).build()
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.DOUBLE_VALUES -> {
                        val values = byFeature.doubleValues.valuesList
                        if (i < values.size) {
                            valuesPayload[feature] = FeatureTypeSingle.newBuilder().setDoubleValue(values[i]).build()
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.BOOL_VALUES -> {
                        val values = byFeature.boolValues.valuesList
                        if (i < values.size) {
                            valuesPayload[feature] = FeatureTypeSingle.newBuilder().setBoolValue(values[i]).build()
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.BYTES_VALUES -> {
                        val values = byFeature.bytesValues.valuesList
                        if (i < values.size) {
                            valuesPayload[feature] = FeatureTypeSingle.newBuilder().setBytesValue(values[i]).build()
                        } else {
                            continue
                        }
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


            val redisPayload = RedisPayload.newBuilder()
                .putAllFeatures(valuesPayload)
                .build()
                .toByteString()

            result.add(
                EntityRecordRedis.newBuilder()
                    .setKey("entity:${entityKeys[i]}")
                    .setValue(redisPayload)
                    .build()
            )
        }

        return result
    }
}