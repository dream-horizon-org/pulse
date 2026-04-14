import XCTest
@testable import PulseKit

final class SessionConfigurationTests: XCTestCase {
  
    func testDefaultConfiguration() {
    let config = SessionConfig.default
    XCTAssertEqual(config.maxLifetime, 4 * 60 * 60)
    XCTAssertEqual(config.backgroundInactivityTimeout, 15 * 60)
    XCTAssertEqual(config.shouldPersist, false)
    XCTAssertEqual(config.startEventName, "session.start")
    XCTAssertEqual(config.endEventName, "session.end")
    }

    func testCustomConfiguration() {
      let config = SessionConfig(backgroundInactivityTimeout: 15,
                                 maxLifetime: 3600,
                                 shouldPersist: true,
                                 startEventName: "custom.start",
                                 endEventName: "custom.end")
      XCTAssertEqual(config.maxLifetime, 3600)
      XCTAssertEqual(config.backgroundInactivityTimeout, 15)
      XCTAssertEqual(config.shouldPersist,true)
      XCTAssertEqual(config.startEventName, "custom.start")
      XCTAssertEqual(config.endEventName, "custom.end")
    }
    
    func testNilConfig() {
      let config = SessionConfig(backgroundInactivityTimeout: nil,
                                 maxLifetime: nil,
                                 startEventName: nil,
                                 endEventName: nil)
      XCTAssertNil(config.backgroundInactivityTimeout)
      XCTAssertNil(config.startEventName)
      XCTAssertNil(config.endEventName)
      XCTAssertNil(config.maxLifetime)
    }

    func testBuilderPattern() {
    let config = SessionConfig.builder()
      .with(maxLifetime: 45 * 60)
      .with(shouldPersist: true)
      .build()

    XCTAssertEqual(config.maxLifetime, 45 * 60)
    XCTAssertEqual(config.shouldPersist,true)
    }

    func testBuilderDefaultValues() {
    let config = SessionConfig.builder().build()
    XCTAssertEqual(config.maxLifetime, 4 * 60 * 60)
    XCTAssertEqual(config.backgroundInactivityTimeout, 15 * 60)
    XCTAssertEqual(config.shouldPersist, false)
    }

    func testBuilderMethodChaining() {
    let builder = SessionConfig.builder()
    XCTAssertEqual(builder.maxLifetime, 4 * 60 * 60)
    let sameBuilder = builder.with(maxLifetime: 60 * 60)
    XCTAssertTrue(builder === sameBuilder)
    XCTAssertEqual(sameBuilder.maxLifetime, 60 * 60)
    }

    func testBuilderEqualsNormalConfig() {
    let normalConfig = SessionConfig(maxLifetime: 45 * 60)
    let builderConfig = SessionConfig.builder()
      .with(maxLifetime: 45 * 60)
      .build()

    XCTAssertEqual(normalConfig.maxLifetime, builderConfig.maxLifetime)
    }
}
