import UIKit
import PulseKit
import OpenTelemetryApi

class ThirdViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Third Screen"
        view.backgroundColor = .systemTeal

        let stackView = UIStackView()
        stackView.axis = .vertical
        stackView.spacing = 16
        stackView.alignment = .center
        stackView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stackView)

        NSLayoutConstraint.activate([
            stackView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stackView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            stackView.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 40),
            stackView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -40)
        ])

        let label = UILabel()
        label.text = "ThirdViewController"
        label.font = .systemFont(ofSize: 22, weight: .bold)
        label.textColor = .white
        label.textAlignment = .center
        stackView.addArrangedSubview(label)

        let hint = UILabel()
        hint.text = "screen.name should be \"ThirdViewController\"\nlast.screen.name should be \"SecondViewController\""
        hint.font = .systemFont(ofSize: 14)
        hint.textColor = .white.withAlphaComponent(0.8)
        hint.textAlignment = .center
        hint.numberOfLines = 0
        stackView.addArrangedSubview(hint)

        addButton(to: stackView, title: "Track Event", color: .systemOrange, action: #selector(trackEventTapped))
        addButton(to: stackView, title: "Track Span", color: .systemGreen, action: #selector(trackSpanTapped))

        let info = UILabel()
        info.text = "Pop back to test screen transitions.\nEach transition updates screen.name\nand last.screen.name on all future signals."
        info.font = .systemFont(ofSize: 13)
        info.textColor = .white.withAlphaComponent(0.6)
        info.textAlignment = .center
        info.numberOfLines = 0
        stackView.addArrangedSubview(info)
    }

    private func addButton(to stack: UIStackView, title: String, color: UIColor, action: Selector) {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        button.backgroundColor = color
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.addTarget(self, action: action, for: .touchUpInside)
        stack.addArrangedSubview(button)
        button.heightAnchor.constraint(equalToConstant: 50).isActive = true
        button.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
    }

    @objc private func trackEventTapped() {
        let timestamp = Date().timeIntervalSince1970 * 1000
        Pulse.shared.trackEvent(
            name: "third_screen_event",
            observedTimeStampInMs: timestamp,
            params: [
                "source": AttributeValue.string("ThirdViewController"),
                "action": AttributeValue.string("button_tap")
            ]
        )
        print("━━━ ThirdViewController: trackEvent ━━━")
        print("  Check screen.name attribute on this event")
    }

    @objc private func trackSpanTapped() {
        Pulse.shared.trackSpan(name: "third_screen_span", params: [
            "source": AttributeValue.string("ThirdViewController")
        ]) {
            Thread.sleep(forTimeInterval: 0.1)
        }
        print("━━━ ThirdViewController: trackSpan ━━━")
        print("  Check screen.name attribute on this span")
    }
}
