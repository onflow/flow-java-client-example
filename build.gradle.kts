
plugins {
    kotlin("jvm") version "1.4.21"
    java
    application
}

val FLOW_JVM_SDK_VERSION    = "0.1.1"
val USE_KOTLIN_APP          = project.findProperty("USE_KOTLIN_APP") == "true"

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
    maven { url = uri("https://dl.bintray.com/ethereum/maven/") }
}

dependencies {
    implementation("org.onflow:flow-jvm-sdk:${FLOW_JVM_SDK_VERSION}")

    // Use JUnit Jupiter Engine for testing.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}

application {
    // Define the main class for the application.
    mainClass.set(if (USE_KOTLIN_APP) {
        "org.onflow.examples.kotlin.App"
    } else {
        "org.onflow.examples.java.App"
    })
}

tasks.test {
    // Use junit platform for unit tests.
    useJUnitPlatform()
}
