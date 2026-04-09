/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

/// Stores session in memory only, does not persist across app restarts
internal class InMemorySessionStorage: SessionStorage {
    private var session: Session?
    
    init(session: Session? = nil) {
        self.session = session
    }
    
    func get() -> Session? {
        return session
    }
    
    func save(_ newSession: Session) {
        self.session = newSession
    }
}
