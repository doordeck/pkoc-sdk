import Foundation

// MARK: - Public Enums

public enum OCCredentialType: Int
{
    case unspecified = 0
    case p256       = 1
    case p384       = 2
    case p521       = 3
    case ed25519    = 4
    case ed448      = 5
    case mlDsa44    = 6
    case mlDsa65    = 7
    case mlDsa87    = 8
}

public enum OCCredentialFilter: Int
{
    case unspecified   = 0
    case sameIdentity  = 1
    case sameKey       = 2
}

// MARK: - Public Models

public struct OCIdentity: Hashable
{
    public enum IdentityCase: Hashable
    {
        case email(String)
        case phone(String)
        case none
    }
    public var identityCase: IdentityCase = .none

    public init(identityCase: IdentityCase = .none)
    {
        self.identityCase = identityCase
    }

    public static func email(_ address: String) -> OCIdentity
    {
        OCIdentity(identityCase: .email(address))
    }

    public static func phone(_ number: String) -> OCIdentity
    {
        OCIdentity(identityCase: .phone(number))
    }

    public var email: String?
    {
        if case .email(let v) = identityCase { return v }
        return nil
    }

    public var phone: String?
    {
        if case .phone(let v) = identityCase { return v }
        return nil
    }

    /// The underlying string value (email address or phone number), for display.
    public var value: String
    {
        switch identityCase
        {
            case .email(let v): return v
            case .phone(let v): return v
            case .none: return ""
        }
    }
}

public struct OCCredential
{
    public var identity: OCIdentity?
    public var credential: Data = Data()
    public var credentialType: OCCredentialType = .unspecified

    public var credentialHex: String
    {
        credential.map { String(format: "%02x", $0) }.joined()
    }
}

public struct OCStartEmailVerificationResponse
{
    public var verificationToken: String = ""
}

public struct OCGetCredentialsResponse
{
    public var credentials: [OCCredential] = []
}

public struct OCOrganization
{
    public var organizationId: String = ""
    public var name: String = ""
    public var contactEmail: String = ""
    public var contactPhone: String = ""
    public var contactAddress: String = ""
}

// MARK: - Internal Protobuf Encoder

internal struct ProtoEncoder
{
    private var data = Data()

    mutating func encodeString(_ fieldNumber: Int, _ value: String)
    {
        guard !value.isEmpty else { return }
        let bytes = Data(value.utf8)
        appendTag(fieldNumber: fieldNumber, wireType: 2)
        appendVarint(UInt64(bytes.count))
        data.append(bytes)
    }

    mutating func encodeBytes(_ fieldNumber: Int, _ value: Data)
    {
        guard !value.isEmpty else { return }
        appendTag(fieldNumber: fieldNumber, wireType: 2)
        appendVarint(UInt64(value.count))
        data.append(value)
    }

    mutating func encodeEnum(_ fieldNumber: Int, _ value: Int)
    {
        guard value != 0 else { return }
        appendTag(fieldNumber: fieldNumber, wireType: 0)
        appendVarint(UInt64(bitPattern: Int64(value)))
    }

    mutating func encodeMessage(_ fieldNumber: Int, _ inner: ProtoEncoder)
    {
        let built = inner.build()
        guard !built.isEmpty else { return }
        appendTag(fieldNumber: fieldNumber, wireType: 2)
        appendVarint(UInt64(built.count))
        data.append(built)
    }

    func build() -> Data { data }

    private mutating func appendTag(fieldNumber: Int, wireType: Int)
    {
        appendVarint(UInt64((fieldNumber << 3) | wireType))
    }

    private mutating func appendVarint(_ value: UInt64)
    {
        var v = value
        repeat
        {
            let byte = UInt8(v & 0x7F)
            v >>= 7
            data.append(v > 0 ? byte | 0x80 : byte)
        } while v > 0
    }
}

// MARK: - Internal Protobuf Decoder

internal final class ProtoDecoder
{
    private let data: Data
    private var offset: Data.Index

    init(_ data: Data)
    {
        self.data = data
        self.offset = data.startIndex
    }

    var hasMore: Bool { offset < data.endIndex }

    func nextTag() -> (Int, Int)?
    {
        guard let tag = readVarint() else { return nil }
        return (Int(tag >> 3), Int(tag & 0x7))
    }

    func readVarint() -> UInt64?
    {
        var result: UInt64 = 0
        var shift = 0
        while offset < data.endIndex
        {
            let byte = data[offset]
            offset = data.index(after: offset)
            result |= UInt64(byte & 0x7F) << shift
            if byte & 0x80 == 0 { return result }
            shift += 7
            if shift >= 64 { return nil }
        }
        return nil
    }

    func readLengthDelimited() -> Data?
    {
        guard let len = readVarint() else { return nil }
        let count = Int(len)
        guard data.distance(from: offset, to: data.endIndex) >= count else { return nil }
        let end = data.index(offset, offsetBy: count)
        let slice = Data(data[offset..<end])
        offset = end
        return slice
    }

    func readString() -> String?
    {
        guard let bytes = readLengthDelimited() else { return nil }
        return String(data: bytes, encoding: .utf8)
    }

    func skip(wireType: Int)
    {
        switch wireType
        {
            case 0: _ = readVarint()
            case 1:
                guard data.distance(from: offset, to: data.endIndex) >= 8 else { offset = data.endIndex; return }
                offset = data.index(offset, offsetBy: 8)
            case 2: _ = readLengthDelimited()
            case 5:
                guard data.distance(from: offset, to: data.endIndex) >= 4 else { offset = data.endIndex; return }
                offset = data.index(offset, offsetBy: 4)
            default:
                offset = data.endIndex
        }
    }
}

// MARK: - Encoding Functions

internal func encodeStartEmailVerificationRequest(
    email: String,
    credential: Data,
    credentialType: OCCredentialType,
    attestationDocument: String
) -> Data
{
    var enc = ProtoEncoder()
    enc.encodeString(1, email)
    enc.encodeBytes(2, credential)
    enc.encodeEnum(3, credentialType.rawValue)
    enc.encodeString(4, attestationDocument)
    return enc.build()
}

internal func encodeCompleteEmailVerificationRequest(token: String, code: String) -> Data
{
    var enc = ProtoEncoder()
    enc.encodeString(1, token)
    enc.encodeString(2, code)
    return enc.build()
}

internal func encodeGetCredentialsRequest(filter: OCCredentialFilter) -> Data
{
    var enc = ProtoEncoder()
    enc.encodeEnum(1, filter.rawValue)
    return enc.build()
}

internal func encodeDeleteCredentialsRequest(identity: OCIdentity?, keyThumbprint: String?) -> Data
{
    var enc = ProtoEncoder()
    if let identity = identity
    {
        var identityEnc = ProtoEncoder()
        switch identity.identityCase
        {
            case .email(let email): identityEnc.encodeString(1, email)
            case .phone(let phone): identityEnc.encodeString(2, phone)
            case .none: break
        }
        enc.encodeMessage(1, identityEnc)
    }
    if let keyThumbprint = keyThumbprint
    {
        enc.encodeString(2, keyThumbprint)
    }
    return enc.build()
}

internal func encodeGetOrganizationByInviteCodeRequest(inviteCode: String) -> Data
{
    var enc = ProtoEncoder()
    enc.encodeString(1, inviteCode)
    return enc.build()
}

internal func encodeShareCredentialWithOrganizationRequest(
    organizationId: String,
    identity: OCIdentity,
    inviteCode: String
) -> Data
{
    var identityEnc = ProtoEncoder()
    switch identity.identityCase
    {
        case .email(let email): identityEnc.encodeString(1, email)
        case .phone(let phone): identityEnc.encodeString(2, phone)
        case .none: break
    }

    var enc = ProtoEncoder()
    enc.encodeString(1, organizationId)
    enc.encodeMessage(2, identityEnc)
    enc.encodeString(3, inviteCode)
    return enc.build()
}

// MARK: - Decoding Functions

internal func decodeStartEmailVerificationResponse(_ data: Data) -> OCStartEmailVerificationResponse
{
    let dec = ProtoDecoder(data)
    var response = OCStartEmailVerificationResponse()
    while dec.hasMore
    {
        guard let (fieldNumber, wireType) = dec.nextTag() else { break }
        switch fieldNumber
        {
            case 1: response.verificationToken = dec.readString() ?? ""
            default: dec.skip(wireType: wireType)
        }
    }
    return response
}

internal func decodeGetCredentialsResponse(_ data: Data) -> OCGetCredentialsResponse
{
    let dec = ProtoDecoder(data)
    var response = OCGetCredentialsResponse()
    while dec.hasMore
    {
        guard let (fieldNumber, wireType) = dec.nextTag() else { break }
        switch fieldNumber
        {
            case 1:
                if let credData = dec.readLengthDelimited()
                {
                    response.credentials.append(decodeOCCredential(credData))
                }
            default: dec.skip(wireType: wireType)
        }
    }
    return response
}

private func decodeOCCredential(_ data: Data) -> OCCredential
{
    let dec = ProtoDecoder(data)
    var cred = OCCredential()
    while dec.hasMore
    {
        guard let (fieldNumber, wireType) = dec.nextTag() else { break }
        switch fieldNumber
        {
            case 1:
                if let identityData = dec.readLengthDelimited()
                {
                    cred.identity = decodeOCIdentity(identityData)
                }
            case 2: cred.credential = dec.readLengthDelimited() ?? Data()
            case 3:
                if let v = dec.readVarint()
                {
                    cred.credentialType = OCCredentialType(rawValue: Int(v)) ?? .unspecified
                }
            default: dec.skip(wireType: wireType)
        }
    }
    return cred
}

private func decodeOCIdentity(_ data: Data) -> OCIdentity
{
    let dec = ProtoDecoder(data)
    var identity = OCIdentity()
    while dec.hasMore
    {
        guard let (fieldNumber, wireType) = dec.nextTag() else { break }
        switch fieldNumber
        {
            case 1:
                if let email = dec.readString()
                {
                    identity.identityCase = .email(email)
                }
            case 2:
                if let phone = dec.readString()
                {
                    identity.identityCase = .phone(phone)
                }
            default: dec.skip(wireType: wireType)
        }
    }
    return identity
}

internal func decodeOCOrganization(_ data: Data) -> OCOrganization
{
    let dec = ProtoDecoder(data)
    var org = OCOrganization()
    while dec.hasMore
    {
        guard let (fieldNumber, wireType) = dec.nextTag() else { break }
        switch fieldNumber
        {
            case 1: org.organizationId   = dec.readString() ?? ""
            case 2: org.name             = dec.readString() ?? ""
            case 3: org.contactEmail     = dec.readString() ?? ""
            case 4: org.contactPhone     = dec.readString() ?? ""
            case 5: org.contactAddress   = dec.readString() ?? ""
            default: dec.skip(wireType: wireType)
        }
    }
    return org
}
