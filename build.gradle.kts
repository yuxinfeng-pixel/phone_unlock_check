// 根 build.gradle.kts 通常为空或只包含插件
plugins {
    // 这里什么都不需要，或者：
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
}
