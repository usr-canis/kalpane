# AndroidX Ink, WorkManager and Compose ship their own consumer rules.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*

# Notes, drawings, trash entries and sync state are stored as JSON with kotlinx.serialization.
# Keep the model classes and their generated serializers intact so release builds read and write
# exactly the same files as debug builds.
-keep class dev.kalpane.data.** { *; }
-keepclassmembers class dev.kalpane.** { *** Companion; }
-keepclasseswithmembers class dev.kalpane.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class dev.kalpane.**$$serializer { *; }
-dontnote kotlinx.serialization.**

# Enum names are stored in settings.
-keepclassmembers enum dev.kalpane.** { *; }

# OkHttp optional platform integrations.
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
