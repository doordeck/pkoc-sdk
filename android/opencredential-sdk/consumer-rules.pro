# Protobuf Lite
-keep class * extends com.google.protobuf.GeneratedMessageLite { *; }
-keep class * extends com.google.protobuf.GeneratedMessageLite$Builder { *; }

# gRPC
-keep class io.grpc.** { *; }
-dontwarn io.grpc.**

# gRPC generated stubs
-keep class com.sentryinteractive.opencredential.api.** { *; }
