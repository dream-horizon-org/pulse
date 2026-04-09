/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

#if os(iOS) || os(tvOS)
import XCTest
import UIKit
import OpenTelemetryApi
@testable import OpenTelemetrySdk
@testable import PulseKit

final class UIKitTapInstrumentationTests: XCTestCase {

    var logExporter: InMemoryLogRecordExporter!
    var loggerProvider: LoggerProviderSdk!
    var logger: OpenTelemetryApi.Logger!

    override func setUp() {
        super.setUp()
        logExporter = InMemoryLogRecordExporter()
        loggerProvider = LoggerProviderBuilder()
            .with(processors: [SimpleLogRecordProcessor(logRecordExporter: logExporter)])
            .build()
        logger = loggerProvider.get(instrumentationScopeName: "test.uikit.tap")
    }

    // MARK: - Click target detection

    func testUIControlIsClickTarget() {
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(UIButton()))
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(UISwitch()))
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(UISegmentedControl()))
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(UIStepper()))
    }

    func testTableViewCellIsClickTarget() {
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(UITableViewCell()))
    }

    func testCollectionViewCellIsClickTarget() {
        let layout = UICollectionViewFlowLayout()
        let cv = UICollectionView(frame: .zero, collectionViewLayout: layout)
        let cell = UICollectionViewCell()
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(cell))
    }

    func testViewWithTapGestureRecognizerIsClickTarget() {
        let view = UIView()
        view.addGestureRecognizer(UITapGestureRecognizer())
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(view))
    }

    func testPlainViewIsNotClickTarget() {
        XCTAssertFalse(UIWindowSwizzler.isClickTarget(UIView()))
    }

    func testUIScrollViewIsNotClickTargetEvenWithTapGesture() {
        let scroll = UIScrollView()
        scroll.addGestureRecognizer(UITapGestureRecognizer())
        XCTAssertFalse(UIWindowSwizzler.isClickTarget(scroll))
    }

    func testUITableViewIsNotClickTarget() {
        let table = UITableView()
        XCTAssertFalse(UIWindowSwizzler.isClickTarget(table))
    }

    func testUILabelIsNotClickTarget() {
        XCTAssertFalse(UIWindowSwizzler.isClickTarget(UILabel()))
    }

    func testViewWithPanGestureOnlyIsNotClickTarget() {
        let view = UIView()
        view.addGestureRecognizer(UIPanGestureRecognizer())
        XCTAssertFalse(UIWindowSwizzler.isClickTarget(view))
    }

    func testViewWithLongPressGestureRecognizerIsClickTarget() {
        let view = UIView()
        view.addGestureRecognizer(UILongPressGestureRecognizer())
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(view))
    }

    func testViewWithSwipeGestureRecognizerIsClickTarget() {
        let view = UIView()
        view.addGestureRecognizer(UISwipeGestureRecognizer())
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(view))
    }

    func testViewWithAccessibilityButtonTraitIsClickTarget() {
        let view = UIView()
        view.accessibilityTraits.insert(.button)
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(view))
    }

    func testViewWithAccessibilityLinkTraitIsClickTarget() {
        let view = UIView()
        view.accessibilityTraits.insert(.link)
        XCTAssertTrue(UIWindowSwizzler.isClickTarget(view))
    }

    // MARK: - Label extraction: UILabel

    func testExtractLabelFromUILabel() {
        let label = UILabel()
        label.text = "Hello World"
        XCTAssertEqual(UIWindowSwizzler.extractLabel(from: label), "Hello World")
    }

    func testExtractLabelFromUILabelReturnsNilWhenEmpty() {
        let label = UILabel()
        label.text = ""
        XCTAssertNil(UIWindowSwizzler.extractLabel(from: label))
    }

    // MARK: - Label extraction: UIButton (direct UILabel child)

    func testExtractLabelFromUIButton() {
        let button = UIButton(type: .system)
        button.setTitle("Add to Cart", for: .normal)
        // Force layout so titleLabel.text is set
        button.layoutIfNeeded()
        let result = UIWindowSwizzler.extractLabel(from: button)
        XCTAssertEqual(result, "Add to Cart")
    }

    // MARK: - Label extraction: UISegmentedControl

    func testExtractLabelFromSegmentedControlReturnsSelectedSegment() {
        let seg = UISegmentedControl(items: ["Monthly", "Daily", "Weekly"])
        seg.selectedSegmentIndex = 0
        XCTAssertEqual(UIWindowSwizzler.extractLabel(from: seg), "Monthly")

        seg.selectedSegmentIndex = 2
        XCTAssertEqual(UIWindowSwizzler.extractLabel(from: seg), "Weekly")
    }

    func testExtractLabelFromSegmentedControlNotAllSegments() {
        let seg = UISegmentedControl(items: ["Monthly", "Daily", "Weekly"])
        seg.selectedSegmentIndex = 1
        let result = UIWindowSwizzler.extractLabel(from: seg)
        // Must be exactly "Daily", NOT "Monthly | Daily | Weekly"
        XCTAssertEqual(result, "Daily")
        XCTAssertFalse(result?.contains("|") ?? false)
    }

    // MARK: - Label extraction: accessibilityLabel fallback

    func testExtractLabelFallsBackToAccessibilityLabel() {
        let view = UIView()
        view.accessibilityLabel = "Settings Button"
        XCTAssertEqual(UIWindowSwizzler.extractLabel(from: view), "Settings Button")
    }

    func testExtractLabelPrefersUILabelOverAccessibilityLabel() {
        let container = UIView()
        let label = UILabel()
        label.text = "Direct Label"
        container.addSubview(label)
        container.accessibilityLabel = "Should Not Use This"
        // Direct UILabel child takes priority over accessibilityLabel
        XCTAssertEqual(UIWindowSwizzler.extractLabel(from: container), "Direct Label")
    }

    // MARK: - Label extraction: recursive scan

    func testExtractLabelRecursivelyCollectsTextFromDescendants() {
        let card = UIView()
        let title = UILabel(); title.text = "Galaxy S24 Ultra"
        let subtitle = UILabel(); subtitle.text = "Samsung"
        card.addSubview(title)
        card.addSubview(subtitle)

        let result = UIWindowSwizzler.extractLabel(from: card)
        XCTAssertEqual(result, "Galaxy S24 Ultra | Samsung")
    }

    func testExtractLabelCapsAtFiveSegments() {
        let container = UIView()
        for i in 1...7 {
            let label = UILabel(); label.text = "Segment \(i)"
            container.addSubview(label)
        }
        let result = UIWindowSwizzler.extractLabel(from: container) ?? ""
        let segments = result.components(separatedBy: " | ")
        XCTAssertLessThanOrEqual(segments.count, 5)
    }

    func testExtractLabelTruncatesResultOver200Chars() {
        let container = UIView()
        // Each segment is 50 chars; 5 segments + delimiters = well over 200
        for _ in 1...5 {
            let label = UILabel()
            label.text = String(repeating: "A", count: 50)
            container.addSubview(label)
        }
        let result = UIWindowSwizzler.extractLabel(from: container) ?? ""
        XCTAssertLessThanOrEqual(result.count, 200)
    }

    func testExtractLabelReturnsNilWhenNoLabelsFound() {
        let view = UIView()
        view.addSubview(UIView()) // no UILabel subviews
        XCTAssertNil(UIWindowSwizzler.extractLabel(from: view))
    }

    // MARK: - PII safety

    func testUITextFieldDoesNotCaptureTypedText() {
        let field = UITextField()
        field.text = "secret_password"
        field.accessibilityLabel = nil
        // Typed text must never be captured
        XCTAssertNil(UIWindowSwizzler.extractLabel(from: field))
    }

    func testUITextFieldCapturesAccessibilityLabelOnly() {
        let field = UITextField()
        field.text = "secret_password"
        field.accessibilityLabel = "Email Address"
        // Only developer-set label, not the typed text
        XCTAssertEqual(UIWindowSwizzler.extractLabel(from: field), "Email Address")
    }

    func testUITextViewDoesNotCaptureTypedText() {
        let textView = UITextView()
        textView.text = "user typed content"
        textView.accessibilityLabel = nil
        XCTAssertNil(UIWindowSwizzler.extractLabel(from: textView))
    }

    func testRecursiveScanSkipsTextInputSubviews() {
        let container = UIView()
        let safeLabel = UILabel(); safeLabel.text = "Safe"
        let field = UITextField(); field.text = "PII data"
        container.addSubview(safeLabel)
        container.addSubview(field)

        let result = UIWindowSwizzler.extractLabel(from: container)
        XCTAssertEqual(result, "Safe")
        XCTAssertFalse(result?.contains("PII data") ?? false)
    }

    // MARK: - Event emission

    func testEmitClickEventEmitsCorrectEventName() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 10, y: 20, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let records = logExporter.getFinishedLogRecords()
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(records[0].eventName, "app.widget.click")
    }

    func testEmitClickEventSetsWidgetNameToClassName() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 0, y: 0, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes["app.widget.name"], .string("UIButton"))
    }

    func testEmitClickEventSetsCoordinates() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 142, y: 380, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes["app.screen.coordinate.x"], .int(142))
        XCTAssertEqual(record.attributes["app.screen.coordinate.y"], .int(380))
    }

    func testEmitClickEventIncludesLabelInContextWhenCaptureContextTrue() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 0, y: 0, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: "label=Checkout",
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes["app.click.context"], .string("label=Checkout"))
    }

    func testEmitClickEventOmitsContextWhenCaptureContextFalse() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 0, y: 0, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertNil(record.attributes["app.click.context"])
    }

    func testEmitClickEventOmitsContextWhenNoLabelFound() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 0, y: 0, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertNil(record.attributes["app.click.context"])
    }

    func testEmitClickEventSegmentedControlUsesSelectedSegmentLabel() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 0, y: 0, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UISegmentedControl", widgetId: nil, clickContext: "label=Weekly",
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes["app.click.context"], .string("label=Weekly"))
    }

    func testEmitClickEventEmitsAppWidgetIdWhenPresent() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 0, y: 0, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: "some_id", clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes["app.widget.id"], .string("some_id"))
    }

    func testEmitDeadClickDoesNotEmitAppWidgetId() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 0, y: 0, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: false, widgetName: nil, widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitDeadClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertNil(record.attributes["app.widget.id"])
    }

    // MARK: - Config

    func testConfigDefaultsToDisabled() {
        let config = UIKitTapInstrumentationConfig()
        XCTAssertFalse(config.enabled)
        XCTAssertFalse(config.captureContext)
    }

    func testConfigCanBeDisabled() {
        var config = UIKitTapInstrumentationConfig()
        config.enabled(false)
        XCTAssertFalse(config.enabled)
    }

    func testConfigCaptureContextCanBeDisabled() {
        var config = UIKitTapInstrumentationConfig()
        config.captureContext(false)
        XCTAssertFalse(config.captureContext)
    }

    func testConfigCanBeEnabled() {
        var config = UIKitTapInstrumentationConfig()
        config.enabled(true)
        config.captureContext(true)
        XCTAssertTrue(config.enabled)
        XCTAssertTrue(config.captureContext)
    }
    
    // MARK: - Click type attribute

    func testClickEventIncludesGoodClickType() {
        let button = UIButton()
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 100, y: 200, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes[PulseAttributes.clickType], .string(PulseAttributes.ClickTypeValues.good))
    }

    func testClickEventIncludesDeadClickType() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 100, y: 200, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: false, widgetName: nil, widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitDeadClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes[PulseAttributes.clickType], .string(PulseAttributes.ClickTypeValues.dead))
    }

    // MARK: - Viewport attributes

    func testClickEventIncludesViewportDimensions() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 187.5, y: 406, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes[PulseAttributes.deviceScreenWidth], .int(375))
        XCTAssertEqual(record.attributes[PulseAttributes.deviceScreenHeight], .int(812))
    }

    func testClickEventIncludesNormalizedCoordinates() {
        let emitter = ClickEventEmitter(logger: logger)
        let click = PendingClick(
            x: 187.5, y: 406, timestampMs: 1000, tapEpochMs: 1000,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitGoodClick(click)

        let record = logExporter.getFinishedLogRecords()[0]
        if case .double(let nx) = record.attributes[PulseAttributes.appScreenCoordinateNx] ?? .double(0) {
            XCTAssertEqual(nx, 0.5, accuracy: 0.01)
        } else {
            XCTFail("Expected double for nx")
        }
        if case .double(let ny) = record.attributes[PulseAttributes.appScreenCoordinateNy] ?? .double(0) {
            XCTAssertEqual(ny, 0.5, accuracy: 0.01)
        } else {
            XCTFail("Expected double for ny")
        }
    }

    // MARK: - Rage detection

    func testClickEventIncludesRageAttributes() {
        let emitter = ClickEventEmitter(logger: logger)
        let rage = RageEvent(
            count: 5, hasTarget: true, x: 100, y: 200, tapEpochMs: 1000,
            widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitRageClick(rage)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes[PulseAttributes.clickIsRage], .bool(true))
        XCTAssertEqual(record.attributes[PulseAttributes.clickRageCount], .int(5))
    }

    func testRageClickUsesGoodTypeWhenTargetExists() {
        let emitter = ClickEventEmitter(logger: logger)
        let rage = RageEvent(
            count: 3, hasTarget: true, x: 100, y: 200, tapEpochMs: 1000,
            widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitRageClick(rage)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes[PulseAttributes.clickType], .string(PulseAttributes.ClickTypeValues.good))
    }

    func testRageClickUsesDeadTypeWhenNoTarget() {
        let emitter = ClickEventEmitter(logger: logger)
        let rage = RageEvent(
            count: 3, hasTarget: false, x: 100, y: 200, tapEpochMs: 1000,
            widgetName: nil, widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        emitter.emitRageClick(rage)

        let record = logExporter.getFinishedLogRecords()[0]
        XCTAssertEqual(record.attributes[PulseAttributes.clickType], .string(PulseAttributes.ClickTypeValues.dead))
    }

    // MARK: - ClickEventBuffer rage threshold

    func testClickEventBufferDetectsRageAtThresholdAndEmitsOnExpiry() {
        var clockMs: Int64 = 0
        var emittedRages: [RageEvent] = []

        let config = RageConfig(timeWindowMs: 2000, rageThreshold: 3, radiusPt: 50)
        let buffer = ClickEventBuffer(
            rageConfig: config,
            onRage: { emittedRages.append($0) },
            onEmit: { _ in },
            clock: { clockMs }
        )

        buffer.record(PendingClick(x: 100, y: 100, timestampMs: 0, tapEpochMs: 0, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))
        buffer.record(PendingClick(x: 110, y: 110, timestampMs: 100, tapEpochMs: 100, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))
        buffer.record(PendingClick(x: 120, y: 120, timestampMs: 200, tapEpochMs: 200, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))

        // Rage cluster formed, but emitted on expiry/flush (not immediately).
        XCTAssertEqual(emittedRages.count, 0)

        // Trigger expiry by recording a tap after time window.
        clockMs = 2500
        buffer.record(PendingClick(x: 300, y: 300, timestampMs: 2500, tapEpochMs: 2500, hasTarget: false, widgetName: nil, widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))

        XCTAssertEqual(emittedRages.count, 1)
        XCTAssertEqual(emittedRages[0].count, 3)
    }

    func testClickEventBufferEmitsIndividualClickBelowThreshold() {
        var emittedCount = 0
        var rageEmitted = false

        let config = RageConfig(timeWindowMs: 2000, rageThreshold: 3, radiusPt: 50)
        let buffer = ClickEventBuffer(
            rageConfig: config,
            onRage: { _ in
                rageEmitted = true
            },
            onEmit: { _ in
                emittedCount += 1
            }
        )

        let click = PendingClick(
            x: 100, y: 100, timestampMs: 0, tapEpochMs: 0,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        buffer.record(click)

        XCTAssertFalse(rageEmitted)
        XCTAssertEqual(emittedCount, 0)

        buffer.flush()
        XCTAssertEqual(emittedCount, 1)
    }

    func testClickEventBufferFlushEmitsPendingRage() {
        var rageEmitted = false
        var emittedCount = 0

        let config = RageConfig(timeWindowMs: 2000, rageThreshold: 3, radiusPt: 50)
        let buffer = ClickEventBuffer(
            rageConfig: config,
            onRage: { _ in
                rageEmitted = true
            },
            onEmit: { _ in
                emittedCount += 1
            }
        )

        let baseClick = PendingClick(
            x: 100, y: 100, timestampMs: 0, tapEpochMs: 0,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )

        buffer.record(baseClick)
        buffer.record(PendingClick(x: 110, y: 110, timestampMs: 100, tapEpochMs: 100, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))
        buffer.record(PendingClick(x: 120, y: 120, timestampMs: 200, tapEpochMs: 200, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))

        XCTAssertFalse(rageEmitted)
        buffer.flush()
        XCTAssertTrue(rageEmitted)
        XCTAssertEqual(emittedCount, 0)
    }

    func testClickEventBufferUsesNearestClusterWhenTwoClustersOverlapRadius() {
        var clockMs: Int64 = 0
        var emittedRages: [RageEvent] = []

        let config = RageConfig(timeWindowMs: 2000, rageThreshold: 3, radiusPt: 50)
        let buffer = ClickEventBuffer(
            rageConfig: config,
            onRage: { emittedRages.append($0) },
            onEmit: { _ in },
            clock: { clockMs }
        )

        // Cluster A around (100,100)
        buffer.record(PendingClick(x: 100, y: 100, timestampMs: 0, tapEpochMs: 0, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))
        buffer.record(PendingClick(x: 105, y: 105, timestampMs: 50, tapEpochMs: 50, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))
        buffer.record(PendingClick(x: 110, y: 110, timestampMs: 100, tapEpochMs: 100, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))

        // Cluster B around (160,160)
        buffer.record(PendingClick(x: 160, y: 160, timestampMs: 200, tapEpochMs: 200, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))
        buffer.record(PendingClick(x: 165, y: 165, timestampMs: 250, tapEpochMs: 250, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))
        buffer.record(PendingClick(x: 170, y: 170, timestampMs: 300, tapEpochMs: 300, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))

        // Tap in overlap area but closer to cluster B.
        buffer.record(PendingClick(x: 145, y: 145, timestampMs: 350, tapEpochMs: 350, hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))

        // Expire both clusters.
        clockMs = 3000
        buffer.record(PendingClick(x: 320, y: 320, timestampMs: 3000, tapEpochMs: 3000, hasTarget: false, widgetName: nil, widgetId: nil, clickContext: nil, viewportWidthPt: 375, viewportHeightPt: 812))

        XCTAssertEqual(emittedRages.count, 2)
        // One cluster should have received the overlap tap and count become 4.
        XCTAssertTrue(emittedRages.contains(where: { $0.count == 4 }))
        XCTAssertTrue(emittedRages.contains(where: { $0.count == 3 }))
    }

    func testClickEventBufferIgnoresClicksOutsideRadius() {
        var rageEmitted = false

        let config = RageConfig(timeWindowMs: 2000, rageThreshold: 3, radiusPt: 50)
        let buffer = ClickEventBuffer(
            rageConfig: config,
            onRage: { _ in
                rageEmitted = true
            },
            onEmit: { _ in }
        )

        let click1 = PendingClick(
            x: 100, y: 100, timestampMs: 0, tapEpochMs: 0,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )
        let click2 = PendingClick(
            x: 200, y: 200, timestampMs: 100, tapEpochMs: 100,
            hasTarget: true, widgetName: "UIButton", widgetId: nil, clickContext: nil,
            viewportWidthPt: 375, viewportHeightPt: 812
        )

        buffer.record(click1)
        buffer.record(click2)

        XCTAssertFalse(rageEmitted)
    }

    // MARK: - RageConfig

    func testRageConfigDefaults() {
        let config = RageConfig()
        XCTAssertEqual(config.timeWindowMs, 2000)
        XCTAssertEqual(config.rageThreshold, 3)
        XCTAssertEqual(config.radiusPt, 50.0)
    }

    func testUIKitTapInstrumentationConfigIncludesRage() {
        var config = UIKitTapInstrumentationConfig()
        config.rage { r in
            r.timeWindowMs = 1500
            r.rageThreshold = 4
        }
        XCTAssertEqual(config.rage.timeWindowMs, 1500)
        XCTAssertEqual(config.rage.rageThreshold, 4)
        XCTAssertEqual(config.rage.radiusPt, 50.0)
    }
}
#endif
