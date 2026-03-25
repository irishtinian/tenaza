// Top-level build file — no dependencies aqui, solo aplicar plugins con apply false
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    // ksp se agrega en plan 02+ cuando se necesite Room
}
