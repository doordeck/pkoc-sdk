package com.sentryinteractive.opencredential.sdk.grpc

/**
 * Exception thrown when a gRPC-Web call returns a non-OK status.
 */
class GrpcWebException(
    val statusCode: Int,
    val grpcMessage: String
) : Exception("gRPC error $statusCode: $grpcMessage")
