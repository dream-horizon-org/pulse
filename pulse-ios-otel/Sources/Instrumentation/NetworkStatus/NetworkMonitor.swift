/*
 * Copyright The Pulse Authors
 * SPDX-License-Identifier: Apache-2.0
 */

#if !os(watchOS)

  import Foundation

  import Network

  public class NetworkMonitor: NetworkMonitorProtocol {
    let monitor = NWPathMonitor()
    var connection: Connection = .unavailable
    let monitorQueue = DispatchQueue(label: "OTel-Network-Monitor")
    let lock = NSLock()

    deinit {
      monitor.cancel()
    }

    public init() throws {
      let pathHandler = { (path: NWPath) in
        let availableInterfaces = path.availableInterfaces
        let wifiInterface = self.getWifiInterface(interfaces: availableInterfaces)
        let cellInterface = self.getCellInterface(interfaces: availableInterfaces)
        var availableInterface: Connection = .unavailable
        if cellInterface != nil {
          availableInterface = .cellular
        }
        if wifiInterface != nil {
          availableInterface = .wifi
        }
        self.lock.lock()
        let previous = self.connection
        switch path.status {
        case .requiresConnection, .satisfied:
          self.connection = availableInterface
        case .unsatisfied:
          self.connection = .unavailable
        @unknown default:
          self.lock.unlock()
          fatalError()
        }
        let newConnection = self.connection
        self.lock.unlock()
        if previous != .unavailable, newConnection == .unavailable {
        PulseLogger.info("sdk.network.export_blocked reason=no_network")
        }
      }
      monitor.pathUpdateHandler = pathHandler
      monitor.start(queue: monitorQueue)
    }

    public func getConnection() -> Connection {
      lock.lock()
      defer {
        lock.unlock()
      }
      return connection
    }

    func getCellInterface(interfaces: [NWInterface]) -> NWInterface? {
      var foundInterface: NWInterface?
      interfaces.forEach { interface in
        if interface.type == .cellular {
          foundInterface = interface
        }
      }
      return foundInterface
    }

    func getWifiInterface(interfaces: [NWInterface]) -> NWInterface? {
      var foundInterface: NWInterface?
      interfaces.forEach { interface in
        if interface.type == .wifi {
          foundInterface = interface
        }
      }
      return foundInterface
    }
  }

#endif
