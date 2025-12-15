import Foundation
import OpenTelemetryApi

/// Helper to convert NSDictionary (from React Native) to PulseAttributeValue or OpenTelemetryApi.AttributeValue
@objc(AttributeValueConverter)
public class AttributeValueConverter: NSObject {
    /// Convert NSDictionary to PulseAttributeValue (for PulseSDK API)
    @objc(convertFromDictionary:)
    public static func convert(_ dict: NSDictionary?) -> [String: PulseAttributeValue] {
        guard let dict = dict else { return [:] }
        var result: [String: PulseAttributeValue] = [:]
        for (key, value) in dict {
            if let keyString = key as? String, !(value is NSNull) {
                if let attrValue = convertToPulseAttributeValue(value) {
                    result[keyString] = attrValue
                }
            }
        }
        return result
    }
    
    /// Convert NSDictionary directly to OpenTelemetryApi.AttributeValue (for direct OpenTelemetry usage)
    static func convertToSwift(_ dict: NSDictionary?) -> [String: OpenTelemetryApi.AttributeValue] {
        guard let dict = dict else { return [:] }
        var result: [String: OpenTelemetryApi.AttributeValue] = [:]
        for (key, value) in dict {
            if let keyString = key as? String, !(value is NSNull) {
                if let attrValue = convertToSwiftAttributeValue(value) {
                    result[keyString] = attrValue
                }
            }
        }
        return result
    }
    
    private static func convertToPulseAttributeValue(_ value: Any?) -> PulseAttributeValue? {
        guard let value = value else { return nil }
        
        if let string = value as? String {
            return PulseAttributeValue.string(string)
        } else if let int = value as? Int {
            return PulseAttributeValue.int(int)
        } else if let int64 = value as? Int64 {
            return PulseAttributeValue.int(Int(int64))
        } else if let double = value as? Double {
            return PulseAttributeValue.double(double)
        } else if let bool = value as? Bool {
            return PulseAttributeValue.bool(bool)
        } else if let stringArray = value as? [String] {
            return PulseAttributeValue.stringArray(stringArray)
        } else if let intArray = value as? [Int] {
            return PulseAttributeValue.intArray(intArray.map { NSNumber(value: $0) })
        } else if let doubleArray = value as? [Double] {
            return PulseAttributeValue.doubleArray(doubleArray.map { NSNumber(value: $0) })
        } else if let boolArray = value as? [Bool] {
            return PulseAttributeValue.boolArray(boolArray.map { NSNumber(value: $0) })
        } else if let numberValue = value as? NSNumber {
            if CFGetTypeID(numberValue) == CFBooleanGetTypeID() {
                return PulseAttributeValue.bool(numberValue.boolValue)
            } else {
                return PulseAttributeValue.double(numberValue.doubleValue)
            }
        } else if let nsArray = value as? NSArray {
            return convertArrayToPulse(nsArray)
        } else {
            return PulseAttributeValue.string(String(describing: value))
        }
    }
    
    private static func convertToSwiftAttributeValue(_ value: Any?) -> OpenTelemetryApi.AttributeValue? {
        guard let value = value else { return nil }
        
        if let string = value as? String {
            return OpenTelemetryApi.AttributeValue.string(string)
        } else if let int = value as? Int {
            return OpenTelemetryApi.AttributeValue.int(int)
        } else if let int64 = value as? Int64 {
            return OpenTelemetryApi.AttributeValue.int(Int(int64))
        } else if let double = value as? Double {
            return OpenTelemetryApi.AttributeValue.double(double)
        } else if let bool = value as? Bool {
            return OpenTelemetryApi.AttributeValue.bool(bool)
        } else if let stringArray = value as? [String] {
            let attrArray = AttributeArray(values: stringArray.map { OpenTelemetryApi.AttributeValue.string($0) })
            return OpenTelemetryApi.AttributeValue.array(attrArray)
        } else if let intArray = value as? [Int] {
            let attrArray = AttributeArray(values: intArray.map { OpenTelemetryApi.AttributeValue.int($0) })
            return OpenTelemetryApi.AttributeValue.array(attrArray)
        } else if let doubleArray = value as? [Double] {
            let attrArray = AttributeArray(values: doubleArray.map { OpenTelemetryApi.AttributeValue.double($0) })
            return OpenTelemetryApi.AttributeValue.array(attrArray)
        } else if let boolArray = value as? [Bool] {
            let attrArray = AttributeArray(values: boolArray.map { OpenTelemetryApi.AttributeValue.bool($0) })
            return OpenTelemetryApi.AttributeValue.array(attrArray)
        } else if let numberValue = value as? NSNumber {
            if CFGetTypeID(numberValue) == CFBooleanGetTypeID() {
                return OpenTelemetryApi.AttributeValue.bool(numberValue.boolValue)
            } else {
                return OpenTelemetryApi.AttributeValue.double(numberValue.doubleValue)
            }
        } else if let nsArray = value as? NSArray {
            return convertArrayToSwift(nsArray)
        } else {
            return OpenTelemetryApi.AttributeValue.string(String(describing: value))
        }
    }
    
    private static func convertArrayToPulse(_ array: NSArray) -> PulseAttributeValue? {
        guard array.count > 0 else {
            return PulseAttributeValue.array([])
        }
        
        guard let firstElement = array.firstObject else {
            return PulseAttributeValue.array([])
        }
        
        if firstElement is String {
            let stringArray = array.compactMap { $0 as? String }
            return PulseAttributeValue.stringArray(stringArray)
        } else if firstElement is NSNumber {
            if CFGetTypeID(firstElement as! NSNumber) == CFBooleanGetTypeID() {
                let boolArray = array.compactMap { ($0 as? NSNumber)?.boolValue }
                return PulseAttributeValue.boolArray(boolArray.map { NSNumber(value: $0) })
            } else {
                let numberArray = array.compactMap { ($0 as? NSNumber)?.doubleValue }
                return PulseAttributeValue.doubleArray(numberArray.map { NSNumber(value: $0) })
            }
        } else if firstElement is Bool {
            let boolArray = array.compactMap { $0 as? Bool }
            return PulseAttributeValue.boolArray(boolArray.map { NSNumber(value: $0) })
        } else {
            return PulseAttributeValue.string(array.description)
        }
    }
    
    private static func convertArrayToSwift(_ array: NSArray) -> OpenTelemetryApi.AttributeValue? {
        guard array.count > 0 else {
            return OpenTelemetryApi.AttributeValue.array(AttributeArray(values: []))
        }
        
        guard let firstElement = array.firstObject else {
            return OpenTelemetryApi.AttributeValue.array(AttributeArray(values: []))
        }
        
        if firstElement is String {
            let stringArray = array.compactMap { $0 as? String }
            let attrArray = AttributeArray(values: stringArray.map { OpenTelemetryApi.AttributeValue.string($0) })
            return OpenTelemetryApi.AttributeValue.array(attrArray)
        } else if firstElement is NSNumber {
            if CFGetTypeID(firstElement as! NSNumber) == CFBooleanGetTypeID() {
                let boolArray = array.compactMap { ($0 as? NSNumber)?.boolValue }
                let attrArray = AttributeArray(values: boolArray.map { OpenTelemetryApi.AttributeValue.bool($0) })
                return OpenTelemetryApi.AttributeValue.array(attrArray)
            } else {
                let numberArray = array.compactMap { ($0 as? NSNumber)?.doubleValue }
                let attrArray = AttributeArray(values: numberArray.map { OpenTelemetryApi.AttributeValue.double($0) })
                return OpenTelemetryApi.AttributeValue.array(attrArray)
            }
        } else if firstElement is Bool {
            let boolArray = array.compactMap { $0 as? Bool }
            let attrArray = AttributeArray(values: boolArray.map { OpenTelemetryApi.AttributeValue.bool($0) })
            return OpenTelemetryApi.AttributeValue.array(attrArray)
        } else {
            return OpenTelemetryApi.AttributeValue.string(array.description)
        }
    }
}

