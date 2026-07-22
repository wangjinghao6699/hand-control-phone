# 添加项目保留规则
-keep class com.google.mediapipe.** { *; }
-dontwarn com.google.mediapipe.**

# Kotlin
-keepattributes *Annotation*
-keep class kotlin.** { *; }
