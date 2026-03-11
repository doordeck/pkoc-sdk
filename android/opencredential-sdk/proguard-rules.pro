# Protobuf Lite
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite {
    <fields>;
}

# gRPC
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**
-keepclassmembers class io.grpc.** { *; }

# gRPC generated stubs
-keep class com.sentryinteractive.opencredential.api.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**
