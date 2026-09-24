# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.audioranobe.app.**$$serializer { *; }
-keepclassmembers class com.audioranobe.app.** { *** Companion; }
-keepclasseswithmembers class com.audioranobe.app.** { kotlinx.serialization.KSerializer serializer(...); }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# JavaScript bridge used by the captcha / OAuth WebViews
-keepclassmembers class com.audioranobe.app.** {
    @android.webkit.JavascriptInterface <methods>;
}

# Markdown parser (intellij-markdown) and charts are pure Kotlin; nothing reflective.
-dontwarn org.intellij.**
-dontwarn org.jetbrains.**
