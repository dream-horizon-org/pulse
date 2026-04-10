import UIKit

/// Minimal screen: confirms local xcframeworks + Swift package link work.
final class RootViewController: UIViewController {
    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Pulse SPM Example"
        view.backgroundColor = .systemBackground

        let label = UILabel()
        label.text = "PulseKit + vendored xcframeworks\n\nOpen PulseSPMExample.xcodeproj to run (real app bundle)."
        label.numberOfLines = 0
        label.textAlignment = .center
        label.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(label)

        NSLayoutConstraint.activate([
            label.leadingAnchor.constraint(equalTo: view.layoutMarginsGuide.leadingAnchor),
            label.trailingAnchor.constraint(equalTo: view.layoutMarginsGuide.trailingAnchor),
            label.centerYAnchor.constraint(equalTo: view.centerYAnchor),
        ])
    }
}
