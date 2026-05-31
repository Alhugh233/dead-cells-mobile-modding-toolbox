plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.compose") version "1.11.0" apply false
}

tasks.register("Delete", Delete::class) {
    delete(rootProject.layout.buildDirectory)
}
