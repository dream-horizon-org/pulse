import UIKit
import PulseKit
import OpenTelemetryApi

class MainViewController: UIViewController {
    
    private let scrollView = UIScrollView()
    private let contentView = UIView()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    private func setupUI() {
        title = "Pulse iOS SDK"
        view.backgroundColor = .systemBackground
        
        view.addSubview(scrollView)
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(contentView)
        contentView.translatesAutoresizingMaskIntoConstraints = false
        
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            
            contentView.topAnchor.constraint(equalTo: scrollView.topAnchor),
            contentView.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            contentView.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            contentView.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor),
            contentView.widthAnchor.constraint(equalTo: scrollView.widthAnchor)
        ])
        
        let stackView = UIStackView()
        stackView.axis = .vertical
        stackView.spacing = 16
        stackView.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(stackView)
        
        NSLayoutConstraint.activate([
            stackView.topAnchor.constraint(equalTo: contentView.topAnchor, constant: 20),
            stackView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 20),
            stackView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -20),
            stackView.bottomAnchor.constraint(equalTo: contentView.bottomAnchor, constant: -20)
        ])
        
        // Header
        let headerLabel = UILabel()
        headerLabel.text = "Pulse iOS SDK Demo"
        headerLabel.font = .systemFont(ofSize: 24, weight: .bold)
        headerLabel.textAlignment = .center
        stackView.addArrangedSubview(headerLabel)
        
        stackView.addArrangedSubview(createSeparator())
        
        // Track Event Button
        let trackEventButton = createButton(
            title: "Track Custom Event",
            action: #selector(trackEventTapped)
        )
        stackView.addArrangedSubview(trackEventButton)
        
        // Track Non-Fatal Button
        let trackNonFatalButton = createButton(
            title: "Track Non-Fatal Error",
            action: #selector(trackNonFatalTapped)
        )
        stackView.addArrangedSubview(trackNonFatalButton)
        
        // Track Span Button
        let trackSpanButton = createButton(
            title: "Track Span (Closure)",
            action: #selector(trackSpanTapped)
        )
        stackView.addArrangedSubview(trackSpanButton)
        
        // Start Span Button
        let startSpanButton = createButton(
            title: "Start Span (Manual)",
            action: #selector(startSpanTapped)
        )
        stackView.addArrangedSubview(startSpanButton)
        
        // Network Request Button
        let networkButton = createButton(
            title: "Make Network Request",
            action: #selector(networkRequestTapped)
        )
        stackView.addArrangedSubview(networkButton)
        
        stackView.addArrangedSubview(createSeparator())
        
        // Status Label
        let statusLabel = UILabel()
        statusLabel.text = "SDK Status: Initialized"
        statusLabel.font = .systemFont(ofSize: 14)
        statusLabel.textColor = .systemGreen
        statusLabel.textAlignment = .center
        stackView.addArrangedSubview(statusLabel)
    }
    
    private func createButton(title: String, action: Selector) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        button.backgroundColor = .systemBlue
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.heightAnchor.constraint(equalToConstant: 50).isActive = true
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }
    
    private func createSeparator() -> UIView {
        let separator = UIView()
        separator.backgroundColor = .separator
        separator.heightAnchor.constraint(equalToConstant: 1).isActive = true
        return separator
    }
    
    @objc private func trackEventTapped() {
        print("trackEventTapped pressed")
        let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
        PulseSDK.shared.trackEvent(
            name: "tract_custom_event",
            observedTimeStampInMs: timestamp,
            params: [
                "button_name": "track_event",
                "screen": "main"
            ]
        )
        showAlert(title: "Event Tracked", message: "Custom event 'button_clicked' has been tracked")
    }
    
    @objc private func trackNonFatalTapped() {
        print("trackNonFatalTapped pressed")
        
        do {
            // Simulate an operation that throws an error
            let invalidJSON = "{ invalid json }"
            let data = invalidJSON.data(using: .utf8)!
            _ = try JSONSerialization.jsonObject(with: data, options: [])
        } catch {
            // Track the caught error as a non-fatal error
            let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
            PulseSDK.shared.trackNonFatal(
                error: error,
                observedTimeStampInMs: timestamp,
                params: [
                    "error_source": "json_parsing",
                    "screen": "main"
                ]
            )
            showAlert(title: "Non-Fatal Tracked", message: "Non-fatal error caught and tracked")
        }
    }
    
    @objc private func trackSpanTapped() {
        let result = PulseSDK.shared.trackSpan(
            name: "track_span",
            params: [
                "action": "track_span",
                "method": "closure_based"
            ]
        ) {
            // Simulate some work
            Thread.sleep(forTimeInterval: 0.5)
            return "Span completed"
        }
        showAlert(title: "Span Tracked", message: "Span completed: \(result)")
    }
    
    @objc private func startSpanTapped() {
        print("startSpanTapped")
        let span = PulseSDK.shared.startSpan(
            name: "manual_created_span",
            params: [
                "action": "start_span_1",
                "method": "manual"
            ]
        )
        span.end()
        
        // Simulate some work
        DispatchQueue.global().asyncAfter(deadline: .now() + 1.0) {
            span.end()
            print("startSpanTapped ended")
            DispatchQueue.main.async {
                self.showAlert(title: "Span Ended", message: "Manual span has been completed")
            }
        }
        
        showAlert(title: "Span Started", message: "Manual span has been started and will end in 1 second")
    }
    
    @objc private func networkRequestTapped() {
        guard let url = URL(string: "https://httpbin.org/get") else { return }
        let task = URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    self.showAlert(title: "Network Error", message: error.localizedDescription)
                } else {
                    self.showAlert(title: "Network Success", message: "Request completed successfully")
                }
            }
        }
        task.resume()
    }
    
    private func showAlert(title: String, message: String) {
        print(title)
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

