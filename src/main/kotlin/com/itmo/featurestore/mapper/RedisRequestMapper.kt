package com.itmo.featurestore.mapper

import com.google.protobuf.ByteString
import com.proto.api.Api
import com.proto.api.Api.EntityRecordRedis
import com.proto.api.Api.FeatureTypeSingle
import com.proto.api.Api.RedisPayload

class RedisRequestMapper(request: Api.PutRequest) {

    private val entityKeys = request.entityKeysList
    private val entitiesCount = request.entityKeysCount
    private val columnsMap = request.columnsMap
    private val entityFeatures = columnsMap.keys

    sealed class ValueType {
        data class StringValue(val value: String) : ValueType()
        data class IntValue(val value: Int) : ValueType()
        data class LongValue(val value: Long) : ValueType()
        data class DoubleValue(val value: Double) : ValueType()
        data class FloatValue(val value: Float) : ValueType()
        data class BoolValue(val value: Boolean) : ValueType()
        data class BytesValue(val value: ByteString) : ValueType()
    }

    fun putRequestMapParser(): MutableList<EntityRecordRedis> {
        val result: MutableList<EntityRecordRedis> = mutableListOf()

        for (i in 0..<entitiesCount) {
            val kotlinFeatureValue: MutableMap<String, ValueType> = mutableMapOf()

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
                            kotlinFeatureValue[feature] = ValueType.StringValue(values[i])
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.INT_VALUES -> {
                        val values = byFeature.intValues.valuesList
                        if (i < values.size) {
                            kotlinFeatureValue[feature] = ValueType.IntValue(values[i])
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.LONG_VALUES -> {
                        val values = byFeature.longValues.valuesList
                        if (i < values.size) {
                            kotlinFeatureValue[feature] = ValueType.LongValue(values[i])
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.FLOAT_VALUES -> {
                        val values = byFeature.floatValues.valuesList
                        if (i < values.size) {
                            kotlinFeatureValue[feature] = ValueType.FloatValue(values[i])
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.DOUBLE_VALUES -> {
                        val values = byFeature.doubleValues.valuesList
                        if (i < values.size) {
                            kotlinFeatureValue[feature] = ValueType.DoubleValue(values[i])
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.BOOL_VALUES -> {
                        val values = byFeature.boolValues.valuesList
                        if (i < values.size) {
                            kotlinFeatureValue[feature] = ValueType.BoolValue(values[i])
                        } else {
                            continue
                        }
                    }

                    Api.FeatureType.ValuesCase.BYTES_VALUES -> {
                        val values = byFeature.bytesValues.valuesList
                        if (i < values.size) {
                            kotlinFeatureValue[feature] = ValueType.BytesValue(values[i])
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

            val valuesPayload: MutableMap<String, FeatureTypeSingle> = mutableMapOf()
            for ((feature, value) in kotlinFeatureValue) {
                val valueProto = when (value) {
                    is ValueType.StringValue -> FeatureTypeSingle.newBuilder().setStringValue(value.value).build()
                    is ValueType.IntValue -> FeatureTypeSingle.newBuilder().setIntValue(value.value).build()
                    is ValueType.LongValue -> FeatureTypeSingle.newBuilder().setLongValue(value.value).build()
                    is ValueType.DoubleValue -> FeatureTypeSingle.newBuilder().setDoubleValue(value.value).build()
                    is ValueType.FloatValue -> FeatureTypeSingle.newBuilder().setFloatValue(value.value).build()
                    is ValueType.BoolValue -> FeatureTypeSingle.newBuilder().setBoolValue(value.value).build()
                    is ValueType.BytesValue -> FeatureTypeSingle.newBuilder().setBytesValue(value.value).build()
                }
                valuesPayload[feature] = valueProto
            }

            val redisPayload = RedisPayload.newBuilder()
                .putAllFeatures(valuesPayload)
                .build()
                .toByteString()

            result.add(
                EntityRecordRedis.newBuilder()
                    .setKey(entityKeys[i])
                    .setValue(redisPayload)
                    .build()
            )
        }

        return result
    }
}