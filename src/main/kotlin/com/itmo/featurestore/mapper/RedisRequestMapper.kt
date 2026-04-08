package com.itmo.featurestore.mapper

import com.google.protobuf.ByteString
import com.itmo.featurestore.storage.RedisStorage
import com.proto.api.Api
import com.proto.api.Api.EntityRecordRedis
import com.proto.api.Api.FeatureColumn
import com.proto.api.Api.FeatureType
import com.proto.api.Api.FeatureTypeSingle
import com.proto.api.Api.GetRequest
import com.proto.api.Api.GetResponse
import com.proto.api.Api.RedisPayload

class RedisRequestMapper() {

    /*getRequest содержит в себе
     → массив ключей
     → массив фичей

    Мы должны извлечь ключи → привести к виду  “entity:$key”
    потом получить из Redis байтовые строки по этим ключам,
    распарсить и отдать запрашиваемые фичи
    */
    fun getRequestKeysParser(request: GetRequest): GetResponse {
        val kotlinEntityValue = loadFromRedis(request)
        return buildGetResponse(request, kotlinEntityValue)
    }

    private fun loadFromRedis(request: GetRequest): MutableMap<String, RedisPayload> {
        val keysList = request.entityKeysList;
        val storage = RedisStorage()
        var kotlinEntityValue: MutableMap<String, RedisPayload> = mutableMapOf()
        for (key in keysList) {
            if (key.isEmpty()) {
                println("$key is empty")
                continue
            }
            val payload = storage.getPayload("entity:${key}") ?: continue
            kotlinEntityValue[key] = payload
        }
        return kotlinEntityValue
    }

    fun buildGetResponse(
        request: GetRequest,
        kotlinEntityValue: MutableMap<String, RedisPayload>
    ): GetResponse {
        //Фичи из запроса
        val requestedFeatures = request.featuresList.filter { it.isNotEmpty() }
        val resultColumns = mutableMapOf<String, FeatureColumn>()

        for (featureName in requestedFeatures) {
            val intValues = mutableListOf<Int>()
            val longValues = mutableListOf<Long>()
            val floatValues = mutableListOf<Float>()
            val doubleValues = mutableListOf<Double>()
            val stringValues = mutableListOf<String>()
            val boolValues = mutableListOf<Boolean>()
            val bytesValues = mutableListOf<com.google.protobuf.ByteString>()

            var detectedType: Api.FeatureTypeSingle.ValuesCase? = null
            for ((_, payload) in kotlinEntityValue) {
                //Извлекли value из фичи
                val featureValue = payload.featuresMap[featureName] ?: continue
                when (featureValue.valuesCase) {
                    //Формируем массив по типам
                    FeatureTypeSingle.ValuesCase.STRING_VALUE -> {
                        detectedType = FeatureTypeSingle.ValuesCase.STRING_VALUE
                        stringValues.add(featureValue.stringValue)
                    }

                    FeatureTypeSingle.ValuesCase.INT_VALUE -> {
                        detectedType = FeatureTypeSingle.ValuesCase.INT_VALUE
                        intValues.add(featureValue.intValue)
                    }

                    FeatureTypeSingle.ValuesCase.LONG_VALUE -> {
                        detectedType = FeatureTypeSingle.ValuesCase.LONG_VALUE
                        longValues.add(featureValue.longValue)
                    }

                    FeatureTypeSingle.ValuesCase.FLOAT_VALUE -> {
                        detectedType = FeatureTypeSingle.ValuesCase.FLOAT_VALUE
                        floatValues.add(featureValue.floatValue)
                    }

                    FeatureTypeSingle.ValuesCase.DOUBLE_VALUE -> {
                        detectedType = FeatureTypeSingle.ValuesCase.DOUBLE_VALUE
                        doubleValues.add(featureValue.doubleValue)
                    }

                    FeatureTypeSingle.ValuesCase.BOOL_VALUE -> {
                        detectedType = FeatureTypeSingle.ValuesCase.BOOL_VALUE
                        boolValues.add(featureValue.boolValue)
                    }

                    FeatureTypeSingle.ValuesCase.BYTES_VALUE -> {
                        detectedType = FeatureTypeSingle.ValuesCase.BYTES_VALUE
                        bytesValues.add(featureValue.bytesValue)
                    }

                    FeatureTypeSingle.ValuesCase.VALUES_NOT_SET, null -> {
                        continue
                    }
                }
            }
            //Приводим тип FeatureTypeSingle → FeatureColumn
            val column = when (detectedType) {
                FeatureTypeSingle.ValuesCase.INT_VALUE -> {
                    FeatureColumn.newBuilder()
                        .addValues(
                            FeatureType.newBuilder()
                                .setIntValues(
                                    Api.IntColumn.newBuilder()
                                        .addAllValues(intValues)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }

                FeatureTypeSingle.ValuesCase.LONG_VALUE -> {
                    FeatureColumn.newBuilder()
                        .addValues(
                            FeatureType.newBuilder()
                                .setLongValues(
                                    Api.LongColumn.newBuilder()
                                        .addAllValues(longValues)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }

                FeatureTypeSingle.ValuesCase.FLOAT_VALUE -> {
                    FeatureColumn.newBuilder()
                        .addValues(
                            FeatureType.newBuilder()
                                .setFloatValues(
                                    Api.FloatColumn.newBuilder()
                                        .addAllValues(floatValues)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }

                FeatureTypeSingle.ValuesCase.DOUBLE_VALUE -> {
                    FeatureColumn.newBuilder()
                        .addValues(
                            FeatureType.newBuilder()
                                .setDoubleValues(
                                    Api.DoubleColumn.newBuilder()
                                        .addAllValues(doubleValues)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }

                FeatureTypeSingle.ValuesCase.STRING_VALUE -> {
                    FeatureColumn.newBuilder()
                        .addValues(
                            FeatureType.newBuilder()
                                .setStringValues(
                                    Api.StringColumn.newBuilder()
                                        .addAllValues(stringValues)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }

                FeatureTypeSingle.ValuesCase.BOOL_VALUE -> {
                    FeatureColumn.newBuilder()
                        .addValues(
                            FeatureType.newBuilder()
                                .setBoolValues(
                                    Api.BoolColumn.newBuilder()
                                        .addAllValues(boolValues)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }

                FeatureTypeSingle.ValuesCase.BYTES_VALUE -> {
                    FeatureColumn.newBuilder()
                        .addValues(
                            FeatureType.newBuilder()
                                .setBytesValues(
                                    Api.BytesColumn.newBuilder()
                                        .addAllValues(bytesValues)
                                        .build()
                                )
                                .build()
                        )
                        .build()
                }

                FeatureTypeSingle.ValuesCase.VALUES_NOT_SET,
                null -> continue

            }

            resultColumns[featureName] = column
        }

        return GetResponse.newBuilder()
            .addAllEntityKeys(kotlinEntityValue.keys)
            .putAllColumns(resultColumns)
            .build()
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