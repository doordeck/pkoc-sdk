import Foundation

/// Loads localised strings from the SDK's resource bundle.
internal enum OCStrings
{
    static func localized(_ key: String) -> String
    {
        NSLocalizedString(key, bundle: Bundle.module, comment: "")
    }

    static func localized(_ key: String, _ args: CVarArg...) -> String
    {
        String(format: NSLocalizedString(key, bundle: Bundle.module, comment: ""), arguments: args)
    }
}
