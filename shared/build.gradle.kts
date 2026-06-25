plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    jvmToolchain(17)

    // Android target is added alongside :androidApp in Phase 1. iOS targets are
    // declared here so the native health bridge (expect/actual) has a home.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // A JVM target keeps the shared module unit-testable on the host without a
    // device/emulator during Phase 0.
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
