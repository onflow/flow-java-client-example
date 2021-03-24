plugins {
    application
}

repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    // Use JUnit Jupiter API for testing.
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.6.2")

    // Use JUnit Jupiter Engine for testing.
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")

    implementation("org.onflow:flow-jvm-sdk:0.1.1")
}

application {
    // Define the main class for the application.
    mainClass.set("org.onflow.App")
}

tasks.test {
    // Use junit platform for unit tests.
    useJUnitPlatform()
}
