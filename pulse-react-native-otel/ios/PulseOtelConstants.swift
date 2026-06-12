import Foundation

struct PulseOtelConstants {
    static let INSTRUMENTATION_SCOPE = "com.pulsereactnativeotel"
    /// Kept in line with PulseKit’s tracer instrumentation version for consistent semantics.
    static let INSTRUMENTATION_VERSION = "1.0.0"
    
    static let ATTR_ERROR_FATAL = "error.fatal"
    static let ATTR_ERROR_TYPE = "exception.type"
    static let ATTR_ERROR_MESSAGE = "exception.message"
    static let ATTR_ERROR_STACK = "exception.stacktrace"
    static let ATTR_ERROR_SOURCE = "error.source"
    
    static let ATTR_THREAD_ID = "thread.id"
    static let ATTR_THREAD_NAME = "thread.name"
    
    static let PLATFORM_REACT_NATIVE = "react-native"
    static let DEFAULT_ERROR_TYPE = "javascript"
    static let ERROR_SOURCE_JS = "js"
}

