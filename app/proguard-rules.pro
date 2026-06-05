# Keep model classes
-keep class com.cloudmelody.model.** { *; }
-keepclassmembers class com.cloudmelody.model.** { *; }
# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
# Coil
-dontwarn coil.**
# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
# Keep ViewBinding
-keep class com.cloudmelody.databinding.** { *; }
