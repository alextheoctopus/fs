plugins {
    kotlin("jvm") version "2.1.10"
    application
    id("com.google.protobuf") version "0.9.4"

    id("me.champeau.jmh") version "0.7.2"
}

group = "com.itmo.proto"
version = "Speed-test"

repositories {
    mavenCentral()
}

val grpcVersion = "1.69.0"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "3.25.5"

dependencies {
    implementation(kotlin("stdlib"))

    // gRPC runtime
    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")

    // Kotlin gRPC stubs (coroutines)
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    // Protobuf runtime
    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    // Needed at runtime (annotations used by generated code)
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    jmh("org.openjdk.jmh:jmh-core:1.37")
    jmh("org.openjdk.jmh:jmh-generator-annprocess:1.37")

    //Redis/Jedis
    implementation("redis.clients:jedis:5.1.0")
    testImplementation(kotlin("test"))
}
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
        }
        create("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().configureEach {
            plugins {
                maybeCreate("grpc")
                maybeCreate("grpckt")
            }
        }
    }
}
application {
    // set your main later, e.g. "com.example.MainKt"
    mainClass = "com.itmo.MainKt"
}
tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}
