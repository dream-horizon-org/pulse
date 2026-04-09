import UIKit

/// Demonstrates UIKit tap auto-instrumentation across every tappable element type.
/// NO accessibilityLabel or accessibilityIdentifier is set on any element intentionally —
/// the SDK should extract context purely from view content and hierarchy.
class TapDemoViewController: UIViewController {

    private let scrollView = UIScrollView()
    private let stack = UIStackView()

    override func viewDidLoad() {
        super.viewDidLoad()
        title = "Tap Demo"
        view.backgroundColor = .systemBackground
        setupScrollView()
        buildSections()
    }

    // MARK: - Layout

    private func setupScrollView() {
        scrollView.translatesAutoresizingMaskIntoConstraints = false
        view.addSubview(scrollView)
        NSLayoutConstraint.activate([
            scrollView.topAnchor.constraint(equalTo: view.safeAreaLayoutGuide.topAnchor),
            scrollView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            scrollView.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            scrollView.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])

        stack.axis = .vertical
        stack.spacing = 12
        stack.translatesAutoresizingMaskIntoConstraints = false
        scrollView.addSubview(stack)
        NSLayoutConstraint.activate([
            stack.topAnchor.constraint(equalTo: scrollView.topAnchor, constant: 20),
            stack.leadingAnchor.constraint(equalTo: scrollView.leadingAnchor, constant: 20),
            stack.trailingAnchor.constraint(equalTo: scrollView.trailingAnchor, constant: -20),
            stack.bottomAnchor.constraint(equalTo: scrollView.bottomAnchor, constant: -40),
            stack.widthAnchor.constraint(equalTo: scrollView.widthAnchor, constant: -40),
        ])
    }

    private func buildSections() {
        addHint(
            "UIKit rows: no accessibility set (SDK infers labels where possible).\n"
                + "Debug builds: [Pulse] tap hitTest → … | resolved → … then app.widget.click."
        )

        // ── 1. UIButton with text ──────────────────────────────────────────────
        // Expected: label=Add to Cart, element=button (from titleLabel.text)
        addSectionHeader("UIButton — text title")
        addNote("Expected → label=Add to Cart; source=view; element=button")

        let textButton = UIButton(type: .system)
        textButton.setTitle("Add to Cart", for: .normal)
        textButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .semibold)
        textButton.backgroundColor = .systemBlue
        textButton.setTitleColor(.white, for: .normal)
        textButton.layer.cornerRadius = 10
        textButton.addTarget(self, action: #selector(buttonTapped(_:)), for: .touchUpInside)
        addElement(textButton, height: 50)

        // ── 2. UIButton icon-only (no text) ────────────────────────────────────
        // Expected: no label (nothing to extract), element=button, widget.name=UIButton
        addSectionHeader("UIButton — icon only (no text)")
        addNote("Expected → source=view; element=button | widget.name=UIButton")

        let iconButton = UIButton(type: .system)
        iconButton.setImage(UIImage(systemName: "heart.fill"), for: .normal)
        iconButton.tintColor = .systemRed
        iconButton.backgroundColor = .systemRed.withAlphaComponent(0.1)
        iconButton.layer.cornerRadius = 10
        iconButton.addTarget(self, action: #selector(buttonTapped(_:)), for: .touchUpInside)
        addElement(iconButton, height: 50)

        // ── 3. UISegmentedControl ──────────────────────────────────────────────
        // Expected: element=nil (not UIButton/UIImageView/cell), widget.name=UISegmentedControl
        addSectionHeader("UISegmentedControl")
        addNote("Expected → source=view | widget.name=UISegmentedControl (no label extractable)")

        let segmented = UISegmentedControl(items: ["Daily", "Weekly", "Monthly"])
        segmented.selectedSegmentIndex = 0
        addElement(segmented, height: 34)

        // ── 4. UISwitch ────────────────────────────────────────────────────────
        // Expected: no label, widget.name=UISwitch
        addSectionHeader("UISwitch")
        addNote("Expected → source=view | widget.name=UISwitch")

        let switchContainer = UIView()
        let toggle = UISwitch()
        toggle.translatesAutoresizingMaskIntoConstraints = false
        switchContainer.addSubview(toggle)
        NSLayoutConstraint.activate([
            toggle.centerXAnchor.constraint(equalTo: switchContainer.centerXAnchor),
            toggle.topAnchor.constraint(equalTo: switchContainer.topAnchor),
            toggle.bottomAnchor.constraint(equalTo: switchContainer.bottomAnchor),
            switchContainer.heightAnchor.constraint(equalToConstant: 40),
        ])
        stack.addArrangedSubview(switchContainer)

        // ── 5. UIStepper ───────────────────────────────────────────────────────
        // Expected: no label, widget.name=UIStepper
        addSectionHeader("UIStepper")
        addNote("Expected → source=view | widget.name=UIStepper")

        let stepperContainer = UIView()
        let stepper = UIStepper()
        stepper.translatesAutoresizingMaskIntoConstraints = false
        stepperContainer.addSubview(stepper)
        NSLayoutConstraint.activate([
            stepper.centerXAnchor.constraint(equalTo: stepperContainer.centerXAnchor),
            stepper.topAnchor.constraint(equalTo: stepperContainer.topAnchor),
            stepper.bottomAnchor.constraint(equalTo: stepperContainer.bottomAnchor),
            stepperContainer.heightAnchor.constraint(equalToConstant: 44),
        ])
        stack.addArrangedSubview(stepperContainer)

        // ── 6. UIView card — nested labels ─────────────────────────────────────
        // Expected: label=Premium Plan | $9.99/month (from recursive UILabel scan)
        addSectionHeader("UIView card (tap gesture, nested labels)")
        addNote("Expected → label=Premium Plan | $9.99/month; source=view")

        let card = makeCard(title: "Premium Plan", subtitle: "$9.99/month", color: .systemPurple)
        let cardTap = UITapGestureRecognizer(target: self, action: #selector(cardTapped(_:)))
        card.addGestureRecognizer(cardTap)
        card.isUserInteractionEnabled = true
        addElement(card, height: 80)

        // ── 7. UIView card — single label ──────────────────────────────────────
        // Expected: label=Free Shipping on orders above $50 (direct UILabel subview)
        addSectionHeader("UIView banner (single label, tap gesture)")
        addNote("Expected → label=Free Shipping on orders above $50; source=view")

        let banner = makeBanner(text: "Free Shipping on orders above $50", color: .systemGreen)
        let bannerTap = UITapGestureRecognizer(target: self, action: #selector(cardTapped(_:)))
        banner.addGestureRecognizer(bannerTap)
        banner.isUserInteractionEnabled = true
        addElement(banner, height: 54)

        // ── 8. UIImageView — tap gesture, no text ─────────────────────────────
        // Expected: no label, element=image, widget.name=UIImageView
        addSectionHeader("UIImageView (tap gesture, image only)")
        addNote("Expected → source=view; element=image | widget.name=UIImageView")

        let imageView = UIImageView(image: UIImage(systemName: "photo.fill.on.rectangle.fill"))
        imageView.contentMode = .scaleAspectFit
        imageView.tintColor = .systemOrange
        imageView.backgroundColor = .systemOrange.withAlphaComponent(0.1)
        imageView.layer.cornerRadius = 10
        imageView.clipsToBounds = true
        imageView.isUserInteractionEnabled = true
        let imageTap = UITapGestureRecognizer(target: self, action: #selector(imageTapped))
        imageView.addGestureRecognizer(imageTap)
        addElement(imageView, height: 80)

        // ── 9. UITableView — cells with text content ───────────────────────────
        // Expected: label=<product name> | <brand> (from recursive scan finding textLabel + detailTextLabel)
        addSectionHeader("UITableView cells (subtitle style)")
        addNote("Expected → label=<product> | <brand> (recursive label scan on cell)")

        let tableView = IntrinsicTableView()
        tableView.dataSource = self
        tableView.delegate = self
        tableView.register(UITableViewCell.self, forCellReuseIdentifier: "cell")
        tableView.isScrollEnabled = false
        tableView.layer.cornerRadius = 10
        tableView.layer.borderColor = UIColor.separator.cgColor
        tableView.layer.borderWidth = 0.5
        tableView.tag = 100
        stack.addArrangedSubview(tableView)

        // ── 10. UICollectionView — cells with text labels ──────────────────────
        // Expected: label=<category name> (from UILabel inside cell via recursive scan)
        addSectionHeader("UICollectionView cells (label inside cell)")
        addNote("Expected → label=<category> (recursive scan finds UILabel in cell)")

        let layout = UICollectionViewFlowLayout()
        layout.scrollDirection = .horizontal
        layout.itemSize = CGSize(width: 100, height: 80)
        layout.minimumInteritemSpacing = 10
        layout.sectionInset = UIEdgeInsets(top: 0, left: 4, bottom: 0, right: 4)

        let collectionView = UICollectionView(frame: .zero, collectionViewLayout: layout)
        collectionView.dataSource = self
        collectionView.delegate = self
        collectionView.register(DemoCollectionCell.self, forCellWithReuseIdentifier: "cvcell")
        collectionView.backgroundColor = .secondarySystemBackground
        collectionView.layer.cornerRadius = 10
        collectionView.showsHorizontalScrollIndicator = false
        collectionView.heightAnchor.constraint(equalToConstant: 90).isActive = true
        stack.addArrangedSubview(collectionView)

        // ── 11. Scroll detection ───────────────────────────────────────────────
        // The collection above has enough items to scroll horizontally.
        // The outer page scroll view can also be scrolled.
        // Expected: scrolling fires ZERO app.widget.click events.
        addSectionHeader("Scroll detection (negative test)")
        addWarning("Scroll anywhere on this screen or the collection above.\nExpected → ZERO app.widget.click events fired during or after scroll.\n\nNote: Taps on blank areas (not on any element) emit click.type='dead'.")
        
        // ── 11.5. Dead Click Demo ───────────────────────────────────────────────
        // Dead clicks: taps on NON-interactive areas (no target view, no gesture recognizer)
        // Expected: click.type='dead' with NO widget.name or context
        addSectionHeader("Dead Click Demo — Tap Empty Spaces")
        addNote("Tap ANYWHERE in the gray box below (not a button, no gesture). SDK logs [DEAD_CLICK] with coords.")
        
        let deadClickDemoContainer = UIView()
        deadClickDemoContainer.backgroundColor = .systemGray5
        deadClickDemoContainer.layer.cornerRadius = 12
        deadClickDemoContainer.layer.borderColor = UIColor.systemRed.withAlphaComponent(0.3).cgColor
        deadClickDemoContainer.layer.borderWidth = 2
        deadClickDemoContainer.clipsToBounds = true
        
        let deadClickLabel = UILabel()
        deadClickLabel.text = "← Tap anywhere here\n(dead click zone)"
        deadClickLabel.font = .systemFont(ofSize: 14, weight: .semibold)
        deadClickLabel.textColor = .systemRed
        deadClickLabel.numberOfLines = 0
        deadClickLabel.textAlignment = .center
        deadClickLabel.isUserInteractionEnabled = false
        
        deadClickDemoContainer.addSubview(deadClickLabel)
        deadClickLabel.translatesAutoresizingMaskIntoConstraints = false
        NSLayoutConstraint.activate([
            deadClickLabel.centerXAnchor.constraint(equalTo: deadClickDemoContainer.centerXAnchor),
            deadClickLabel.centerYAnchor.constraint(equalTo: deadClickDemoContainer.centerYAnchor),
        ])
        
        addElement(deadClickDemoContainer, height: 100)
        
        addNote("Check debug logs: [DEAD_CLICK] type=dead | coords=(...) | norm=(...) | viewport=...")

        // ── 12. UITextField — WITH accessibilityLabel (content description) ────
        // Expected: label=Email Address (developer-set, not PII)
        addSectionHeader("UITextField — with accessibilityLabel")
        addNote("accessibilityLabel IS set here intentionally — it is developer metadata, not PII.\nExpected → label=Email Address; source=view | widget.name=Email Address")

        let emailField = UITextField()
        emailField.borderStyle = .roundedRect
        emailField.placeholder = "type something here (not captured)"
        emailField.accessibilityLabel = "Email Address"   // developer-set content description ✅
        // NO accessibilityIdentifier
        addElement(emailField, height: 44)

        // ── 13. UITextField — WITHOUT accessibilityLabel ───────────────────────
        // Expected: no label at all — typed text is PII and must never appear
        addSectionHeader("UITextField — no accessibilityLabel (PII safe)")
        addNote("Type anything in here. It must NEVER appear in the payload.\nExpected → no label field; source=view | widget.name=UITextField")

        let plainField = UITextField()
        plainField.borderStyle = .roundedRect
        plainField.placeholder = "type your password here…"
        // NO accessibilityLabel, NO accessibilityIdentifier — deliberately
        addElement(plainField, height: 44)

        // ── 14. UITextView — WITH accessibilityLabel ───────────────────────────
        // Expected: label=Comments Box (developer-set), typed text never captured
        addSectionHeader("UITextView — with accessibilityLabel")
        addNote("Multiline input. accessibilityLabel is developer metadata.\nExpected → label=Comments Box; source=view | widget.name=Comments Box")

        let textView = UITextView()
        textView.text = "Tap me — my text content must not appear in the payload"
        textView.font = .systemFont(ofSize: 14)
        textView.layer.borderColor = UIColor.separator.cgColor
        textView.layer.borderWidth = 0.5
        textView.layer.cornerRadius = 8
        textView.isEditable = false   // read-only so it behaves like a tappable view
        textView.accessibilityLabel = "Comments Box"   // developer-set content description ✅
        // NO accessibilityIdentifier
        addElement(textView, height: 80)

        // ── 15. UIView with tap gesture — zero labels ──────────────────────────
        // Expected: no label, widget.name = UIView (pure class name fallback)
        addSectionHeader("UIView — no labels, no accessibility (class name fallback)")
        addNote("Expected → source=view | widget.name=UIView (nothing to extract)")

        let emptyCard = UIView()
        emptyCard.backgroundColor = .systemGray5
        emptyCard.layer.cornerRadius = 10
        let emptyTap = UITapGestureRecognizer(target: self, action: #selector(cardTapped(_:)))
        emptyCard.addGestureRecognizer(emptyTap)
        emptyCard.isUserInteractionEnabled = true
        addElement(emptyCard, height: 60)
        
        // ── 16. Rage Click Demo ─────────────────────────────────────────────────
        // Rage clicks: 3+ taps within 2000ms + 50pt radius
        // Expected: click.is_rage=true, click.rageCount=N
        addSectionHeader("Rage Click Demo — Rapid Taps")
        addNote("TAP RAPIDLY (3+ times quickly) in the red button below. SDK logs [RAGE_CLICK] when threshold hit.")
        
        let rageButton = UIButton(type: .system)
        rageButton.setTitle("🔥 Tap me rapidly!", for: .normal)
        rageButton.titleLabel?.font = .systemFont(ofSize: 16, weight: .bold)
        rageButton.backgroundColor = .systemRed
        rageButton.setTitleColor(.white, for: .normal)
        rageButton.layer.cornerRadius = 12
        rageButton.addTarget(self, action: #selector(rageButtonTapped(_:)), for: .touchUpInside)
        addElement(rageButton, height: 60)
        
        addWarning("Tap the red button 3+ times in quick succession (within 2 seconds, same location). SDK will emit [RAGE_CLICK] with rage_count.\nAll individual taps during rage window are suppressed.")
    }
    
    // MARK: - Actions

    @objc private func buttonTapped(_ sender: UIButton) {
        print("[TapDemo] UIButton tapped — SDK should have logged app.widget.click above this line")
    }

    @objc private func rageButtonTapped(_ sender: UIButton) {
        print("[TapDemo] Rage button tapped — if 3+ taps rapid, SDK logs [RAGE_CLICK]")
    }

    @objc private func cardTapped(_ sender: UITapGestureRecognizer) {
        print("[TapDemo] UIView card tapped — SDK should have logged app.widget.click above this line")
    }

    @objc private func imageTapped() {
        print("[TapDemo] UIImageView tapped — SDK should have logged app.widget.click above this line")
    }

    // MARK: - Helpers

    private func addSectionHeader(_ text: String) {
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 13, weight: .semibold)
        label.textColor = .secondaryLabel
        label.textTransform()
        stack.addArrangedSubview(label)
        stack.setCustomSpacing(4, after: label)
    }

    private func addNote(_ text: String) {
        let label = UILabel()
        label.text = text
        label.font = .monospacedSystemFont(ofSize: 11, weight: .regular)
        label.textColor = .tertiaryLabel
        label.numberOfLines = 0
        stack.addArrangedSubview(label)
        stack.setCustomSpacing(6, after: label)
    }

    private func addWarning(_ text: String) {
        let container = UIView()
        container.backgroundColor = .systemOrange.withAlphaComponent(0.1)
        container.layer.cornerRadius = 10
        let label = UILabel()
        label.text = "⚠️ " + text
        label.font = .systemFont(ofSize: 13)
        label.textColor = .systemOrange
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: container.topAnchor, constant: 12),
            label.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -12),
            label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 12),
            label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -12),
        ])
        stack.addArrangedSubview(container)
    }

    private func addHint(_ text: String) {
        let container = UIView()
        container.backgroundColor = .systemBlue.withAlphaComponent(0.08)
        container.layer.cornerRadius = 10
        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 13)
        label.textColor = .systemBlue
        label.numberOfLines = 0
        label.translatesAutoresizingMaskIntoConstraints = false
        container.addSubview(label)
        NSLayoutConstraint.activate([
            label.topAnchor.constraint(equalTo: container.topAnchor, constant: 12),
            label.bottomAnchor.constraint(equalTo: container.bottomAnchor, constant: -12),
            label.leadingAnchor.constraint(equalTo: container.leadingAnchor, constant: 12),
            label.trailingAnchor.constraint(equalTo: container.trailingAnchor, constant: -12),
        ])
        stack.addArrangedSubview(container)
    }

    private func addElement(_ view: UIView, height: CGFloat) {
        view.translatesAutoresizingMaskIntoConstraints = false
        view.heightAnchor.constraint(equalToConstant: height).isActive = true
        stack.addArrangedSubview(view)
        stack.setCustomSpacing(20, after: view)
    }

    private func makeCard(title: String, subtitle: String, color: UIColor) -> UIView {
        let card = UIView()
        card.backgroundColor = color.withAlphaComponent(0.1)
        card.layer.cornerRadius = 12

        let vStack = UIStackView()
        vStack.axis = .vertical
        vStack.spacing = 4
        vStack.translatesAutoresizingMaskIntoConstraints = false
        card.addSubview(vStack)
        NSLayoutConstraint.activate([
            vStack.centerYAnchor.constraint(equalTo: card.centerYAnchor),
            vStack.leadingAnchor.constraint(equalTo: card.leadingAnchor, constant: 16),
            vStack.trailingAnchor.constraint(equalTo: card.trailingAnchor, constant: -16),
        ])

        let titleLabel = UILabel()
        titleLabel.text = title
        titleLabel.font = .systemFont(ofSize: 16, weight: .semibold)
        titleLabel.textColor = color

        let subtitleLabel = UILabel()
        subtitleLabel.text = subtitle
        subtitleLabel.font = .systemFont(ofSize: 14)
        subtitleLabel.textColor = color.withAlphaComponent(0.8)

        vStack.addArrangedSubview(titleLabel)
        vStack.addArrangedSubview(subtitleLabel)
        return card
    }

    private func makeBanner(text: String, color: UIColor) -> UIView {
        let banner = UIView()
        banner.backgroundColor = color.withAlphaComponent(0.1)
        banner.layer.cornerRadius = 10

        let label = UILabel()
        label.text = text
        label.font = .systemFont(ofSize: 14, weight: .medium)
        label.textColor = color
        label.translatesAutoresizingMaskIntoConstraints = false
        banner.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: banner.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: banner.centerYAnchor),
        ])
        return banner
    }
}

// MARK: - UILabel extension (uppercase section headers)

private extension UILabel {
    func textTransform() {
        if let t = text { text = t.uppercased() }
    }
}

// MARK: - UITableViewDataSource / Delegate

private let tableItems: [(title: String, detail: String)] = [
    ("iPhone 15 Pro", "Apple"),
    ("Galaxy S24 Ultra", "Samsung"),
    ("Pixel 8", "Google"),
]

extension TapDemoViewController: UITableViewDataSource, UITableViewDelegate {
    func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        tableItems.count
    }

    func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        let cell = tableView.dequeueReusableCell(withIdentifier: "cell", for: indexPath)
        let item = tableItems[indexPath.row]
        // Using subtitle style — textLabel + detailTextLabel both hold text
        // SDK recursive scan: cell → contentView → [UILabel(textLabel), UILabel(detailTextLabel)]
        var content = cell.defaultContentConfiguration()
        content.text = item.title
        content.secondaryText = item.detail
        cell.contentConfiguration = content
        // NO accessibilityLabel or accessibilityIdentifier set
        return cell
    }

    func tableView(_ tableView: UITableView, didSelectRowAt indexPath: IndexPath) {
        tableView.deselectRow(at: indexPath, animated: true)
        print("[TapDemo] UITableViewCell tapped — SDK should have logged app.widget.click above this line")
    }
}

// MARK: - UICollectionViewDataSource / Delegate

private let collectionItems = ["Electronics", "Fashion", "Home", "Sports", "Books", "Toys", "Beauty", "Garden", "Automotive", "Grocery"]

extension TapDemoViewController: UICollectionViewDataSource, UICollectionViewDelegate {
    func collectionView(_ collectionView: UICollectionView, numberOfItemsInSection section: Int) -> Int {
        collectionItems.count
    }

    func collectionView(_ collectionView: UICollectionView, cellForItemAt indexPath: IndexPath) -> UICollectionViewCell {
        let cell = collectionView.dequeueReusableCell(withReuseIdentifier: "cvcell", for: indexPath) as! DemoCollectionCell
        cell.configure(title: collectionItems[indexPath.item])
        // NO accessibilityLabel or accessibilityIdentifier set
        return cell
    }

    func collectionView(_ collectionView: UICollectionView, didSelectItemAt indexPath: IndexPath) {
        print("[TapDemo] UICollectionViewCell tapped — SDK should have logged app.widget.click above this line")
    }
}

// MARK: - DemoCollectionCell

private class DemoCollectionCell: UICollectionViewCell {
    private let label = UILabel()

    override init(frame: CGRect) {
        super.init(frame: frame)
        contentView.backgroundColor = .systemBackground
        contentView.layer.cornerRadius = 8
        contentView.layer.borderColor = UIColor.separator.cgColor
        contentView.layer.borderWidth = 0.5
        label.font = .systemFont(ofSize: 12, weight: .medium)
        label.textAlignment = .center
        label.numberOfLines = 2
        label.translatesAutoresizingMaskIntoConstraints = false
        contentView.addSubview(label)
        NSLayoutConstraint.activate([
            label.centerXAnchor.constraint(equalTo: contentView.centerXAnchor),
            label.centerYAnchor.constraint(equalTo: contentView.centerYAnchor),
            label.leadingAnchor.constraint(equalTo: contentView.leadingAnchor, constant: 4),
            label.trailingAnchor.constraint(equalTo: contentView.trailingAnchor, constant: -4),
        ])
    }

    required init?(coder: NSCoder) { fatalError() }

    func configure(title: String) {
        label.text = title
        // NO accessibilityLabel or accessibilityIdentifier
    }
}

// MARK: - IntrinsicTableView (self-sizing for embedding in scroll view)

private class IntrinsicTableView: UITableView {
    override var contentSize: CGSize {
        didSet { invalidateIntrinsicContentSize() }
    }
    override var intrinsicContentSize: CGSize {
        layoutIfNeeded()
        return CGSize(width: UIView.noIntrinsicMetric, height: contentSize.height)
    }
}
