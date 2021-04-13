
val FLOW_JVM_SDK_VERSION = "0.1.1"

plugins {
    application
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://dl.bintray.com/ethereum/maven/") }
}

dependencies {
    // Use JUnit Jupiter API for testing.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")

    // Use JUnit Jupiter Engine for testing.
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    implementation("org.onflow:flow-jvm-sdk:${FLOW_JVM_SDK_VERSION}")
    implementation("org.json:json:20201115")
    implementation("org.ethereum:ethereumj-core:1.12.0-RELEASE")
}

application {
    // Define the main class for the application.
    mainClass.set("org.onflow.App")
}

tasks.test {
    // Use junit platform for unit tests.
    useJUnitPlatform()
}
