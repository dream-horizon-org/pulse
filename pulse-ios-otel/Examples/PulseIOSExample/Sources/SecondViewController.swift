import UIKit
import PulseKit
import OpenTelemetryApi

class SecondViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Second Screen"
        view.backgroundColor = .systemIndigo

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
        label.text = "SecondViewController"
        label.font = .systemFont(ofSize: 22, weight: .bold)
        label.textColor = .white
        label.textAlignment = .center
        stackView.addArrangedSubview(label)

        let hint = UILabel()
        hint.text = "screen.name should be \"SecondViewController\"\nlast.screen.name should be \"MainViewController\""
        hint.font = .systemFont(ofSize: 14)
        hint.textColor = .white.withAlphaComponent(0.8)
        hint.textAlignment = .center
        hint.numberOfLines = 0
        stackView.addArrangedSubview(hint)

        addButton(to: stackView, title: "Track Event", color: .systemOrange, action: #selector(trackEventTapped))
        addButton(to: stackView, title: "Track Span", color: .systemGreen, action: #selector(trackSpanTapped))
        addButton(to: stackView, title: "Push Third Screen →", color: .white, titleColor: .systemIndigo, action: #selector(pushThirdScreen))
    }

    private func addButton(to stack: UIStackView, title: String, color: UIColor, titleColor: UIColor = .white, action: Selector) {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        button.backgroundColor = color
        button.setTitleColor(titleColor, for: .normal)
        button.layer.cornerRadius = 8
        button.addTarget(self, action: action, for: .touchUpInside)
        stack.addArrangedSubview(button)
        button.heightAnchor.constraint(equalToConstant: 50).isActive = true
        button.widthAnchor.constraint(equalTo: stack.widthAnchor).isActive = true
    }

    @objc private func trackEventTapped() {
        let timestamp = Date().timeIntervalSince1970 * 1000
        Pulse.shared.trackEvent(
            name: "second_screen_event",
            observedTimeStampInMs: timestamp,
            params: [
                "source": AttributeValue.string("SecondViewController"),
                "action": AttributeValue.string("button_tap")
            ]
        )
        print("━━━ SecondViewController: trackEvent ━━━")
        print("  Check screen.name attribute on this event")
    }

    @objc private func trackSpanTapped() {
        Pulse.shared.trackSpan(name: "second_screen_span", params: [
            "source": AttributeValue.string("SecondViewController")
        ]) {
            Thread.sleep(forTimeInterval: 0.1)
        }
        print("━━━ SecondViewController: trackSpan ━━━")
        print("  Check screen.name attribute on this span")
    }

    @objc private func pushThirdScreen() {
        navigationController?.pushViewController(ThirdViewController(), animated: true)
    }
}
