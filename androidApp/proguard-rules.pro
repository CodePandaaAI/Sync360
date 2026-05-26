# Sync360 ProGuard Rules

# ==============================================================================
# 1. JVM-specific Warnings (Already added to suppress compile errors)
# ==============================================================================
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn org.slf4j.impl.StaticMDCBinder

# ==============================================================================
# 2. Kotlinx Serialization & API DTOs
# ==============================================================================
# Keep all serialized models and their fields so JSON properties don't get renamed/obfuscated
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
# Keep our network API data models completely intact
-keep class com.liftley.sync360.features.sync.data.network.api.** { *; }

# ==============================================================================
# 3. Ktor Networking (CIO Engine & HTTP Server)
# ==============================================================================
# Ktor uses reflection to locate and load engines (CIO) and negotiators
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ==============================================================================
# 4. Koin Dependency Injection
# ==============================================================================
# Prevent Koin annotations and definitions from being stripped
-keep class org.koin.** { *; }
-dontwarn org.koin.**
# Keep all public constructors so Koin can instantiate parameters dynamically
-keepclassmembers class * {
    public <init>(...);
}

# ==============================================================================
# 5. Core Platform Operations & Repositories
# ==============================================================================
# Keep our custom interfaces and implementation classes so Koin can resolve them
-keep class com.liftley.sync360.core.platform.** { *; }
-keep class com.liftley.sync360.features.sync.data.repository.** { *; }
-keep class com.liftley.sync360.features.sync.presentation.SyncViewModel { *; }
