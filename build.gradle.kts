plugins {
    // AGP 9 compiles Kotlin itself (no separate Kotlin plugin) and runs on
    // Gradle 9, which supports the JDK 25 bundled with current Android Studio.
    id("com.android.application") version "9.1.1" apply false
}
