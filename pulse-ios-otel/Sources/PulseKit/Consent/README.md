# Data collection consent

**Pending:** spans/logs buffer in memory (5000 cap, drop newest) before `BatchSpanProcessor` / `BatchLogRecordProcessor`.  
**Allowed:** buffer is replayed through batch, then exports normally.  
**Denied:** buffer cleared; SDK shuts down for this process.

**API:** `initialize(..., dataCollectionState:)`, `setDataCollectionState(.allowed | .denied)`. Default `.allowed`. Denied at init skips building OpenTelemetry.

**Shutdown:** `Pulse.shutdown()` stops consent processors, which stop the inner batch processors.
