import UIKit
import PulseKit
import OpenTelemetryApi

/// Wraps a UITabBarController to test lifecycle instrumentation with tab-based navigation.
/// UITabBarController itself is filtered by shouldTrack, but child VCs are tracked.
/// Switching tabs fires viewWillDisappear/viewDidAppear → Stopped + Restarted spans.
class TabBarExampleViewController: UITabBarController {

    override func viewDidLoad() {
        super.viewDidLoad()

        let tabA = TabAViewController()
        tabA.tabBarItem = UITabBarItem(title: "Tab A", image: UIImage(systemName: "a.circle"), tag: 0)

        let tabB = TabBViewController()
        tabB.tabBarItem = UITabBarItem(title: "Tab B", image: UIImage(systemName: "b.circle"), tag: 1)

        viewControllers = [tabA, tabB]
    }
}

// MARK: - Tab A

class TabAViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Tab A"
        view.backgroundColor = .systemOrange

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 16
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 40),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -40)
        ])

        let label = UILabel()
        label.text = "TabAViewController"
        label.font = .systemFont(ofSize: 22, weight: .bold)
        label.textColor = .white
        stack.addArrangedSubview(label)

        let hint = UILabel()
        hint.text = "Switch to Tab B and back.\nFirst visit → Created span.\nReturn visit → Restarted span."
        hint.font = .systemFont(ofSize: 14)
        hint.textColor = .white.withAlphaComponent(0.8)
        hint.textAlignment = .center
        hint.numberOfLines = 0
        stack.addArrangedSubview(hint)

        addButton(to: stack, title: "Track Event (Tab A)", color: .systemRed, action: #selector(trackEventTapped))
        addButton(to: stack, title: "Dismiss TabBar", color: .white, titleColor: .systemOrange, action: #selector(dismissTapped))
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
        Pulse.shared.trackEvent(
            name: "tab_a_event",
            observedTimeStampInMs: Date().timeIntervalSince1970 * 1000,
            params: ["source": AttributeValue.string("TabAViewController")]
        )
        print("━━━ TabAViewController: trackEvent ━━━")
    }

    @objc private func dismissTapped() {
        dismiss(animated: true)
    }
}

// MARK: - Tab B

class TabBViewController: UIViewController {

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Tab B"
        view.backgroundColor = .systemPurple

        let stack = UIStackView()
        stack.axis = .vertical
        stack.spacing = 16
        stack.alignment = .center
        stack.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(stack)

        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            stack.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            stack.leadingAnchor.constraint(equalTo: view.leadingAnchor, constant: 40),
            stack.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -40)
        ])

        let label = UILabel()
        label.text = "TabBViewController"
        label.font = .systemFont(ofSize: 22, weight: .bold)
        label.textColor = .white
        stack.addArrangedSubview(label)

        let hint = UILabel()
        hint.text = "First visit → Created span.\nReturn to Tab A then back here → Restarted span.\nUITabBarController itself is NOT tracked."
        hint.font = .systemFont(ofSize: 14)
        hint.textColor = .white.withAlphaComponent(0.8)
        hint.textAlignment = .center
        hint.numberOfLines = 0
        stack.addArrangedSubview(hint)

        addButton(to: stack, title: "Track Event (Tab B)", color: .systemTeal, action: #selector(trackEventTapped))
        addButton(to: stack, title: "Dismiss TabBar", color: .white, titleColor: .systemPurple, action: #selector(dismissTapped))
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
        Pulse.shared.trackEvent(
            name: "tab_b_event",
            observedTimeStampInMs: Date().timeIntervalSince1970 * 1000,
            params: ["source": AttributeValue.string("TabBViewController")]
        )
        print("━━━ TabBViewController: trackEvent ━━━")
    }

    @objc private func dismissTapped() {
        dismiss(animated: true)
    }
}
