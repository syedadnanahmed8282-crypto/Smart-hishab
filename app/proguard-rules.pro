# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

-keep class androidx.startup.** { *; }
-keep class * implements androidx.startup.Initializer { *; }
