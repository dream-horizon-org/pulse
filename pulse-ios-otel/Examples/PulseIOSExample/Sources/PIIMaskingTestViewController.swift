import UIKit
import WebKit

// MARK: - Custom View Classes for Class-Level Override Testing

/// Custom view class that should always be masked (for Test 5)
class PrivateSecureView: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .systemRed.withAlphaComponent(0.2)
        layer.cornerRadius = 8
        layer.borderColor = UIColor.systemRed.cgColor
        layer.borderWidth = 2
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

/// Custom label class that should always be masked (for Test 5)
class PrivateDataLabel: UILabel {
    override init(frame: CGRect) {
        super.init(frame: frame)
        textColor = .systemRed
        font = .systemFont(ofSize: 16, weight: .semibold)
        backgroundColor = .systemRed.withAlphaComponent(0.1)
        layer.cornerRadius = 8
        layer.masksToBounds = true
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

/// Custom view class that should always be visible (for Test 6)
class PublicInfoView: UIView {
    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .systemGreen.withAlphaComponent(0.2)
        layer.cornerRadius = 8
        layer.borderColor = UIColor.systemGreen.cgColor
        layer.borderWidth = 2
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

/// Custom label class that should always be visible (for Test 6)
class SafePublicLabel: UILabel {
    override init(frame: CGRect) {
        super.init(frame: frame)
        textColor = .systemGreen
        font = .systemFont(ofSize: 16, weight: .semibold)
        backgroundColor = .systemGreen.withAlphaComponent(0.1)
        layer.cornerRadius = 8
        layer.masksToBounds = true
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
}

/// Comprehensive test screen for PII masking scenarios
/// Based on SESSION_REPLAY_PII_MASKING_TEST_PLAN.md
class PIIMaskingTestViewController: UIViewController {
    
    private let scrollView = UIScrollView()
    private let contentView = UIView()
    private let stackView = UIStackView()
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupUI()
    }
    
    private func setupUI() {
        title = "PII Masking Test"
        view.backgroundColor = .systemBackground
        
        // Setup scroll view
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
        
        // Setup stack view
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
        
        // Add all test elements
        addHeader()
        addLabels()
        addTextFields()
        addTextViews()
        addImages()
        addPickerView()
        addWebView()
        addInstanceOverrides()
        addClassOverrides()
        addInfoFooter()
    }
    
    // MARK: - Header
    
    private func addHeader() {
        let headerLabel = UILabel()
        headerLabel.text = "PII Masking Test Screen"
        headerLabel.font = .systemFont(ofSize: 24, weight: .bold)
        headerLabel.textAlignment = .center
        stackView.addArrangedSubview(headerLabel)
        
        let descriptionLabel = UILabel()
        descriptionLabel.text = "This screen contains all UI elements needed to test PII masking scenarios. Check screenshots in Pulse dashboard to verify masking behavior."
        descriptionLabel.font = .systemFont(ofSize: 14)
        descriptionLabel.textColor = .secondaryLabel
        descriptionLabel.textAlignment = .center
        descriptionLabel.numberOfLines = 0
        stackView.addArrangedSubview(descriptionLabel)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Labels Section
    
    private func addLabels() {
        stackView.addArrangedSubview(createSectionHeader("Labels (UILabel)"))
        
        // Regular label - should be visible with maskAllInputs, masked with maskAll
        let regularLabel = UILabel()
        regularLabel.text = "Welcome User - Regular Label"
        regularLabel.font = .systemFont(ofSize: 16)
        regularLabel.numberOfLines = 0
        stackView.addArrangedSubview(regularLabel)
        
        // Label with force unmask - should ALWAYS be visible (instance override)
        let alwaysVisibleLabel = UILabel()
        alwaysVisibleLabel.text = "✅ ALWAYS VISIBLE - Force Unmasked Label"
        alwaysVisibleLabel.font = .systemFont(ofSize: 16, weight: .bold)
        alwaysVisibleLabel.textColor = .systemGreen
        alwaysVisibleLabel.numberOfLines = 0
        // SessionReplay is compiled into PulseKit when using CocoaPods
        alwaysVisibleLabel.pulseReplayUnmask()
        stackView.addArrangedSubview(alwaysVisibleLabel)
        
        // Email label
        let emailLabel = UILabel()
        emailLabel.text = "Email Address"
        emailLabel.font = .systemFont(ofSize: 16)
        stackView.addArrangedSubview(emailLabel)
        
        // Phone label
        let phoneLabel = UILabel()
        phoneLabel.text = "Phone Number"
        phoneLabel.font = .systemFont(ofSize: 16)
        stackView.addArrangedSubview(phoneLabel)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Text Fields Section
    
    private func addTextFields() {
        stackView.addArrangedSubview(createSectionHeader("Text Fields (UITextField)"))
        
        // Regular text field - should be masked with maskAll/maskAllInputs, visible with maskSensitiveInputs
        let regularTextField = UITextField()
        regularTextField.placeholder = "Enter your name"
        regularTextField.borderStyle = .roundedRect
        regularTextField.text = "John Doe"
        regularTextField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(regularTextField)
        
        // Text field with force unmask - should ALWAYS be visible (instance override)
        let alwaysVisibleTextField = UITextField()
        alwaysVisibleTextField.placeholder = "✅ ALWAYS VISIBLE - Force Unmasked Field"
        alwaysVisibleTextField.borderStyle = .roundedRect
        alwaysVisibleTextField.text = "This should be visible"
        alwaysVisibleTextField.backgroundColor = .systemGreen.withAlphaComponent(0.1)
        alwaysVisibleTextField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        // SessionReplay is compiled into PulseKit when using CocoaPods
        alwaysVisibleTextField.pulseReplayUnmask()
        stackView.addArrangedSubview(alwaysVisibleTextField)
        
        // Password field - should always be masked
        let passwordField = UITextField()
        passwordField.placeholder = "Password"
        passwordField.isSecureTextEntry = true
        passwordField.borderStyle = .roundedRect
        passwordField.text = "secret123"
        passwordField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(passwordField)
        
        // Email field - should be masked with maskSensitiveInputs
        let emailField = UITextField()
        emailField.placeholder = "Email"
        emailField.textContentType = .emailAddress
        emailField.keyboardType = .emailAddress
        emailField.borderStyle = .roundedRect
        emailField.text = "user@example.com"
        emailField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(emailField)
        
        // Phone field - should be masked with maskSensitiveInputs
        let phoneField = UITextField()
        phoneField.placeholder = "Phone"
        phoneField.textContentType = .telephoneNumber
        phoneField.keyboardType = .phonePad
        phoneField.borderStyle = .roundedRect
        phoneField.text = "+1-555-123-4567"
        phoneField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(phoneField)
        
        // Password via textContentType
        let passwordField2 = UITextField()
        passwordField2.placeholder = "Password (via textContentType)"
        passwordField2.textContentType = .password
        passwordField2.borderStyle = .roundedRect
        passwordField2.text = "password456"
        passwordField2.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(passwordField2)
        
        // Password via accessibility label
        let passwordField3 = UITextField()
        passwordField3.placeholder = "Password (via accessibility)"
        passwordField3.accessibilityLabel = "password field"
        passwordField3.borderStyle = .roundedRect
        passwordField3.text = "password789"
        passwordField3.heightAnchor.constraint(equalToConstant: 44).isActive = true
        stackView.addArrangedSubview(passwordField3)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Text Views Section
    
    private func addTextViews() {
        stackView.addArrangedSubview(createSectionHeader("Text Views (UITextView)"))
        
        // Regular text view - should be masked with maskAll/maskAllInputs, visible with maskSensitiveInputs
        let regularTextView = UITextView()
        regularTextView.text = "Enter comments here - Regular text view"
        regularTextView.font = .systemFont(ofSize: 16)
        regularTextView.layer.borderColor = UIColor.systemGray4.cgColor
        regularTextView.layer.borderWidth = 1
        regularTextView.layer.cornerRadius = 8
        regularTextView.heightAnchor.constraint(equalToConstant: 80).isActive = true
        stackView.addArrangedSubview(regularTextView)
        
        // Password text view - should always be masked
        let passwordTextView = UITextView()
        passwordTextView.text = "Enter password here"
        passwordTextView.isSecureTextEntry = true
        passwordTextView.font = .systemFont(ofSize: 16)
        passwordTextView.layer.borderColor = UIColor.systemGray4.cgColor
        passwordTextView.layer.borderWidth = 1
        passwordTextView.layer.cornerRadius = 8
        passwordTextView.heightAnchor.constraint(equalToConstant: 80).isActive = true
        stackView.addArrangedSubview(passwordTextView)
        
        // Email text view - should be masked with maskSensitiveInputs
        let emailTextView = UITextView()
        emailTextView.text = "Enter email here"
        emailTextView.keyboardType = .emailAddress  // UITextView doesn't have textContentType, use keyboardType instead
        emailTextView.accessibilityLabel = "email"  // Also set accessibility label for better detection
        emailTextView.font = .systemFont(ofSize: 16)
        emailTextView.layer.borderColor = UIColor.systemGray4.cgColor
        emailTextView.layer.borderWidth = 1
        emailTextView.layer.cornerRadius = 8
        emailTextView.heightAnchor.constraint(equalToConstant: 80).isActive = true
        stackView.addArrangedSubview(emailTextView)
        
        // Phone text view - should be masked with maskSensitiveInputs
        let phoneTextView = UITextView()
        phoneTextView.text = "Enter phone here"
        phoneTextView.keyboardType = .phonePad  // UITextView doesn't have textContentType, use keyboardType instead
        phoneTextView.accessibilityLabel = "phone"  // Also set accessibility label for better detection
        phoneTextView.font = .systemFont(ofSize: 16)
        phoneTextView.layer.borderColor = UIColor.systemGray4.cgColor
        phoneTextView.layer.borderWidth = 1
        phoneTextView.layer.cornerRadius = 8
        phoneTextView.heightAnchor.constraint(equalToConstant: 80).isActive = true
        stackView.addArrangedSubview(phoneTextView)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Images Section
    
    private func addImages() {
        stackView.addArrangedSubview(createSectionHeader("Images (UIImageView)"))
        
        // Profile image - controlled by imagePrivacy setting
        let profileImageView = UIImageView()
        profileImageView.contentMode = .scaleAspectFill
        profileImageView.clipsToBounds = true
        profileImageView.layer.cornerRadius = 8
        profileImageView.backgroundColor = .systemBlue
        
        // Create a simple colored image
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 150))
        profileImageView.image = renderer.image { context in
            UIColor.systemBlue.setFill()
            context.fill(CGRect(origin: .zero, size: CGSize(width: 200, height: 150)))
            
            // Add some text to make it more visible
            let text = "Profile Image"
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 20, weight: .bold),
                .foregroundColor: UIColor.white
            ]
            let attributedString = NSAttributedString(string: text, attributes: attributes)
            let textSize = attributedString.size()
            let textRect = CGRect(
                x: (200 - textSize.width) / 2,
                y: (150 - textSize.height) / 2,
                width: textSize.width,
                height: textSize.height
            )
            attributedString.draw(in: textRect)
        }
        
        profileImageView.heightAnchor.constraint(equalToConstant: 150).isActive = true
        stackView.addArrangedSubview(profileImageView)
        
        // App logo image
        let logoImageView = UIImageView()
        logoImageView.contentMode = .scaleAspectFit
        logoImageView.backgroundColor = .systemGray6
        
        let logoRenderer = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 100))
        logoImageView.image = logoRenderer.image { context in
            UIColor.systemOrange.setFill()
            context.fill(CGRect(origin: .zero, size: CGSize(width: 200, height: 100)))
            
            let text = "App Logo"
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 18, weight: .semibold),
                .foregroundColor: UIColor.white
            ]
            let attributedString = NSAttributedString(string: text, attributes: attributes)
            let textSize = attributedString.size()
            let textRect = CGRect(
                x: (200 - textSize.width) / 2,
                y: (100 - textSize.height) / 2,
                width: textSize.width,
                height: textSize.height
            )
            attributedString.draw(in: textRect)
        }
        
        logoImageView.heightAnchor.constraint(equalToConstant: 100).isActive = true
        stackView.addArrangedSubview(logoImageView)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Picker View Section
    
    private func addPickerView() {
        stackView.addArrangedSubview(createSectionHeader("Picker View (UIPickerView)"))
        
        let pickerView = UIPickerView()
        pickerView.heightAnchor.constraint(equalToConstant: 120).isActive = true
        pickerView.delegate = self
        pickerView.dataSource = self
        stackView.addArrangedSubview(pickerView)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Web View Section
    
    private func addWebView() {
        stackView.addArrangedSubview(createSectionHeader("Web View (WKWebView)"))
        
        let webViewInfoLabel = UILabel()
        webViewInfoLabel.text = "WKWebView masking depends on textAndInputPrivacy and imagePrivacy settings"
        webViewInfoLabel.font = .systemFont(ofSize: 12)
        webViewInfoLabel.textColor = .secondaryLabel
        webViewInfoLabel.numberOfLines = 0
        stackView.addArrangedSubview(webViewInfoLabel)
        
        let webView = WKWebView()
        webView.heightAnchor.constraint(equalToConstant: 200).isActive = true
        webView.layer.borderColor = UIColor.systemGray4.cgColor
        webView.layer.borderWidth = 1
        webView.layer.cornerRadius = 8
        
        // Load a simple HTML page with a form
        let htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { font-family: -apple-system, BlinkMacSystemFont, sans-serif; padding: 20px; }
                input { width: 100%; padding: 10px; margin: 10px 0; border: 1px solid #ccc; border-radius: 5px; }
                button { padding: 10px 20px; background: #007AFF; color: white; border: none; border-radius: 5px; }
            </style>
        </head>
        <body>
            <h2>Test Form in WebView</h2>
            <input type="text" placeholder="Name" value="John Doe">
            <input type="email" placeholder="Email" value="user@example.com">
            <input type="password" placeholder="Password" value="secret123">
            <button>Submit</button>
        </body>
        </html>
        """
        webView.loadHTMLString(htmlContent, baseURL: nil)
        stackView.addArrangedSubview(webView)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Instance Overrides Section
    
    private func addInstanceOverrides() {
        stackView.addArrangedSubview(createSectionHeader("Instance-Level Overrides"))
        
        let overrideInfoLabel = UILabel()
        overrideInfoLabel.text = "These views have instance-level overrides applied (pulseReplayMask/unmask)"
        overrideInfoLabel.font = .systemFont(ofSize: 12)
        overrideInfoLabel.textColor = .secondaryLabel
        overrideInfoLabel.numberOfLines = 0
        stackView.addArrangedSubview(overrideInfoLabel)
        
        // Label with force unmask (should be visible even if config says maskAll)
        let unmaskedLabel = UILabel()
        unmaskedLabel.text = "✅ INSTANCE OVERRIDE: This label is force-unmasked (should ALWAYS be visible)"
        unmaskedLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        unmaskedLabel.textColor = .systemGreen
        unmaskedLabel.numberOfLines = 0
        unmaskedLabel.backgroundColor = .systemGreen.withAlphaComponent(0.1)
        unmaskedLabel.layer.cornerRadius = 8
        unmaskedLabel.layer.masksToBounds = true
        // SessionReplay is compiled into PulseKit when using CocoaPods
        unmaskedLabel.pulseReplayUnmask()
        stackView.addArrangedSubview(unmaskedLabel)
        
        // Text field with force mask (should be masked even if config says maskSensitiveInputs)
        let maskedTextField = UITextField()
        maskedTextField.placeholder = "❌ INSTANCE OVERRIDE: This field is force-masked (should ALWAYS be hidden)"
        maskedTextField.borderStyle = .roundedRect
        maskedTextField.text = "Force masked content"
        maskedTextField.backgroundColor = .systemRed.withAlphaComponent(0.1)
        maskedTextField.heightAnchor.constraint(equalToConstant: 44).isActive = true
        // SessionReplay is compiled into PulseKit when using CocoaPods
        maskedTextField.pulseReplayMask()
        stackView.addArrangedSubview(maskedTextField)
        
        // Image with force mask (should be masked even if imagePrivacy == .maskNone)
        let maskedImageView = UIImageView()
        maskedImageView.contentMode = .scaleAspectFill
        maskedImageView.clipsToBounds = true
        maskedImageView.layer.cornerRadius = 8
        maskedImageView.backgroundColor = .systemRed
        
        let maskedImageRenderer = UIGraphicsImageRenderer(size: CGSize(width: 200, height: 100))
        maskedImageView.image = maskedImageRenderer.image { context in
            UIColor.systemRed.setFill()
            context.fill(CGRect(origin: .zero, size: CGSize(width: 200, height: 100)))
            
            let text = "Force Masked Image"
            let attributes: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 16, weight: .semibold),
                .foregroundColor: UIColor.white
            ]
            let attributedString = NSAttributedString(string: text, attributes: attributes)
            let textSize = attributedString.size()
            let textRect = CGRect(
                x: (200 - textSize.width) / 2,
                y: (100 - textSize.height) / 2,
                width: textSize.width,
                height: textSize.height
            )
            attributedString.draw(in: textRect)
        }
        
        maskedImageView.heightAnchor.constraint(equalToConstant: 100).isActive = true
        // SessionReplay is compiled into PulseKit when using CocoaPods
        maskedImageView.pulseReplayMask()
        stackView.addArrangedSubview(maskedImageView)
        
        // Alternative: Using accessibility label
        let accessibilityLabel = UILabel()
        accessibilityLabel.text = "❌ INSTANCE OVERRIDE: This label uses accessibilityLabel = 'pulse-mask' (should ALWAYS be hidden)"
        accessibilityLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        accessibilityLabel.textColor = .systemOrange
        accessibilityLabel.numberOfLines = 0
        accessibilityLabel.backgroundColor = .systemOrange.withAlphaComponent(0.1)
        accessibilityLabel.layer.cornerRadius = 8
        accessibilityLabel.layer.masksToBounds = true
        accessibilityLabel.accessibilityLabel = "pulse-mask"
        stackView.addArrangedSubview(accessibilityLabel)
        
        // Alternative: Using accessibility identifier
        let accessibilityIdLabel = UILabel()
        accessibilityIdLabel.text = "✅ INSTANCE OVERRIDE: This label uses accessibilityIdentifier = 'pulse-unmask' (should ALWAYS be visible)"
        accessibilityIdLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        accessibilityIdLabel.textColor = .systemBlue
        accessibilityIdLabel.numberOfLines = 0
        accessibilityIdLabel.backgroundColor = .systemBlue.withAlphaComponent(0.1)
        accessibilityIdLabel.layer.cornerRadius = 8
        accessibilityIdLabel.layer.masksToBounds = true
        accessibilityIdLabel.accessibilityIdentifier = "pulse-unmask"
        stackView.addArrangedSubview(accessibilityIdLabel)
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Class-Level Overrides Section
    
    private func addClassOverrides() {
        stackView.addArrangedSubview(createSectionHeader("Class-Level Overrides (Level 2 Priority)"))
        
        let classOverrideInfoLabel = UILabel()
        classOverrideInfoLabel.text = """
        These views use custom classes that can be registered in config.maskViewClasses or config.unmaskViewClasses.
        
        To test:
        1. Get the fully-qualified class name: String(describing: type(of: view))
        2. Add to config.maskViewClasses to always mask these classes
        3. Add to config.unmaskViewClasses to always unmask these classes
        
        Class names in this app:
        - PrivateSecureView (red border)
        - PrivateDataLabel (red text)
        - PublicInfoView (green border)
        - SafePublicLabel (green text)
        """
        classOverrideInfoLabel.font = .systemFont(ofSize: 12)
        classOverrideInfoLabel.textColor = .secondaryLabel
        classOverrideInfoLabel.numberOfLines = 0
        stackView.addArrangedSubview(classOverrideInfoLabel)
        
        // PrivateSecureView - should be masked when registered in maskViewClasses
        let privateView = PrivateSecureView()
        let privateViewLabel = UILabel()
        privateViewLabel.text = "PrivateSecureView instance (red border)"
        privateViewLabel.font = .systemFont(ofSize: 14)
        privateViewLabel.textColor = .systemRed
        stackView.addArrangedSubview(privateViewLabel)
        privateView.heightAnchor.constraint(equalToConstant: 60).isActive = true
        stackView.addArrangedSubview(privateView)
        
        // PrivateDataLabel - should be masked when registered in maskViewClasses
        let privateLabel = PrivateDataLabel()
        privateLabel.text = "PrivateDataLabel instance (should be masked when class is registered)"
        privateLabel.numberOfLines = 0
        stackView.addArrangedSubview(privateLabel)
        
        // PublicInfoView - should be visible when registered in unmaskViewClasses
        let publicView = PublicInfoView()
        let publicViewLabel = UILabel()
        publicViewLabel.text = "PublicInfoView instance (green border)"
        publicViewLabel.font = .systemFont(ofSize: 14)
        publicViewLabel.textColor = .systemGreen
        stackView.addArrangedSubview(publicViewLabel)
        publicView.heightAnchor.constraint(equalToConstant: 60).isActive = true
        stackView.addArrangedSubview(publicView)
        
        // SafePublicLabel - should be visible when registered in unmaskViewClasses
        let publicLabel = SafePublicLabel()
        publicLabel.text = "SafePublicLabel instance (should be visible when class is registered)"
        publicLabel.numberOfLines = 0
        stackView.addArrangedSubview(publicLabel)
        
        // Debug: Print class names to console
        #if DEBUG
        print("[PIIMaskingTest] Class names for registration:")
        print("  PrivateSecureView: \(String(describing: type(of: privateView)))")
        print("  PrivateDataLabel: \(String(describing: type(of: privateLabel)))")
        print("  PublicInfoView: \(String(describing: type(of: publicView)))")
        print("  SafePublicLabel: \(String(describing: type(of: publicLabel)))")
        #endif
        
        stackView.addArrangedSubview(createSeparator())
    }
    
    // MARK: - Footer
    
    private func addInfoFooter() {
        let footerLabel = UILabel()
        footerLabel.text = """
        Test Instructions:
        1. Navigate to this screen
        2. Wait for session replay to capture screenshots
        3. Check Pulse dashboard to verify masking behavior
        4. Test different privacy configurations:
           - maskAll (default)
           - maskAllInputs
           - maskSensitiveInputs
           - imagePrivacy: maskAll / maskNone
        
        See SESSION_REPLAY_PII_MASKING_TEST_PLAN.md for detailed test scenarios.
        """
        footerLabel.font = .systemFont(ofSize: 12)
        footerLabel.textColor = .secondaryLabel
        footerLabel.numberOfLines = 0
        stackView.addArrangedSubview(footerLabel)
    }
    
    // MARK: - UI Helpers
    
    private func createSectionHeader(_ text: String) -> UILabel {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 18, weight: .semibold)
        label.textColor = .label
        return label
    }
    
    private func createSeparator() -> UIView {
        let separator = UIView()
        separator.backgroundColor = .separator
        separator.heightAnchor.constraint(equalToConstant: 1).isActive = true
        return separator
    }
}

// MARK: - UIPickerViewDataSource & UIPickerViewDelegate

extension PIIMaskingTestViewController: UIPickerViewDataSource, UIPickerViewDelegate {
    func numberOfComponents(in pickerView: UIPickerView) -> Int {
        return 1
    }
    
    func pickerView(_ pickerView: UIPickerView, numberOfRowsInComponent component: Int) -> Int {
        return 5
    }
    
    func pickerView(_ pickerView: UIPickerView, titleForRow row: Int, forComponent component: Int) -> String? {
        return "Option \(row + 1)"
    }
}
