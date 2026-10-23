# OpenCV's Java API is bound to native code via JNI by class and method name.
-keep class org.opencv.** { *; }

# MediaPipe reads its protobuf-lite messages reflectively by field name, and loads task classes from
# native code; renaming either breaks embedding at runtime ("Field platform_ ... not found").
-keep class com.google.mediapipe.** { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-dontwarn com.google.mediapipe.**
# MediaPipe logs through Flogger, which finds the calling class by inspecting the stack by name.
-keep class com.google.common.flogger.** { *; }

# ML Kit instantiates its component registrars reflectively through no-arg constructors.
-keep class com.google.mlkit.** { *; }
