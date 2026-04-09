import Foundation
import OpenTelemetrySdk
import OpenTelemetryApi

// Filters out spans with pulse.internal=true
internal class FilteringSpanExporter: SpanExporter {
    private let delegate: SpanExporter
    
    init(delegate: SpanExporter) {
        self.delegate = delegate
    }
    
    func export(spans: [SpanData], explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        let filtered = spans.filter { span in
            guard let attr = span.attributes["pulse.internal"],
                  case .bool(let value) = attr else { return true }
            return !value
        }
        return filtered.isEmpty ? .success : delegate.export(spans: filtered, explicitTimeout: explicitTimeout)
    }
    
    func flush(explicitTimeout: TimeInterval?) -> SpanExporterResultCode {
        return delegate.flush(explicitTimeout: explicitTimeout)
    }
    
    func shutdown(explicitTimeout: TimeInterval?) {
        delegate.shutdown(explicitTimeout: explicitTimeout)
    }
}
