import UIKit

/// `UIColor.systemCyan` and `.systemIndigo` are iOS 15+. Example app targets iOS 13.
enum ExampleColorCompat {
    static var systemCyan: UIColor {
        if #available(iOS 15.0, *) {
            return .systemCyan
        }
        return UIColor(red: 0.224, green: 0.737, blue: 0.851, alpha: 1.0)
    }

    static var systemIndigo: UIColor {
        if #available(iOS 15.0, *) {
            return .systemIndigo
        }
        return UIColor(red: 0.345, green: 0.337, blue: 0.839, alpha: 1.0)
    }
}
