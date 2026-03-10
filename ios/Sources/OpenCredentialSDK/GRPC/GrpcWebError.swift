import Foundation

public enum GrpcWebError: Error, LocalizedError
{
    case grpcStatus(Int, String)
    case httpError(Int)
    case malformedResponse

    public var statusCode: Int
    {
        switch self
        {
            case .grpcStatus(let code, _): return code
            case .httpError(let code): return code
            case .malformedResponse: return -1
        }
    }

    public var grpcMessage: String
    {
        switch self
        {
            case .grpcStatus(_, let message): return message
            case .httpError(let code): return "HTTP error \(code)"
            case .malformedResponse: return "Malformed gRPC-Web response"
        }
    }

    public var statusName: String
    {
        switch self
        {
            case .grpcStatus(let code, _):
                switch code
                {
                    case 0: return "OK"
                    case 1: return "CANCELLED"
                    case 2: return "UNKNOWN"
                    case 3: return "INVALID_ARGUMENT"
                    case 4: return "DEADLINE_EXCEEDED"
                    case 5: return "NOT_FOUND"
                    case 6: return "ALREADY_EXISTS"
                    case 7: return "PERMISSION_DENIED"
                    case 8: return "RESOURCE_EXHAUSTED"
                    case 9: return "FAILED_PRECONDITION"
                    case 10: return "ABORTED"
                    case 11: return "OUT_OF_RANGE"
                    case 12: return "UNIMPLEMENTED"
                    case 13: return "INTERNAL"
                    case 14: return "UNAVAILABLE"
                    case 15: return "DATA_LOSS"
                    case 16: return "UNAUTHENTICATED"
                    default: return "UNKNOWN(\(code))"
                }
            case .httpError: return "HTTP_ERROR"
            case .malformedResponse: return "MALFORMED"
        }
    }

    public var errorDescription: String?
    {
        switch self
        {
            case .grpcStatus(let code, let message):
                return "gRPC error \(statusName) (\(code)): \(message)"
            case .httpError(let code):
                return "HTTP error: \(code)"
            case .malformedResponse:
                return "Malformed gRPC-Web response"
        }
    }
}
