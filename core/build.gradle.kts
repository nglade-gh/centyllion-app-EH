import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {

    jvm {
        testRuns["test"].executionTask.configure {
            useJUnitPlatform()
        }
    }

    js() {
        browser {
            // JS tests require a browser (ChromeHeadless). Disabled by default;
            // enable with -PenableJsTests=true (requires Chrome/Chromium).
            testTask {
                enabled = project.findProperty("enableJsTests") as Boolean? ?: false
            }
        }
        binaries.library()

        compilations.forEach {
            it.kotlinOptions {
                moduleKind = "amd"
            }
        }
    }

    sourceSets {

        commonMain {
            dependencies {
                api(libs.serialization.json)
                api(libs.benasher44)
            }
        }

        commonTest {
            dependencies {
                implementation(libs.bundles.test)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.bundles.test.jvm)
            }
        }

        jsTest {
            dependencies {
                implementation(libs.bundles.test.js)
            }
        }
    }
}
