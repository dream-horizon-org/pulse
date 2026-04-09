/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation
import OpenTelemetryApi
import OpenTelemetrySdk

public class ApplicationResourceProvider: ResourceProvider {
  let applicationDataSource: IApplicationDataSource

  public init(source: IApplicationDataSource) {
    applicationDataSource = source
  }

  override public var attributes: [String: AttributeValue] {
    var attributes = [String: AttributeValue]()

    if let bundleName = applicationDataSource.name {
      attributes[ResourceAttributes.serviceName.rawValue] = AttributeValue.string(bundleName)
    }

    if let version = applicationVersion() {
      attributes[ResourceAttributes.serviceVersion.rawValue] = AttributeValue.string(version)
    }
    
    // Pulse-specific: app.build_id (CFBundleVersion from Bundle.main)
    // Standard iOS: Bundle.main.infoDictionary[kCFBundleVersionKey]
    // https://developer.apple.com/documentation/bundleresources/information_property_list/cfbundleversion
    if let buildId = applicationDataSource.build {
      attributes["app.build_id"] = AttributeValue.string(buildId)
    }
    
    // Pulse-specific: app.build_name ("version_build" format)
    // iOS provides: CFBundleShortVersionString (version) and CFBundleVersion (build) separately
    // This combines them to match "${versionName}_${versionCode}" format
    if let version = applicationDataSource.version, let build = applicationDataSource.build {
      attributes["app.build_name"] = AttributeValue.string("\(version)_\(build)")
    } else if let version = applicationDataSource.version {
      attributes["app.build_name"] = AttributeValue.string(version)
    } else if let build = applicationDataSource.build {
      attributes["app.build_name"] = AttributeValue.string(build)
    }

    return attributes
  }

  func applicationVersion() -> String? {
    if let build = applicationDataSource.build {
      if let version = applicationDataSource.version {
        return "\(version) (\(build))"
      }
      return build
    } else {
      return applicationDataSource.version
    }
  }
}
