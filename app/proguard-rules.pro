# Release build rules. Keep only what reflection or the framework requires.

# Compose / AndroidX ship their own consumer rules; nothing app-specific needs
# keeping, so the app package stays obfuscated.

# Kotlin metadata is not needed at runtime.
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

# Keep line numbers for readable crash stacks without keeping names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
