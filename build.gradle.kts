// Root build script. Plugins are declared here with `apply false` and applied in
// the subproject that needs them (see :shared).
plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
}
