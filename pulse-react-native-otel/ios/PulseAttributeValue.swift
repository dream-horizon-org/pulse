import Foundation
import OpenTelemetryApi

/// Objective-C compatible wrapper for OpenTelemetry's `AttributeValue` enum.
@objc(PulseAttributeValue)
public class PulseAttributeValue: NSObject {
    private let _swiftValue: OpenTelemetryApi.AttributeValue
    
    private init(_ swiftValue: OpenTelemetryApi.AttributeValue) {
        self._swiftValue = swiftValue
    }
    
    internal var swiftValue: OpenTelemetryApi.AttributeValue {
        return _swiftValue
    }
    
    @objc(string:)
    public static func string(_ value: String) -> PulseAttributeValue {
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.string(value))
    }
    
    @objc(int:)
    public static func int(_ value: Int) -> PulseAttributeValue {
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.int(value))
    }
    
    @objc(double:)
    public static func double(_ value: Double) -> PulseAttributeValue {
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.double(value))
    }
    
    @objc(bool:)
    public static func bool(_ value: Bool) -> PulseAttributeValue {
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.bool(value))
    }
    
    @objc(array:)
    public static func array(_ values: [PulseAttributeValue]) -> PulseAttributeValue {
        let attrArray = AttributeArray(values: values.map { $0.swiftValue })
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.array(attrArray))
    }
    
    @objc(stringArray:)
    public static func stringArray(_ values: [String]) -> PulseAttributeValue {
        let attrArray = AttributeArray(values: values.map { OpenTelemetryApi.AttributeValue.string($0) })
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.array(attrArray))
    }
    
    @objc(intArray:)
    public static func intArray(_ values: [NSNumber]) -> PulseAttributeValue {
        let attrArray = AttributeArray(values: values.map { OpenTelemetryApi.AttributeValue.int($0.intValue) })
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.array(attrArray))
    }
    
    @objc(doubleArray:)
    public static func doubleArray(_ values: [NSNumber]) -> PulseAttributeValue {
        let attrArray = AttributeArray(values: values.map { OpenTelemetryApi.AttributeValue.double($0.doubleValue) })
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.array(attrArray))
    }
    
    @objc(boolArray:)
    public static func boolArray(_ values: [NSNumber]) -> PulseAttributeValue {
        let attrArray = AttributeArray(values: values.map { OpenTelemetryApi.AttributeValue.bool($0.boolValue) })
        return PulseAttributeValue(OpenTelemetryApi.AttributeValue.array(attrArray))
    }
}

extension PulseAttributeValue {
    internal static func from(_ swiftValue: OpenTelemetryApi.AttributeValue) -> PulseAttributeValue {
        return PulseAttributeValue(swiftValue)
    }
    
    internal var toSwift: OpenTelemetryApi.AttributeValue {
        return _swiftValue
    }
    
    /// Convert a single value (NSString, NSNumber, etc.) to PulseAttributeValue
    /// This is a convenience method for Objective-C++ bridge code
    @objc(attributeValueFromValue:)
    public static func fromValue(_ value: Any?) -> PulseAttributeValue? {
        guard let value = value, !(value is NSNull) else { return nil }
        
        if let string = value as? String {
            return PulseAttributeValue.string(string)
        } else if let number = value as? NSNumber {
            if CFGetTypeID(number) == CFBooleanGetTypeID() {
                return PulseAttributeValue.bool(number.boolValue)
            } else {
                // Use double for NSNumber to preserve precision
                return PulseAttributeValue.double(number.doubleValue)
            }
        } else if let boolValue = value as? Bool {
            return PulseAttributeValue.bool(boolValue)
        } else {
            return PulseAttributeValue.string(String(describing: value))
        }
    }
}

extension Dictionary where Key == String, Value == PulseAttributeValue {
    internal func toSwiftAttributes() -> [String: OpenTelemetryApi.AttributeValue] {
        return self.mapValues { $0.swiftValue }
    }
}

