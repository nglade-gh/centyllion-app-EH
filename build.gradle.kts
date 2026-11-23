
plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false

    // serialization
    alias(libs.plugins.kotlin.serialization) apply false

    // sqldelight
    alias(libs.plugins.sqldelight) apply false

}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
    group = "com.centyllion"
    // Endless Bloom (EB) — version majeure : la logique de calcul diffère de NetBioDyn Forever
    version = "2.0.0"
}

