/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk
#if os(iOS)
import UIKit
#endif

public class DeviceResourceProvider: ResourceProvider {
  let deviceSource: IDeviceDataSource

  public init(source: IDeviceDataSource) {
    deviceSource = source
  }

  override public var attributes: [String: AttributeValue] {
    var attributes = [String: AttributeValue]()

    if let deviceModel = deviceSource.model {
      attributes[ResourceAttributes.deviceModelIdentifier.rawValue] = AttributeValue.string(deviceModel)
    }

    if let deviceId = deviceSource.identifier {
      attributes[ResourceAttributes.deviceId.rawValue] = AttributeValue.string(deviceId)
    }
    
    // device.manufacturer (official ResourceAttributes - iOS/macOS apps SHOULD hardcode "Apple")
    // OpenTelemetry spec: https://opentelemetry.io/docs/specs/semconv/resource/device/#manufacturer
    #if os(iOS) || os(macOS)
    attributes[ResourceAttributes.deviceManufacturer.rawValue] = AttributeValue.string("Apple")
    #endif
    
    // OpenTelemetry spec: https://opentelemetry.io/docs/specs/semconv/resource/device/#model
    #if os(iOS)
    // iOS: Use UIDevice for user-friendly names (required by OpenTelemetry spec)
    attributes[ResourceAttributes.deviceModelName.rawValue] = AttributeValue.string(UIDevice.current.model)
    #elseif os(macOS)
    // macOS: sysctl returns hardware identifier (limitation - no user-friendly API available)
    if let model = deviceSource.model {
      attributes[ResourceAttributes.deviceModelName.rawValue] = AttributeValue.string(model)
    }
    #endif

    return attributes
  }
}
