/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import Foundation

internal final class SessionReplayPersistingEmitter {

    private static let queueSpecificKey = DispatchSpecificKey<UInt8>()
    private static let queueMarker: UInt8 = 1

    private let storageDir: URL
    private let transport: SessionReplayTransport
    private let encryption: SessionReplayStorageEncryption
    private let flushIntervalSeconds: TimeInterval
    private let flushAt: Int
    private let maxBatchSize: Int

    private let queue = DispatchQueue(label: "com.pulse.ios.sdk.sessionreplay.persisting", qos: .utility)
    private var deque: [URL] = []
    private let dequeLock = NSLock()
    private var isFlushing = false
    private let flushLock = NSLock()
    private var flushTimer: DispatchSourceTimer?
    /// When true, periodic flush timer is not running (consent PENDING or explicit pause).
    private var isFlushTimerPaused: Bool = false
    private let flushTimerStateLock = NSLock()

    private var isShutDown: Bool = false
    private let shutdownLock: NSLock = NSLock()

    private static let fileExtension = "replay"

    init(
        storageDir: URL? = nil,
        transport: SessionReplayTransport,
        encryption: SessionReplayStorageEncryption? = nil,
        flushIntervalSeconds: TimeInterval = 60,
        flushAt: Int = 10,
        maxBatchSize: Int = 50,
        startFlushTimer: Bool = true
    ) {
        if let dir = storageDir {
            self.storageDir = dir
        } else {
            let caches = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask).first!
            self.storageDir = caches.appendingPathComponent("pulse-replay", isDirectory: true)
        }

        self.transport = transport

        if let enc = encryption {
            self.encryption = enc
        } else {
            if #available(iOS 13.0, macOS 10.15, tvOS 13.0, watchOS 6.0, *) {
                self.encryption = DefaultSessionReplayEncryption()
            } else {
                self.encryption = NoOpSessionReplayEncryption()
            }
        }

        self.flushIntervalSeconds = flushIntervalSeconds
        self.flushAt = flushAt
        self.maxBatchSize = maxBatchSize

        try? FileManager.default.createDirectory(at: self.storageDir, withIntermediateDirectories: true)

        queue.setSpecific(key: Self.queueSpecificKey, value: Self.queueMarker)

        // Trim stray files from earlier runs
        trimDiskFiles()

        if startFlushTimer {
            scheduleFlushTimer()
        } else {
            flushTimerStateLock.lock()
            isFlushTimerPaused = true
            flushTimerStateLock.unlock()
        }
    }

    deinit {
        flushTimer?.cancel()
    }

    private func executeOnQueueSync(_ work: () -> Void) {
        if DispatchQueue.getSpecific(key: Self.queueSpecificKey) == Self.queueMarker {
            work()
        } else {
            queue.sync(execute: work)
        }
    }

    // MARK: - Shutdown Management

    func shutdown() {
        shutdownLock.lock()
        guard !isShutDown else {
            shutdownLock.unlock()
            return
        }
        isShutDown = true
        shutdownLock.unlock()

        executeOnQueueSync { [self] in
            flushTimer?.cancel()
            flushTimer = nil
            flushIfNeeded(ignoringShutdown: true) // Final flush bypasses guard
        }
    }


    /// Stops the periodic flush timer without shutting down; `emit` and explicit `flush()` still work.
    func pauseFlushTimer() {
        queue.async { [weak self] in
            guard let self = self else { return }
            self.flushTimer?.cancel()
            self.flushTimer = nil
            self.flushTimerStateLock.lock()
            self.isFlushTimerPaused = true
            self.flushTimerStateLock.unlock()
        }
    }

    /// Restarts the periodic flush timer after `pauseFlushTimer()` (no-op if shut down or not paused).
    func resumeFlushTimer() {
        queue.async { [weak self] in
            guard let self = self else { return }
            self.shutdownLock.lock()
            let dead = self.isShutDown
            self.shutdownLock.unlock()
            guard !dead else { return }
            self.flushTimerStateLock.lock()
            let wasPaused = self.isFlushTimerPaused
            if wasPaused {
                self.isFlushTimerPaused = false
            }
            self.flushTimerStateLock.unlock()
            guard wasPaused else { return }
            self.scheduleFlushTimer()
        }
    }

    func emit(payloadJson: String) {
        shutdownLock.lock()
        guard !isShutDown else {
            shutdownLock.unlock()
            return
        }
        shutdownLock.unlock()
        
        queue.async { [weak self] in
            guard let self = self else {
                return
            }
            do {
                guard let jsonData = payloadJson.data(using: .utf8) else {
                    return
                }

                guard let encrypted = self.encryption.encrypt(jsonData) else {
                    return
                }

                let timestamp = Int(Date().timeIntervalSince1970 * 1000)
                let uuid = UUID().uuidString
                let fileName = "\(timestamp)_\(uuid).\(Self.fileExtension)"
                let fileURL = self.storageDir.appendingPathComponent(fileName)

                try encrypted.write(to: fileURL, options: .atomic)

                self.dequeLock.lock()
                self.deque.append(fileURL)
                
                // Apply eviction if total files exceed maxBatchSize
                self.evictOldestFilesIfNeeded()
                
                let currentCount = self.deque.count
                self.dequeLock.unlock()
                if currentCount >= self.flushAt {
                    self.flushIfNeeded()
                }
            } catch {
            }
        }
    }

    func sendCachedEvents() {
        queue.async { [weak self] in
            guard let self = self else { return }
            do {
                let fm = FileManager.default
                let files = try fm.contentsOfDirectory(at: self.storageDir, includingPropertiesForKeys: [.contentModificationDateKey])
                    .filter { $0.pathExtension == Self.fileExtension }
                    .sorted { url1, url2 in
                        let date1 = (try? url1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
                        let date2 = (try? url2.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
                        return date1 < date2
                    }

                guard !files.isEmpty else { return }
                
                // Sync deque with disk files and apply eviction
                self.dequeLock.lock()
                self.deque = files
                self.dequeLock.unlock()
                self.evictOldestFilesIfNeeded()

                let maxUncompressedSizeBytes = 1 * 1024 * 1024
                let maxBatchesPerChunk = self.flushAt
                
                var remainingFiles = files
                var chunkNumber = 0
                
                while !remainingFiles.isEmpty {
                    chunkNumber += 1
                    var fileToContent: [(URL, String)] = []
                    var filesToRemove: [URL] = []
                    
                    for file in remainingFiles {
                        do {
                            let content = try self.readFileContent(file)
                            let testContent = fileToContent.map { $0.1 } + [content]
                            let testPayload: String
                            if testContent.count == 1 {
                                testPayload = testContent[0]
                            } else {
                                testPayload = "[" + testContent.joined(separator: ",") + "]"
                            }
                            let testPayloadSize = testPayload.data(using: .utf8)?.count ?? 0
                            if testPayloadSize > maxUncompressedSizeBytes && !fileToContent.isEmpty {
                                break
                            }
                            fileToContent.append((file, content))
                            filesToRemove.append(file)
                            if fileToContent.count >= maxBatchesPerChunk {
                                break
                            }
                        } catch {
                            try? fm.removeItem(at: file)
                            filesToRemove.append(file)
                        }
                    }
                    
                    for file in filesToRemove {
                        if let index = remainingFiles.firstIndex(of: file) {
                            remainingFiles.remove(at: index)
                        }
                    }

                    guard !fileToContent.isEmpty else { continue }

                    let contents = fileToContent.map { $0.1 }
                    let payload: String
                    if contents.count == 1 {
                        payload = contents[0]
                    } else {
                        payload = "[" + contents.joined(separator: ",") + "]"
                    }

                    let semaphore = DispatchSemaphore(value: 0)
                    var sendSuccess = false
                    
                    self.transport.sendRaw(jsonString: payload) { success in
                        sendSuccess = success
                        semaphore.signal()
                    }
                    
                    let timeout = semaphore.wait(timeout: .now() + 30)
                    
                    if timeout == .timedOut {
                        break
                    }
                    
                    if sendSuccess {
                        self.dequeLock.lock()
                        for (file, _) in fileToContent {
                            try? fm.removeItem(at: file)
                        }
                        self.dequeLock.unlock()
                    } else {
                        break
                    }
                }
            } catch {
            }
        }
    }

    func flush() {
        queue.async { [weak self] in
            self?.flushIfNeeded()
        }
    }

    private func flushIfNeeded(ignoringShutdown: Bool = false) {
        if !ignoringShutdown {
            shutdownLock.lock()
            guard !isShutDown else {
                shutdownLock.unlock()
                return
            }
            shutdownLock.unlock()
        }
        
        flushLock.lock()
        guard !isFlushing else {
            flushLock.unlock()
            return
        }
        isFlushing = true
        flushLock.unlock()

        dequeLock.lock()
        let n = min(flushAt, deque.count)
        let toSend = Array(deque.prefix(n))
        deque.removeFirst(n)
        dequeLock.unlock()

        guard !toSend.isEmpty else { 
            // If no files to send, mark flush as complete
            flushLock.lock()
            isFlushing = false
            flushLock.unlock()
            return 
        }

        var fileToContent: [(URL, String)] = []
        let fm = FileManager.default

        for file in toSend {
            do {
                let content = try readFileContent(file)
                fileToContent.append((file, content))
            } catch {
                try? fm.removeItem(at: file)
            }
        }

        guard !fileToContent.isEmpty else { 
            // If no content to send, mark flush as complete
            flushLock.lock()
            isFlushing = false
            flushLock.unlock()
            return 
        }

        let contents = fileToContent.map { $0.1 }
        let payload: String
        if contents.count == 1 {
            payload = contents[0]
        } else {
            payload = "[" + contents.joined(separator: ",") + "]"
        }

        // Completion handler moves isFlushing reset to after send completes
        transport.sendRaw(jsonString: payload) { [weak self] success in
            guard let self = self else { return }
            if success {
                self.dequeLock.lock()
                for (file, _) in fileToContent {
                    try? fm.removeItem(at: file)
                }
                self.dequeLock.unlock()
            } else {
                self.dequeLock.lock()
                for (file, _) in fileToContent.reversed() {
                    self.deque.insert(file, at: 0)
                }
                self.dequeLock.unlock()
            }
            
            // Mark flush as complete only after send completes
            self.flushLock.lock()
            self.isFlushing = false
            self.flushLock.unlock()
        }
    }

    private func scheduleFlushTimer() {
        flushTimer?.cancel()
        flushTimerStateLock.lock()
        isFlushTimerPaused = false
        flushTimerStateLock.unlock()

        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(
            deadline: .now() + flushIntervalSeconds,
            repeating: flushIntervalSeconds,
            leeway: .seconds(1)
        )
        timer.setEventHandler { [weak self] in
            guard let self = self else { return }
            self.dequeLock.lock()
            let queueSize = self.deque.count
            self.dequeLock.unlock()
            if queueSize > 0 {
                self.flushIfNeeded()
            }
        }
        timer.resume()
        self.flushTimer = timer
    }

    private func readFileContent(_ file: URL) throws -> String {
        let data = try Data(contentsOf: file)

        if let decrypted = try? encryption.decrypt(data),
           let json = String(data: decrypted, encoding: .utf8) {
            return json
        }

        if let json = String(data: data, encoding: .utf8) {
            return json
        }

        throw NSError(
            domain: "com.pulse.sessionreplay",
            code: -1,
            userInfo: [NSLocalizedDescriptionKey: "Failed to read replay file: \(file.lastPathComponent)"]
        )
    }
    
    private func trimDiskFiles() {
        queue.async { [weak self] in
            guard let self: SessionReplayPersistingEmitter = self else { return }
            do {
                let fm: FileManager = FileManager.default
                let files: [URL] = try fm.contentsOfDirectory(at: self.storageDir, includingPropertiesForKeys: [.contentModificationDateKey])
                    .filter { $0.pathExtension == Self.fileExtension }
                    .sorted { url1, url2 in
                        let date1: Date = (try? url1.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
                        let date2: Date = (try? url2.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? Date.distantPast
                        return date1 < date2
                    }
                
                self.dequeLock.lock()
                self.deque = files
                self.dequeLock.unlock()
                
                self.evictOldestFilesIfNeeded()
            } catch {
            }
        }
    }
    
    private func evictOldestFilesIfNeeded() {
        let totalFiles: Int = deque.count
        if totalFiles > maxBatchSize {
            let filesToEvict: Int = totalFiles - maxBatchSize
            let filesToRemove: [URL] = Array(deque.prefix(filesToEvict))
            deque.removeFirst(filesToEvict)
            
            let fm: FileManager = FileManager.default
            for file: URL in filesToRemove {
                try? fm.removeItem(at: file)
            }
        }
    }
    
}
