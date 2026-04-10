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
        
        // Add test UI elements for PII masking verification
        // SessionReplay is compiled into PulseKit when using CocoaPods
        addTestMaskingElements(to: stackView)
        
        stackView.addArrangedSubview(createSeparator())
        
        // ── Telemetry Testing ──
        stackView.addArrangedSubview(createSectionHeader("Telemetry Testing"))
        
        stackView.addArrangedSubview(createButton(
            title: "Track Custom Event",
            action: #selector(trackEventTapped)
        ))
        stackView.addArrangedSubview(createButton(
            title: "Track Non-Fatal Error",
            action: #selector(trackNonFatalTapped)
        ))
        stackView.addArrangedSubview(createButton(
            title: "Track Span (Closure)",
            action: #selector(trackSpanTapped)
        ))
        stackView.addArrangedSubview(createButton(
            title: "Start Span (Manual)",
            action: #selector(startSpanTapped)
        ))
        stackView.addArrangedSubview(createButton(
            title: "Make Network Request",
            action: #selector(networkRequestTapped)
        ))
        
        stackView.addArrangedSubview(createSeparator())
        
        // ── OTel Direct API Testing ──
        stackView.addArrangedSubview(createSectionHeader("OTel Direct API"))
        
        stackView.addArrangedSubview(createButton(
            title: "Emit OTel Log (all severities)",
            action: #selector(emitOtelLogTapped),
            color: .systemTeal
        ))
        stackView.addArrangedSubview(createButton(
            title: "Span with Events + Attributes",
            action: #selector(spanWithEventsTapped),
            color: .systemTeal
        ))
        stackView.addArrangedSubview(createButton(
            title: "Nested Spans (parent-child)",
            action: #selector(nestedSpansTapped),
            color: .systemTeal
        ))
        stackView.addArrangedSubview(createButton(
            title: "Log with Thread Info",
            action: #selector(logWithThreadInfoTapped),
            color: .systemTeal
        ))
        
        stackView.addArrangedSubview(createSeparator())
        
        // ── Metric Testing ──
        stackView.addArrangedSubview(createSectionHeader("Metric Testing"))
        
        let metricHint = UILabel()
        metricHint.text = "Tests all OTel metric instrument types.\nMetrics flush every 60s via PeriodicMetricReader."
        metricHint.font = .systemFont(ofSize: 12)
        metricHint.textColor = .secondaryLabel
        metricHint.textAlignment = .center
        metricHint.numberOfLines = 0
        stackView.addArrangedSubview(metricHint)
        
        stackView.addArrangedSubview(createButton(
            title: "Counter (Long, monotonic)",
            action: #selector(metricLongCounterTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Counter (Double, monotonic)",
            action: #selector(metricDoubleCounterTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "UpDownCounter (Long)",
            action: #selector(metricLongUpDownCounterTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "UpDownCounter (Double)",
            action: #selector(metricDoubleUpDownCounterTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Gauge (Double)",
            action: #selector(metricDoubleGaugeTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Gauge (Long)",
            action: #selector(metricLongGaugeTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Histogram (Double)",
            action: #selector(metricDoubleHistogramTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Histogram (Long + buckets)",
            action: #selector(metricLongHistogramWithBucketsTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Sum (Double UpDown)",
            action: #selector(metricDoubleSumTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Sum (Long UpDown)",
            action: #selector(metricLongSumTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "🔥 Fire All Metrics At Once",
            action: #selector(metricFireAllTapped),
            color: .systemPink
        ))
        
        stackView.addArrangedSubview(createSeparator())
        
        // ── Crash Testing ──
        stackView.addArrangedSubview(createSectionHeader("Crash Testing (will kill app!)"))
        
        let crashWarning = UILabel()
        crashWarning.text = "These buttons trigger REAL crashes. The crash report\nwill be sent as a device.crash OTel log on next launch."
        crashWarning.font = .systemFont(ofSize: 12)
        crashWarning.textColor = .secondaryLabel
        crashWarning.textAlignment = .center
        crashWarning.numberOfLines = 0
        stackView.addArrangedSubview(crashWarning)
        
        stackView.addArrangedSubview(createButton(
            title: "NSException (Obj-C)",
            action: #selector(crashNSExceptionTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "Fatal Error (Swift)",
            action: #selector(crashFatalErrorTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "Array Out of Bounds",
            action: #selector(crashArrayBoundsTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "Force Unwrap nil",
            action: #selector(crashForceUnwrapTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "Stack Overflow",
            action: #selector(crashStackOverflowTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "SIGABRT (abort())",
            action: #selector(crashAbortTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "Null Pointer Dereference (SIGSEGV)",
            action: #selector(crashNullPointerTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "Crash on Background Thread",
            action: #selector(crashBackgroundThreadTapped),
            color: .systemRed
        ))
        stackView.addArrangedSubview(createButton(
            title: "Crash on Named Thread",
            action: #selector(crashNamedThreadTapped),
            color: .systemRed
        ))
        
        stackView.addArrangedSubview(createSeparator())
        
        // ── Interaction Testing ──
        stackView.addArrangedSubview(createSectionHeader("Interaction Testing"))
        
        let event1Button = createButton(
            title: "Trigger Event1",
            action: #selector(event1Tapped),
            color: .systemGreen
        )
        stackView.addArrangedSubview(event1Button)
        
        let event2Button = createButton(
            title: "Trigger Event2",
            action: #selector(event2Tapped),
            color: .systemOrange
        )
        stackView.addArrangedSubview(event2Button)
        
        stackView.addArrangedSubview(createSeparator())

        // ── Tap Auto-Instrumentation ──
        stackView.addArrangedSubview(createSectionHeader("Tap Auto-Instrumentation"))

        let tapHint = UILabel()
        tapHint.text = "Opens a screen with every tappable UIKit element type.\nNo accessibilityLabel/ID set — tests automatic context extraction."
        tapHint.font = .systemFont(ofSize: 12)
        tapHint.textColor = .secondaryLabel
        tapHint.textAlignment = .center
        tapHint.numberOfLines = 0
        stackView.addArrangedSubview(tapHint)

        stackView.addArrangedSubview(createButton(
            title: "Tap Demo →",
            action: #selector(openTapDemoTapped),
            color: .systemCyan
        ))

        stackView.addArrangedSubview(createSeparator())

        // ── App Lifecycle Testing ──
        stackView.addArrangedSubview(createSectionHeader("App Lifecycle Testing"))
        
        let lifecycleHint = UILabel()
        lifecycleHint.text = "Push screens to trigger viewDidAppear swizzle.\nscreen.name and last.screen.name update on every transition.\nBackground/foreground the app to trigger device.app.lifecycle logs."
        lifecycleHint.font = .systemFont(ofSize: 12)
        lifecycleHint.textColor = .secondaryLabel
        lifecycleHint.textAlignment = .center
        lifecycleHint.numberOfLines = 0
        stackView.addArrangedSubview(lifecycleHint)
        
        stackView.addArrangedSubview(createButton(
            title: "Push Second Screen →",
            action: #selector(pushSecondScreenTapped),
            color: .systemIndigo
        ))
        stackView.addArrangedSubview(createButton(
            title: "Present Modal (pageSheet)",
            action: #selector(presentModalScreenTapped),
            color: .systemIndigo
        ))
        stackView.addArrangedSubview(createButton(
            title: "Present Modal (fullScreen)",
            action: #selector(presentFullScreenModalTapped),
            color: .systemIndigo
        ))
        stackView.addArrangedSubview(createButton(
            title: "Open TabBar (2 tabs)",
            action: #selector(openTabBarTapped),
            color: .systemIndigo
        ))
        stackView.addArrangedSubview(createButton(
            title: "Show Current Lifecycle State",
            action: #selector(showLifecycleStateTapped),
            color: .systemIndigo
        ))
        
        stackView.addArrangedSubview(createSeparator())
        
        // ── Session Replay Testing ──
        stackView.addArrangedSubview(createSectionHeader("Session Replay Testing"))
        
        stackView.addArrangedSubview(createButton(
            title: "Test PII Masking",
            action: #selector(testPIIMaskingTapped),
            color: .systemPurple
        ))
        stackView.addArrangedSubview(createButton(
            title: "Trigger Network Failure",
            action: #selector(networkFailureTapped),
            color: .systemPurple
        ))
        
        stackView.addArrangedSubview(createSeparator())
        
        // Status Label
        let statusLabel = UILabel()
        statusLabel.text = "SDK Status: Initialized"
        statusLabel.font = .systemFont(ofSize: 14)
        statusLabel.textColor = .systemGreen
        statusLabel.textAlignment = .center
        stackView.addArrangedSubview(statusLabel)
    }
    
    // MARK: - UI Helpers
    
    // SessionReplay is compiled into PulseKit when using CocoaPods
    private func addTestMaskingElements(to stackView: UIStackView) {
        // Add some test UI elements that should be masked
        let testSectionLabel = UILabel()
        testSectionLabel.text = "PII Masking Test Elements"
        testSectionLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        testSectionLabel.textColor = .systemOrange
        testSectionLabel.textAlignment = .center
        stackView.addArrangedSubview(testSectionLabel)
        
        // Test text field (should be masked by default)
        let testTextField = UITextField()
        testTextField.placeholder = "Enter sensitive data here"
        testTextField.borderStyle = .roundedRect
        testTextField.text = "This should be masked"
        testTextField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(testTextField)
        
        // Test password field (should always be masked)
        let passwordField = UITextField()
        passwordField.placeholder = "Password"
        passwordField.isSecureTextEntry = true
        passwordField.borderStyle = .roundedRect
        passwordField.text = "password123"
        passwordField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(passwordField)
        
        // Test label (should be masked if textAndInputPrivacy == .maskAll)
        let testLabel = UILabel()
        testLabel.text = "This label text should be masked if maskAll is enabled"
        testLabel.numberOfLines = 0
        testLabel.font = .systemFont(ofSize: 14)
        testLabel.textColor = .label
        stackView.addArrangedSubview(testLabel)
        
        // Test image view (should be masked if imagePrivacy == .maskAll)
        let testImageView = UIImageView()
        testImageView.backgroundColor = .systemBlue
        testImageView.contentMode = .scaleAspectFill
        testImageView.clipsToBounds = true
        testImageView.heightAnchor.constraint(equalToConstant: 100).isActive = true
        testImageView.layer.cornerRadius = 8
        // Create a simple colored image
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 100))
        testImageView.image = renderer.image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(origin: .zero, size: CGSize(width: 200, height: 100)))
        }
        stackView.addArrangedSubview(testImageView)
        
        let infoLabel = UILabel()
        infoLabel.text = "These elements above should be masked in screenshots."
        infoLabel.font = .systemFont(ofSize: 12)
        infoLabel.textColor = .secondaryLabel
        infoLabel.textAlignment = .center
        infoLabel.numberOfLines = 0
        stackView.addArrangedSubview(infoLabel)
    }
    
    private func createButton(title: String, action: Selector, color: UIColor = .systemBlue) -> UIButton {
        let button = UIButton(type: .system)
        button.setTitle(title, for: .normal)
        button.titleLabel?.font = .systemFont(ofSize: 16, weight: .medium)
        button.backgroundColor = color
        button.setTitleColor(.white, for: .normal)
        button.layer.cornerRadius = 8
        button.heightAnchor.constraint(equalToConstant: 50).isActive = true
        button.addTarget(self, action: action, for: .touchUpInside)
        return button
    }
    
    private func createSectionHeader(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 18, weight: .semibold)
        label.textAlignment = .center
        return label
    }
    
    private func createSeparator() -> UIView {
        let separator = UIView()
        separator.backgroundColor = .separator
        separator.heightAnchor.constraint(equalToConstant: 1).isActive = true
        return separator
    }
    
    // MARK: - Telemetry Testing
    
    @objc private func trackEventTapped() {
        print("━━━ trackEventTapped ━━━")
        let timestamp = Date().timeIntervalSince1970 * 1000
        Pulse.shared.trackEvent(
            name: "track_custom_event",
            observedTimeStampInMs: timestamp,
            params: [
                "button_name": AttributeValue.string("track_event"),
                "bool_attr": AttributeValue.bool(true),
                "int_attr": AttributeValue.int(123),
            ]
        )
        print("  Event: tract_custom_event")
        print("  Params: button_name=track_event, bool_attr=true, int_attr=123")
        showAlert(title: "Event Tracked", message: "Custom event 'button_clicked' has been tracked")
    }
    
    @objc private func trackNonFatalTapped() {
        print("━━━ trackNonFatalTapped ━━━")
        
        do {
            let invalidJSON = "{ invalid json }"
            let data = invalidJSON.data(using: .utf8)!
            _ = try JSONSerialization.jsonObject(with: data, options: [])
        } catch {
            let timestamp = Int64(Date().timeIntervalSince1970 * 1000)
            Pulse.shared.trackNonFatal(
                error: error,
                observedTimeStampInMs: timestamp,
                params: [
                    "error_source": AttributeValue.string("json_parsing"),
                    "screen": AttributeValue.string("main")
                ]
            )
            print("  Error: \(error.localizedDescription)")
            print("  Type: \(type(of: error))")
            showAlert(title: "Non-Fatal Tracked", message: "Non-fatal error caught and tracked")
        }
    }
    
    @objc private func trackSpanTapped() {
        print("━━━ trackSpanTapped ━━━")
        let result = Pulse.shared.trackSpan(
            name: "track_span",
            params: [
                "action": AttributeValue.string("track_span"),
                "method": AttributeValue.string("closure_based")
            ]
        ) {
            Thread.sleep(forTimeInterval: 0.5)
            return "Span completed"
        }
        print("  Span: track_span (closure)")
        print("  Result: \(result)")
        showAlert(title: "Span Tracked", message: "Span completed: \(result)")
    }
    
    @objc private func startSpanTapped() {
        print("━━━ startSpanTapped ━━━")
        let span = Pulse.shared.startSpan(
            name: "manual_created_span",
            params: [
                "action": AttributeValue.string("start_span_1"),
                "method": AttributeValue.string("manual")
            ]
        )
        print("  Span started: manual_created_span")
        print("  SpanContext: traceId=\(span.context.traceId), spanId=\(span.context.spanId)")
        
        DispatchQueue.global().asyncAfter(deadline: .now() + 1.0) {
            span.end()
            print("  Span ended: manual_created_span")
            DispatchQueue.main.async {
                self.showAlert(title: "Span Ended", message: "Manual span has been completed")
            }
        }
        
        showAlert(title: "Span Started", message: "Manual span has been started and will end in 1 second")
    }
    
    @objc private func networkRequestTapped() {
        print("━━━ networkRequestTapped ━━━")
        guard let url = URL(string: "https://httpbin.org/get") else { return }
        let task = URLSession.shared.dataTask(with: url) { data, response, error in
            DispatchQueue.main.async {
                if let error = error {
                    print("  Network error: \(error.localizedDescription)")
                    self.showAlert(title: "Network Error", message: error.localizedDescription)
                } else if let httpResponse = response as? HTTPURLResponse {
                    print("  HTTP \(httpResponse.statusCode)")
                    if httpResponse.statusCode >= 200 && httpResponse.statusCode < 300 {
                        self.showAlert(title: "Network Success", message: "Status: \(httpResponse.statusCode)")
                    } else {
                        self.showAlert(title: "HTTP Error", message: "Status: \(httpResponse.statusCode)")
                    }
                }
            }
        }
        task.resume()
    }
    
    // MARK: - OTel Direct API Testing
    
    @objc private func emitOtelLogTapped() {
        print("━━━ emitOtelLogTapped ━━━")
        guard let otel = Pulse.shared.getOtelOrNull() else {
            print("  ERROR: OTel not initialized")
            return
        }
        
        let logger = otel.loggerProvider.get(instrumentationScopeName: "com.pulse.example.logs")
        
        let severities: [(Severity, String)] = [
            (.trace, "TRACE"), (.debug, "DEBUG"), (.info, "INFO"),
            (.warn, "WARN"), (.error, "ERROR"), (.fatal, "FATAL")
        ]
        
        for (severity, name) in severities {
            let thread = Thread.current
            let threadId = "\(pthread_mach_thread_np(pthread_self()))"
            let threadName = thread.name ?? (Thread.isMainThread ? "main" : "unknown")
            
            logger.logRecordBuilder()
                .setSeverity(severity)
                .setBody(.string("Test log at \(name) severity"))
                .setEventName("test.log.\(name.lowercased())")
                .setAttributes([
                    "log.severity": .string(name),
                    "thread.id": .string(threadId),
                    "thread.name": .string(threadName),
                    "test.timestamp": .string(ISO8601DateFormatter().string(from: Date()))
                ])
                .emit()
            
            print("  [\(name)] thread.id=\(threadId) thread.name=\(threadName)")
        }
        
        showAlert(title: "OTel Logs Emitted", message: "6 log records emitted (TRACE through FATAL)")
    }
    
    @objc private func spanWithEventsTapped() {
        print("━━━ spanWithEventsTapped ━━━")
        guard let otel = Pulse.shared.getOtelOrNull() else {
            print("  ERROR: OTel not initialized")
            return
        }
        
        let tracer = otel.tracerProvider.get(
            instrumentationName: "com.pulse.example.traces",
            instrumentationVersion: "1.0.0"
        )
        
        let span = tracer.spanBuilder(spanName: "example.operation")
            .setSpanKind(spanKind: .client)
            .setAttribute(key: "http.method", value: "GET")
            .setAttribute(key: "http.url", value: "https://api.example.com/data")
            .setAttribute(key: "custom.user_id", value: "user-12345")
            .setAttribute(key: "http.request_content_length", value: 0)
            .setAttribute(key: "http.response_content_length", value: 4096)
            .startSpan()
        
        print("  Span started: example.operation")
        print("  traceId: \(span.context.traceId)")
        print("  spanId: \(span.context.spanId)")
        print("  kind: CLIENT")
        
        span.addEvent(name: "request.started", attributes: [
            "http.request_content_length": .int(0)
        ])
        print("  Event added: request.started")
        
        Thread.sleep(forTimeInterval: 0.2)
        
        span.addEvent(name: "response.received", attributes: [
            "http.status_code": .int(200),
            "http.response_content_length": .int(4096)
        ])
        print("  Event added: response.received")
        
        span.setAttribute(key: "http.status_code", value: 200)
        span.setAttribute(key: "http.duration", value: 150.0)
        span.end()
        print("  Span ended: example.operation")
        
        showAlert(title: "Span with Events", message: "Span 'example.operation' with 2 events emitted")
    }
    
    @objc private func nestedSpansTapped() {
        print("━━━ nestedSpansTapped ━━━")
        guard let otel = Pulse.shared.getOtelOrNull() else {
            print("  ERROR: OTel not initialized")
            return
        }
        
        let tracer = otel.tracerProvider.get(
            instrumentationName: "com.pulse.example.traces"
        )
        
        let parentSpan = tracer.spanBuilder(spanName: "parent.operation")
            .setSpanKind(spanKind: .internal)
            .setAttribute(key: "operation.type", value: "batch")
            .startSpan()
        
        print("  Parent span: traceId=\(parentSpan.context.traceId) spanId=\(parentSpan.context.spanId)")
        
        let childSpan1 = tracer.spanBuilder(spanName: "child.db_query")
            .setParent(parentSpan)
            .setSpanKind(spanKind: .client)
            .setAttribute(key: "db.system", value: "sqlite")
            .setAttribute(key: "db.statement", value: "SELECT * FROM users")
            .startSpan()
        
        print("  Child span 1: spanId=\(childSpan1.context.spanId) parent=\(parentSpan.context.spanId)")
        Thread.sleep(forTimeInterval: 0.1)
        childSpan1.setAttribute(key: "db.duration", value: 45.0)
        childSpan1.end()
        print("  Child span 1 ended: child.db_query")
        
        let childSpan2 = tracer.spanBuilder(spanName: "child.cache_write")
            .setParent(parentSpan)
            .setSpanKind(spanKind: .internal)
            .setAttribute(key: "cache.key", value: "users_list")
            .startSpan()
        
        print("  Child span 2: spanId=\(childSpan2.context.spanId) parent=\(parentSpan.context.spanId)")
        Thread.sleep(forTimeInterval: 0.05)
        childSpan2.end()
        print("  Child span 2 ended: child.cache_write")
        
        parentSpan.end()
        print("  Parent span ended: parent.operation")
        
        showAlert(title: "Nested Spans", message: "Parent + 2 child spans emitted (same traceId)")
    }
    
    @objc private func logWithThreadInfoTapped() {
        print("━━━ logWithThreadInfoTapped ━━━")
        guard let otel = Pulse.shared.getOtelOrNull() else {
            print("  ERROR: OTel not initialized")
            return
        }
        
        let logger = otel.loggerProvider.get(instrumentationScopeName: "com.pulse.example.threads")
        
        func emitFromThread(label: String) {
            let thread = Thread.current
            let threadId = "\(pthread_mach_thread_np(pthread_self()))"
            let threadName = thread.name ?? (Thread.isMainThread ? "main" : label)
            
            logger.logRecordBuilder()
                .setSeverity(.info)
                .setBody(.string("Log from \(label)"))
                .setEventName("test.thread_log")
                .setAttributes([
                    "thread.id": .string(threadId),
                    "thread.name": .string(threadName),
                    "thread.is_main": .bool(Thread.isMainThread),
                    "thread.label": .string(label),
                    "thread.priority": .double(thread.threadPriority)
                ])
                .emit()
            
            print("  [\(label)] thread.id=\(threadId) thread.name=\(threadName) isMain=\(Thread.isMainThread)")
        }
        
        emitFromThread(label: "main-thread")
        
        DispatchQueue.global(qos: .userInitiated).async {
            emitFromThread(label: "global-userInitiated")
        }
        
        DispatchQueue.global(qos: .background).async {
            emitFromThread(label: "global-background")
        }
        
        let customQueue = DispatchQueue(label: "com.pulse.example.custom-queue")
        customQueue.async {
            emitFromThread(label: "custom-queue")
        }
        
        let namedThread = Thread {
            Thread.current.name = "PulseTestWorker"
            emitFromThread(label: "named-thread")
        }
        namedThread.name = "PulseTestWorker"
        namedThread.start()
        
        showAlert(title: "Thread Logs", message: "5 logs emitted from different threads (check console)")
    }
    
    // MARK: - Metric Testing
    
    @objc private func metricLongCounterTapped() {
        print("━━━ metricLongCounterTapped ━━━")
        Pulse.shared.trackLongCounterMetric(name: "test.long_counter", value: 1, attributes: ["screen": .string("main"), "action": .string("tap")])
        print("  LongCounter 'test.long_counter' += 1")
        showAlert(title: "Long Counter", message: "Incremented test.long_counter by 1")
    }
    
    @objc private func metricDoubleCounterTapped() {
        print("━━━ metricDoubleCounterTapped ━━━")
        Pulse.shared.trackDoubleCounterMetric(name: "test.double_counter", value: 1.5, attributes: ["screen": .string("main")])
        print("  DoubleCounter 'test.double_counter' += 1.5")
        showAlert(title: "Double Counter", message: "Incremented test.double_counter by 1.5")
    }
    
    @objc private func metricLongUpDownCounterTapped() {
        print("━━━ metricLongUpDownCounterTapped ━━━")
        Pulse.shared.trackLongUpDownCounterMetric(name: "test.long_updown", value: -1, attributes: ["resource": .string("connections")])
        print("  LongUpDownCounter 'test.long_updown' += -1")
        showAlert(title: "Long UpDownCounter", message: "test.long_updown adjusted by -1")
    }
    
    @objc private func metricDoubleUpDownCounterTapped() {
        print("━━━ metricDoubleUpDownCounterTapped ━━━")
        Pulse.shared.trackDoubleUpDownCounterMetric(name: "test.double_updown", value: -2.5, attributes: ["resource": .string("memory_mb")])
        print("  DoubleUpDownCounter 'test.double_updown' += -2.5")
        showAlert(title: "Double UpDownCounter", message: "test.double_updown adjusted by -2.5")
    }
    
    @objc private func metricDoubleGaugeTapped() {
        print("━━━ metricDoubleGaugeTapped ━━━")
        let memoryMB = Double(ProcessInfo.processInfo.physicalMemory) / 1_048_576.0
        Pulse.shared.trackDoubleGaugeMetric(name: "test.double_gauge", value: memoryMB, attributes: ["unit": .string("MB")])
        print("  DoubleGauge 'test.double_gauge' = \(String(format: "%.1f", memoryMB)) MB")
        showAlert(title: "Double Gauge", message: "test.double_gauge set to \(String(format: "%.1f", memoryMB)) MB")
    }
    
    @objc private func metricLongGaugeTapped() {
        print("━━━ metricLongGaugeTapped ━━━")
        let activeThreads = Thread.isMainThread ? 1 : 0
        Pulse.shared.trackLongGaugeMetric(name: "test.long_gauge", value: activeThreads, attributes: ["type": .string("thread_count")])
        print("  LongGauge 'test.long_gauge' = \(activeThreads)")
        showAlert(title: "Long Gauge", message: "test.long_gauge set to \(activeThreads)")
    }
    
    @objc private func metricDoubleHistogramTapped() {
        print("━━━ metricDoubleHistogramTapped ━━━")
        let duration = Double.random(in: 50...500)
        Pulse.shared.trackDoubleHistogramMetric(name: "test.double_histogram", value: duration, attributes: ["operation": .string("api_call")])
        print("  DoubleHistogram 'test.double_histogram' recorded \(String(format: "%.1f", duration)) ms")
        showAlert(title: "Double Histogram", message: "test.double_histogram recorded \(String(format: "%.1f", duration)) ms")
    }
    
    @objc private func metricLongHistogramWithBucketsTapped() {
        print("━━━ metricLongHistogramWithBucketsTapped ━━━")
        let bytes = Int.random(in: 64...2048)
        Pulse.shared.trackLongHistogramMetric(name: "test.long_histogram_buckets", value: bytes, bucketBoundaries: [10.0, 50.0, 100.0, 250.0, 500.0, 1000.0], attributes: ["operation": .string("payload_size")])
        print("  LongHistogram 'test.long_histogram_buckets' recorded \(bytes) bytes (buckets: 10,50,100,250,500,1000)")
        showAlert(title: "Long Histogram + Buckets", message: "test.long_histogram_buckets recorded \(bytes) bytes")
    }
    
    @objc private func metricDoubleSumTapped() {
        print("━━━ metricDoubleSumTapped ━━━")
        let delta = Double.random(in: -10...10)
        Pulse.shared.trackDoubleSumMetric(name: "test.double_sum", value: delta, attributes: ["source": .string("sum_test")])
        print("  DoubleSum (UpDownCounter) 'test.double_sum' += \(String(format: "%.2f", delta))")
        showAlert(title: "Double Sum", message: "test.double_sum adjusted by \(String(format: "%.2f", delta))")
    }
    
    @objc private func metricLongSumTapped() {
        print("━━━ metricLongSumTapped ━━━")
        let delta = Int.random(in: -5...5)
        Pulse.shared.trackLongSumMetric(name: "test.long_sum", value: delta, attributes: ["source": .string("sum_test")])
        print("  LongSum (UpDownCounter) 'test.long_sum' += \(delta)")
        showAlert(title: "Long Sum", message: "test.long_sum adjusted by \(delta)")
    }
    
    @objc private func metricFireAllTapped() {
        print("━━━ metricFireAllTapped ━━━")
        metricLongCounterTapped()
        metricDoubleCounterTapped()
        metricLongUpDownCounterTapped()
        metricDoubleUpDownCounterTapped()
        metricDoubleGaugeTapped()
        metricLongGaugeTapped()
        metricDoubleHistogramTapped()
        metricLongHistogramWithBucketsTapped()
        metricDoubleSumTapped()
        metricLongSumTapped()
        print("━━━ All 10 metric types fired ━━━")
    }
    
    // MARK: - Crash Testing
    
    @objc private func crashNSExceptionTapped() {
        confirmCrash(type: "NSException") {
            NSException(
                name: NSExceptionName("TestCrashException"),
                reason: "Test Obj-C exception from Pulse iOS SDK",
                userInfo: nil
            ).raise()
        }
    }
    
    @objc private func crashFatalErrorTapped() {
        confirmCrash(type: "Swift fatalError") {
            fatalError("Test fatal error from Pulse iOS SDK")
        }
    }
    
    @objc private func crashArrayBoundsTapped() {
        confirmCrash(type: "Array out of bounds") {
            let array = [1, 2, 3]
            let _ = array[10]
        }
    }
    
    @objc private func crashForceUnwrapTapped() {
        confirmCrash(type: "Force unwrap nil") {
            let value: String? = nil
            let _ = value!
        }
    }
    
    @objc private func crashStackOverflowTapped() {
        confirmCrash(type: "Stack overflow") {
            self.infiniteRecursion()
        }
    }
    
    @objc private func crashAbortTapped() {
        confirmCrash(type: "SIGABRT (abort)") {
            abort()
        }
    }
    
    @objc private func crashNullPointerTapped() {
        confirmCrash(type: "Null pointer (SIGSEGV)") {
            let ptr = UnsafeMutablePointer<Int>(bitPattern: 0)!
            ptr.pointee = 42
        }
    }
    
    @objc private func crashBackgroundThreadTapped() {
        confirmCrash(type: "Background thread crash") {
            DispatchQueue.global(qos: .background).async {
                NSException(
                    name: NSExceptionName("BackgroundCrashException"),
                    reason: "Test crash on background thread",
                    userInfo: nil
                ).raise()
            }
        }
    }
    
    @objc private func crashNamedThreadTapped() {
        confirmCrash(type: "Named thread crash") {
            let thread = Thread {
                NSException(
                    name: NSExceptionName("NamedThreadCrashException"),
                    reason: "Test crash on named thread 'PulseCrashTestThread'",
                    userInfo: nil
                ).raise()
            }
            thread.name = "PulseCrashTestThread"
            thread.start()
        }
    }
    
    // MARK: - Interaction Testing
    
    @objc private func event1Tapped() {
        print("━━━ event1Tapped ━━━")
        let timestamp = Date().timeIntervalSince1970 * 1000
        Pulse.shared.trackEvent(
            name: "event1",
            observedTimeStampInMs: timestamp,
            params: [
                "source": AttributeValue.string("interaction_test"),
                "button": AttributeValue.string("event1_button")
            ]
        )
        showAlert(title: "Event1 Triggered", message: "Event 'event1' tracked. Trigger 'event2' to complete the interaction sequence.")
    }
    
    @objc private func event2Tapped() {
        print("━━━ event2Tapped ━━━")
        let timestamp = Date().timeIntervalSince1970 * 1000
        Pulse.shared.trackEvent(
            name: "event2",
            observedTimeStampInMs: timestamp,
            params: [
                "source": AttributeValue.string("interaction_test"),
                "button": AttributeValue.string("event2_button")
            ]
        )
        showAlert(title: "Event2 Triggered", message: "Event 'event2' tracked. If 'event1' was triggered first, the interaction sequence should be complete!")
    }
    
    // MARK: - App Lifecycle Testing
    
    @objc private func openTapDemoTapped() {
        navigationController?.pushViewController(TapDemoViewController(), animated: true)
    }

    @objc private func pushSecondScreenTapped() {
        print("━━━ pushSecondScreenTapped ━━━")
        print("  Pushing SecondViewController")
        print("  screen.name will change to \"SecondViewController\"")
        print("  last.screen.name will become \"MainViewController\"")
        navigationController?.pushViewController(SecondViewController(), animated: true)
    }
    
    @objc private func presentModalScreenTapped() {
        print("━━━ presentModalScreenTapped ━━━")
        let modal = ThirdViewController()
        modal.title = "Modal Screen"
        let nav = UINavigationController(rootViewController: modal)
        nav.modalPresentationStyle = .pageSheet
        modal.navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(dismissModal)
        )
        print("  Presenting ThirdViewController modally")
        print("  screen.name will change to \"ThirdViewController\"")
        present(nav, animated: true)
    }
    
    @objc private func dismissModal() {
        dismiss(animated: true)
    }
    
    @objc private func presentFullScreenModalTapped() {
        print("━━━ presentFullScreenModalTapped ━━━")
        let modal = ThirdViewController()
        modal.title = "Full Screen Modal"
        let nav = UINavigationController(rootViewController: modal)
        nav.modalPresentationStyle = .fullScreen
        modal.navigationItem.rightBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .done,
            target: self,
            action: #selector(dismissModal)
        )
        print("  fullScreen modal — MainVC WILL get viewWillDisappear")
        print("  Unlike pageSheet where MainVC stays partially visible")
        present(nav, animated: true)
    }

    @objc private func openTabBarTapped() {
        print("━━━ openTabBarTapped ━━━")
        print("  UITabBarController is filtered by shouldTrack")
        print("  But child VCs (TabAViewController, TabBViewController) are tracked")
        print("  Switching tabs fires viewWillDisappear/viewDidAppear = Restarted span")
        let tabBarVC = TabBarExampleViewController()
        present(tabBarVC, animated: true)
    }

    @objc private func showLifecycleStateTapped() {
        print("━━━ showLifecycleStateTapped ━━━")
        let state = AppStateWatcher.shared.currentState
        let message = "AppStateWatcher.currentState = \"\(state.rawValue)\"\n\nBackground the app and reopen to see foreground/background transitions in the console.\n\nEach transition emits a device.app.lifecycle log with ios.app.state attribute."
        print("  currentState: \(state.rawValue)")
        showAlert(title: "Lifecycle State: \(state.rawValue)", message: message)
    }
    
    // MARK: - Session Replay Testing
    
    @objc private func testPIIMaskingTapped() {
        if SessionReplayInstrumentation.getInstance() != nil {
            let testVC = PIIMaskingTestViewController()
            navigationController?.pushViewController(testVC, animated: true)
        } else {
            showAlert(title: "Not Available", message: "Session Replay is not initialized. Please ensure Session Replay is enabled in your PulseKit configuration.")
        }
    }
    
    @objc private func networkFailureTapped() {
        print("━━━ networkFailureTapped ━━━")
        // Use an invalid URL that will definitely fail to test network error handling in session replay
        guard let url = URL(string: "https://invalid-url-that-does-not-exist-12345.com/api/fail") else {
            showAlert(title: "Error", message: "Failed to create URL")
            return
        }
        
        var request = URLRequest(url: url)
        request.timeoutInterval = 5.0
        request.httpMethod = "GET"
        
        let task = URLSession.shared.dataTask(with: request) { [weak self] data, response, error in
            DispatchQueue.main.async {
                guard let self = self else { return }
                if let error = error {
                    print("  Network error: \(error.localizedDescription)")
                    print("  Error domain: \((error as NSError).domain)")
                    print("  Error code: \((error as NSError).code)")
                    self.showAlert(
                        title: "Network Failure",
                        message: "Network request failed as expected.\n\nError: \(error.localizedDescription)\n\nThis failure should be captured in session replay for E2E testing."
                    )
                } else if let httpResponse = response as? HTTPURLResponse {
                    print("  HTTP \(httpResponse.statusCode)")
                    self.showAlert(
                        title: "Unexpected Success",
                        message: "Request succeeded with status: \(httpResponse.statusCode)\n\nThis was unexpected - the URL should have failed."
                    )
                } else {
                    self.showAlert(
                        title: "Network Failure",
                        message: "Network request failed (no response).\n\nThis failure should be captured in session replay for E2E testing."
                    )
                }
            }
        }
        task.resume()
    }
    
    // MARK: - Helpers
    
    private func confirmCrash(type: String, action: @escaping () -> Void) {
        let alert = UIAlertController(
            title: "Trigger \(type)?",
            message: "This will crash the app. On next launch the crash report will be emitted as a device.crash log event.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        alert.addAction(UIAlertAction(title: "Crash", style: .destructive) { _ in
            action()
        })
        present(alert, animated: true)
    }
    
    private func infiniteRecursion() {
        infiniteRecursion()
    }
    
    private func showAlert(title: String, message: String) {
        print("  -> \(title): \(message)")
        let alert = UIAlertController(title: title, message: message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: "OK", style: .default))
        present(alert, animated: true)
    }
}

// MARK: - Session Replay Frame Viewer
/// View controller that displays captured session replay frames in a scrollable grid.
class SessionReplayFrameViewerViewController: UIViewController {
    private let frames: [SessionReplayFrame]
    private var collectionView: UICollectionView!
    
    init(frames: [SessionReplayFrame]) {
        self.frames = frames
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    private func setupUI() {
        title = "Captured Frames (\(frames.count))"
        view.backgroundColor = .systemBackground
        
        // Close button
        navigationItem.leftBarButtonItem = UIBarButtonItem(
            barButtonSystemItem: .close,
            target: self,
            action: #selector(closeTapped)
        )
        
        // Collection view layout
        let layout = UICollectionViewFlowLayout()
        layout.minimumInteritemSpacing = 10
        layout.minimumLineSpacing = 10
        layout.sectionInset = UIEdgeInsets(top: 10, left: 10, bottom: 10, right: 10)
        
        // Calculate item size (2 columns)
        let screenWidth = view.bounds.width
        let itemWidth = (screenWidth - 30) / 2 // 30 = 2*10 (insets) + 10 (spacing)
        layout.itemSize = CGSize(width: itemWidth, height: itemWidth * 1.5) // 2:3 aspect ratio
        
        collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.translatesAutoresizingMaskIntoConstraints = false
        collectionView.backgroundColor = .systemBackground
        collectionView.delegate = self
        collectionView.dataSource = self
        collectionView.register(FrameCell.self, forCellWithReuseIdentifier: "FrameCell")
        
        view.addSubview(collectionView)
        NSLayoutConstraint.activate([
            collectionView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            collectionView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            collectionView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            collectionView.bottomAnchor.constraint(equalTo: view.bottomAnchor)
        ])
    }
    
    @objc private func closeTapped() {
        dismiss(animated: true)
    }
}

extension SessionReplayFrameViewerViewController: UICollectionViewDataSource {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        return frames.count
    }
    
    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "FrameCell", for: indexPath) as! FrameCell
        let frame = frames[indexPath.item]
        cell.configure(with: frame)
        return cell
    }
}

extension SessionReplayFrameViewerViewController: UICollectionViewDelegate {
    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        let frame = frames[indexPath.item]
        let detailVC = SessionReplayFrameDetailViewController(frame: frame)
        navigationController?.pushViewController(detailVC, animated: true)
    }
}

// MARK: - Frame Cell

class FrameCell: UICollectionViewCell {
    private let imageView = UIImageView()
    private let infoLabel = UILabel()
    private let timestampLabel = UILabel()
    
    override init(frame: CGRect) {
        super.init(frame: frame)
        setupUI()
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    private func setupUI() {
        contentView.backgroundColor = .secondarySystemBackground
        contentView.layer.cornerRadius = 8
        contentView.layer.masksToBounds = true
        
        imageView.contentMode = .scaleAspectFill
        imageView.clipsToBounds = true
        imageView.backgroundColor = .tertiarySystemBackground
        
        infoLabel.font = .systemFont(ofSize: 10)
        infoLabel.textColor = .secondaryLabel
        infoLabel.textAlignment = .center
        infoLabel.numberOfLines = 2
        
        timestampLabel.font = .systemFont(ofSize: 9)
        timestampLabel.textColor = .tertiaryLabel
        timestampLabel.textAlignment = .center
        timestampLabel.numberOfLines = 1
        
        imageView.translatesAutoresizingMaskIntoConstraints = false
        infoLabel.translatesAutoresizingMaskIntoConstraints = false
        timestampLabel.translatesAutoresizingMaskIntoConstraints = false
        
        contentView.addSubview(imageView)
        contentView.addSubview(infoLabel)
        contentView.addSubview(timestampLabel)
        
        NSLayoutConstraint.activate([
            imageView.topAnchor.constraint(equalTo: contentView.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: contentView.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: contentView.trailingAnchor),
            imageView.heightAnchor.constraint(equalTo: contentView.heightAnchor, multiplier: 0.75),
            
            infoLabel.topAnchor.constraint(equalTo: imageView.bottomAnchor, constant: 4),
            infoLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 4),
            infoLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -4),
            
            timestampLabel.topAnchor.constraint(equalTo: infoLabel.bottomAnchor, constant: 2),
            timestampLabel.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 4),
            timestampLabel.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -4),
            timestampLabel.bottomAnchor.constraint(lessThanOrEqualTo: contentView.bottomAnchor, constant: -4)
        ])
    }
    
    func configure(with frame: SessionReplayFrame) {
        // Decode image from data
        if let image = UIImage(data: frame.imageData) {
            imageView.image = image
        } else {
            imageView.image = nil
            imageView.backgroundColor = .systemRed
        }
        
        // Format info
        let sizeKB = frame.imageData.count / 1024
        infoLabel.text = "\(frame.format.rawValue.uppercased())\n\(frame.width)×\(frame.height) • \(sizeKB) KB"
        
        // Format timestamp
        let formatter = DateFormatter()
        formatter.dateFormat = "HH:mm:ss"
        timestampLabel.text = formatter.string(from: frame.timestamp)
    }
}

// MARK: - Frame Detail View Controller

class SessionReplayFrameDetailViewController: UIViewController {
    private let frame: SessionReplayFrame
    private let scrollView = UIScrollView()
    private let imageView = UIImageView()
    private let infoStackView = UIStackView()
    
    init(frame: SessionReplayFrame) {
        self.frame = frame
        super.init(nibName: nil, bundle: nil)
    }
    
    required init?(coder: NSCoder) {
        fatalError("init(coder:) has not been implemented")
    }
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    private func setupUI() {
        title = "Frame Details"
        view.backgroundColor = .systemBackground
        
        // Decode and display image
        if let image = UIImage(data: frame.imageData) {
            imageView.image = image
            imageView.contentMode = .scaleAspectFit
            imageView.backgroundColor = .black
        } else {
            imageView.backgroundColor = .systemRed
            let label = UILabel()
            label.text = "Failed to decode image"
            label.textColor = .white
            label.textAlignment = .center
            imageView.addSubview(label)
            label.translatesAutoresizingMaskIntoConstraints = false
            NSLayoutConstraint.activate([
                label.centerXAnchor.constraint(equalTo: imageView.centerXAnchor),
                label.centerYAnchor.constraint(equalTo: imageView.centerYAnchor)
            ])
        }
        
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        imageView.translatesAutoresizingMaskIntoConstraints = false
        
        view.addSubview(scrollView)
        scrollView.addSubview(imageView)
        
        // Info labels
        infoStackView.axis = .vertical
        infoStackView.spacing = 8
        infoStackView.alignment = .leading
        infoStackView.translatesAutoresizingMaskIntoConstraints = false
        
        let formatLabel = createInfoLabel(title: "Format", value: frame.format.rawValue.uppercased())
        let sizeLabel = createInfoLabel(title: "Dimensions", value: "\(frame.width) × \(frame.height) pixels")
        let dataSizeLabel = createInfoLabel(title: "Data Size", value: "\(frame.imageData.count) bytes (\(frame.imageData.count / 1024) KB)")
        let sessionLabel = createInfoLabel(title: "Session ID", value: frame.sessionId)
        let screenLabel = createInfoLabel(title: "Screen", value: frame.screenName)
        
        let formatter = DateFormatter()
        formatter.dateStyle = .medium
        formatter.timeStyle = .medium
        let timestampLabel = createInfoLabel(title: "Timestamp", value: formatter.string(from: frame.timestamp))
        
        infoStackView.addArrangedSubview(formatLabel)
        infoStackView.addArrangedSubview(sizeLabel)
        infoStackView.addArrangedSubview(dataSizeLabel)
        infoStackView.addArrangedSubview(sessionLabel)
        infoStackView.addArrangedSubview(screenLabel)
        infoStackView.addArrangedSubview(timestampLabel)
        
        scrollView.addSubview(infoStackView)
        
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
            
            imageView.topAnchor.constraint(equalTo: scrollView.topAnchor),
            imageView.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor),
            imageView.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor),
            imageView.widthAnchor.constraint(equalTo: scrollView.widthAnchor),
            imageView.heightAnchor.constraint(equalTo: imageView.widthAnchor, multiplier: CGFloat(frame.height) / CGFloat(frame.width)),
            
            infoStackView.topAnchor.constraint(equalTo: imageView.bottomAnchor, constant: 20),
            infoStackView.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor, constant: 20),
            infoStackView.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor, constant: -20),
            infoStackView.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor, constant: -20)
        ])
    }
    
    private func createInfoLabel(title: String, value: String) -> UIView {
        let container = UIView()
        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        titleLabel.textColor = .label
        
        let valueLabel = UILabel()
        valueLabel.text = value
        valueLabel.font = .systemFont(ofSize: 14)
        valueLabel.textColor = .secondaryLabel
        valueLabel.numberOfLines = 0
        
        titleLabel.translatesAutoresizingMaskIntoConstraints = false
        valueLabel.translatesAutoresizingMaskIntoConstraints = false
        
        container.addSubview(titleLabel)
        container.addSubview(valueLabel)
        
        NSLayoutConstraint.activate([
            titleLabel.topAnchor.constraint(equalTo: container.topAnchor),
            titleLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            titleLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            
            valueLabel.topAnchor.constraint(equalTo: titleLabel.bottomAnchor, constant: 4),
            valueLabel.leadingAnchor.constraint(equalTo: container.leadingAnchor),
            valueLabel.trailingAnchor.constraint(equalTo: container.trailingAnchor),
            valueLabel.bottomAnchor.constraint(equalTo: container.bottomAnchor)
        ])
        
        return container
    }
}
