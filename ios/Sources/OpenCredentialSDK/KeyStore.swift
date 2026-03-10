import Foundation

public struct OCKeyData: Codable
{
    public let publicKey: Data
    public let privateKey: Data

    public init(publicKey: Data, privateKey: Data)
    {
        self.publicKey = publicKey
        self.privateKey = privateKey
    }
}

public enum OCKeyStoreError: Error
{
    case keyFileNotFound
}

public final class OCKeyStore
{
    private static func fileURL() throws -> URL
    {
        try FileManager.default.url(
            for: .documentDirectory,
            in: .userDomainMask,
            appropriateFor: nil,
            create: false
        ).appendingPathComponent("com.sentryinteractive.opencredential.keystore")
    }

    public static func loadSync() throws -> OCKeyData
    {
        let fileURL = try fileURL()
        guard let file = try? FileHandle(forReadingFrom: fileURL) else
        {
            throw OCKeyStoreError.keyFileNotFound
        }
        return try JSONDecoder().decode(OCKeyData.self, from: file.availableData)
    }

    public static func load(completion: @escaping (Result<OCKeyData, Error>) -> Void)
    {
        DispatchQueue.global(qos: .background).async
        {
            do
            {
                let fileURL = try fileURL()
                guard let file = try? FileHandle(forReadingFrom: fileURL) else
                {
                    DispatchQueue.main.async { completion(.failure(OCKeyStoreError.keyFileNotFound)) }
                    return
                }
                let data = try JSONDecoder().decode(OCKeyData.self, from: file.availableData)
                DispatchQueue.main.async { completion(.success(data)) }
            }
            catch
            {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }

    public static func save(keyData: OCKeyData, completion: @escaping (Result<Bool, Error>) -> Void)
    {
        DispatchQueue.global(qos: .background).async
        {
            do
            {
                let data = try JSONEncoder().encode(keyData)
                let outFile = try fileURL()
                try data.write(to: outFile)
                DispatchQueue.main.async { completion(.success(true)) }
            }
            catch
            {
                DispatchQueue.main.async { completion(.failure(error)) }
            }
        }
    }
}
