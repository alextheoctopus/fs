package com.custom.bench

import org.openjdk.jmh.annotations.*
import java.util.concurrent.TimeUnit
import com.custom.speedtest.SpeedTest.*
import org.openjdk.jmh.infra.Blackhole

import io.grpc.protobuf.ProtoUtils
import kotlin.random.Random


@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 7, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(2)
open class ProtoSerializationBenchmark {

    @Param("1000", "10000")
    var nEntities: Int = 0

    @Param("10", "50")
    var nFeatures: Int = 0

    private lateinit var rowMsg: PutRequestRowDict
    private lateinit var colMsg: PutRequestColumnDict

    // gRPC marshallers
    lateinit var rowMarshaller: io.grpc.MethodDescriptor.Marshaller<PutRequestRowDict>
    lateinit var colMarshaller: io.grpc.MethodDescriptor.Marshaller<PutRequestColumnDict>


    @AuxCounters(AuxCounters.Type.EVENTS)
    @State(Scope.Thread)
    open class MemCounters {
        var rowWireBytes: Long = 0
        var colWireBytes: Long = 0
        var rowByteArrayBytes: Long = 0
        var colByteArrayBytes: Long = 0
    }

    private var rowWireBytes: Int = 0
    private var colWireBytes: Int = 0
    private var rowByteArrayBytes: Int = 0
    private var colByteArrayBytes: Int = 0

    @Setup(Level.Trial)
    fun setup() {
        rowMsg = buildRowRequest(nEntities, nFeatures)
        colMsg = buildColumnRequest(nEntities, nFeatures)

        // gRPC protobuf marshaller
        rowMarshaller = ProtoUtils.marshaller(PutRequestRowDict.getDefaultInstance())
        colMarshaller = ProtoUtils.marshaller(PutRequestColumnDict.getDefaultInstance())


        // ---- MEMORY (BYTES) ----
        rowWireBytes = rowMsg.serializedSize
        colWireBytes = colMsg.serializedSize

        // реальный размер массива после сериализации
        rowByteArrayBytes = rowMsg.toByteArray().size
        colByteArrayBytes = colMsg.toByteArray().size
    }

    @Benchmark
    fun serializeRow(): ByteArray = rowMsg.toByteArray()

    @Benchmark
    fun serializeColumn(): ByteArray = colMsg.toByteArray()

    @Benchmark
    fun grpcMarshalRow(bh: Blackhole) {
        val stream = rowMarshaller.stream(rowMsg).readBytes()
        bh.consume(stream)
    }

    @Benchmark
    fun grpcMarshalColumn(bh: Blackhole) {
        val bytes = colMarshaller.stream(colMsg).readBytes()
        bh.consume(bytes)
    }

    @Benchmark
    fun reportMemory(c: MemCounters, bh: Blackhole) {
        c.rowWireBytes = rowWireBytes.toLong()
        c.colWireBytes = colWireBytes.toLong()
        c.rowByteArrayBytes = rowByteArrayBytes.toLong()
        c.colByteArrayBytes = colByteArrayBytes.toLong()

        // чтобы JIT не выкинул
        bh.consume(c.rowWireBytes)
        bh.consume(c.colWireBytes)
        bh.consume(c.rowByteArrayBytes)
        bh.consume(c.colByteArrayBytes)
    }

    private fun entityKey(i: Int) = "patient:$i"

    /** Ровно f фич. Первые базовые + дальше f0.. */
    private fun featureNames(f: Int): List<String> {
        val base = mutableListOf("name", "lastname", "bloodType", "age", "isFemale", "sugar")
        if (f <= base.size) return base.take(f)

        var idx = 0
        while (base.size < f) {
            base.add("f$idx")
            idx++
        }
        return base
    }

    private enum class Kind { STRING, INT32, INT64, FLOAT, DOUBLE, BOOL, BYTES }

    /** Фиксируем тип для каждой фичи (стабильно для row/column) */
    private fun kindOfFeature(feat: String): Kind = when (feat) {
        "name", "lastname", "bloodType" -> Kind.STRING
        "age" -> Kind.INT32
        "isFemale" -> Kind.BOOL
        "sugar" -> Kind.FLOAT
        // если хочешь реально добавлять long/double/bytes — можешь часть f* раскидать по этим типам:
        else -> Kind.STRING
    }

    /**
     * Генерит одно значение FeatureType для (feat, entityIndex).
     * ВАЖНО: детерминированно, чтобы row/column совпадали.
     * (Random можно использовать, но тогда лучше seed-ить по (feat,i), иначе сравнение будет «шумным».)
     */
    private fun featureValueFor(feat: String, i: Int): FeatureType {
        return when (kindOfFeature(feat)) {
            Kind.STRING -> {
                val v = when (feat) {
                    "name" -> "Name$i"
                    "lastname" -> "Last$i"
                    "bloodType" -> if (i % 2 == 0) "A+" else "O-"
                    else -> "$feat-val-$i"
                }
                FeatureType.newBuilder().setStringValue(v).build()
            }

            Kind.INT32 -> {
                // детерминированно (без Random): возраст 10..79
                val v = 10 + (i * 37 % 70)
                FeatureType.newBuilder().setIntValue(v).build()
            }

            Kind.BOOL -> {
                val v = (i % 2 == 0)
                FeatureType.newBuilder().setBoolValue(v).build()
            }

            Kind.FLOAT -> {
                // детерминированно float
                val v = ((i * 2654435761L) % 10_000L).toFloat() / 1000f  // 0..9.999
                FeatureType.newBuilder().setFloatValue(v).build()
            }

            Kind.INT64 -> {
                val v = i.toLong() * 1_000_000L
                FeatureType.newBuilder().setLongValue(v).build()
            }

            Kind.DOUBLE -> {
                val v = ((i * 11400714819323198485UL.toLong()) ushr 11).toDouble() / Long.MAX_VALUE
                FeatureType.newBuilder().setDoubleValue(v).build()
            }

            Kind.BYTES -> {
                val bs = com.google.protobuf.ByteString.copyFromUtf8("b:$feat:$i")
                FeatureType.newBuilder().setBytesValue(bs).build()
            }
        }
    }

    /**
     * RowDict:
     * PutRequestRowDict { repeated string features; repeated Entity rows; }
     * Entity { string entity_key; repeated FeatureType values; }
     */
    private fun buildRowRequest(n: Int, f: Int): PutRequestRowDict {
        val feats = featureNames(f)

        val req = PutRequestRowDict.newBuilder()
            .addAllFeatures(feats)

        for (i in 1..n) {
            val row = Entity.newBuilder()
                .setEntityKey(entityKey(i))

            // values[j] соответствует features[j]
            for (feat in feats) {
                row.addValues(featureValueFor(feat, i))
            }

            req.addRows(row)
        }

        return req.build()
    }

    /**
     * ColumnDict:
     * PutRequestColumnDict { repeated string entity_keys; repeated string features; repeated FeatureColumn columns; }
     * FeatureColumn.oneof { *Column repeated values }
     */
    private fun buildColumnRequest(n: Int, f: Int): PutRequestColumnDict {
        val feats = featureNames(f)
        val keys = (1..n).map { entityKey(it) }

        val req = PutRequestColumnDict.newBuilder()
            .addAllEntityKeys(keys)
            .addAllFeatures(feats)

        for (feat in feats) {
            val kind = kindOfFeature(feat)
            val col = FeatureColumn.newBuilder()

            when (kind) {
                Kind.STRING -> {
                    val values = (1..n).map { i -> featureValueFor(feat, i).stringValue }
                    col.setStringValues(StringColumn.newBuilder().addAllValues(values).build())
                }

                Kind.INT32 -> {
                    val values = (1..n).map { i -> featureValueFor(feat, i).intValue }
                    col.setIntValues(IntColumn.newBuilder().addAllValues(values).build())
                }

                Kind.INT64 -> {
                    val values = (1..n).map { i -> featureValueFor(feat, i).longValue }
                    col.setLongValues(LongColumn.newBuilder().addAllValues(values).build())
                }

                Kind.FLOAT -> {
                    val values = (1..n).map { i -> featureValueFor(feat, i).floatValue }
                    col.setFloatValues(FloatColumn.newBuilder().addAllValues(values).build())
                }

                Kind.DOUBLE -> {
                    val values = (1..n).map { i -> featureValueFor(feat, i).doubleValue }
                    col.setDoubleValues(DoubleColumn.newBuilder().addAllValues(values).build())
                }

                Kind.BOOL -> {
                    val values = (1..n).map { i -> featureValueFor(feat, i).boolValue }
                    col.setBoolValues(BoolColumn.newBuilder().addAllValues(values).build())
                }

                Kind.BYTES -> {
                    val values = (1..n).map { i -> featureValueFor(feat, i).bytesValue }
                    col.setBytesValues(BytesColumn.newBuilder().addAllValues(values).build())
                }
            }

            req.addColumns(col.build())
        }

        return req.build()
    }
}