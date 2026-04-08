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
val jmhVersion = "1.37"

dependencies {
    implementation(kotlin("stdlib"))

    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")

    compileOnly("org.apache.tomcat:annotations-api:6.0.53")

    implementation("redis.clients:jedis:5.1.0")

    testImplementation(kotlin("test"))

    jmh("org.openjdk.jmh:jmh-core:$jmhVersion")
    jmhAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
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
    mainClass = "com.itmo.MainKt"
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

jmh {
    resultFormat.set("JSON")
}