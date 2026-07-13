# CloudMelody ProGuard Rules

# ─── Keep model classes (used by reflection / JSON parsing) ───
-keep class com.cloudmelody.model.** { *; }
-keepclassmembers class com.cloudmelody.model.** { *; }

# ─── OkHttp / Okio ───
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase

# ─── Coil ───
-dontwarn coil.**
-keep class coil.** { *; }

# ─── Kotlin coroutines ───
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# ─── ViewBinding ───
-keep class com.cloudmelody.databinding.** { *; }

# ─── Kotlin metadata ───
-keep class kotlin.Metadata { *; }

# ─── JSON (org.json) ───
-keep class org.json.** { *; }
