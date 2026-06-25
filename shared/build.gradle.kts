plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    // JDK 21 toolchain. Revisit when :androidApp lands — AGP supports 17–21; the
    // shared module's jvm() target only needs a modern LTS for host-side tests.
    jvmToolchain(21)

    // The HealthBridge sync boundary uses an expect/actual class (Beta in K2);
    // this opts in so it doesn't warn on every build.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

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
