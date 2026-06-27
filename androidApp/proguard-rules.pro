# Sync360 ProGuard Rules

# Ktor contains a JVM debugger detector that references java.lang.management
# classes. Android does not provide these classes, and this code path is not
# needed on Android, so release minification should ignore the missing classes.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean
