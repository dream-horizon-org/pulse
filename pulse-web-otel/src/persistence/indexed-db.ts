// IndexedDB signal buffer — failed OTLP exports (opt-in via diskBuffering).

import type {
  BufferedOtlpEnvelope,
  BufferedSignalRow,
  BufferedSignalType,
} from "../types/persistence";

export type {
  BufferedOtlpEnvelope,
  BufferedSignalRow,
  BufferedSignalType,
} from "../types/persistence";

const DB_NAME = "pulse_signal_buffer";
const DB_VERSION = 2;
const STORE_NAME = "signals";

const DEFAULT_MAX_AGE_MS = 24 * 60 * 60 * 1000;
const DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024;

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === "undefined") {
      reject(new Error("IndexedDB is not available"));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (db.objectStoreNames.contains(STORE_NAME)) {
        db.deleteObjectStore(STORE_NAME);
      }
      const store = db.createObjectStore(STORE_NAME, {
        keyPath: "id",
        autoIncrement: true,
      });
      store.createIndex("timestamp", "timestamp", { unique: false });
      store.createIndex("signalType", "signalType", { unique: false });
    };

    request.onsuccess = (event) => {
      resolve((event.target as IDBOpenDBRequest).result);
    };

    request.onerror = (event) => {
      reject((event.target as IDBOpenDBRequest).error);
    };
  });
}

export class IdbSignalBuffer {
  private readonly maxAgeMs: number;
  private readonly maxSizeBytes: number;

  constructor(maxAgeMs?: number, maxSizeBytes?: number) {
    this.maxAgeMs = maxAgeMs ?? DEFAULT_MAX_AGE_MS;
    this.maxSizeBytes = maxSizeBytes ?? DEFAULT_MAX_SIZE_BYTES;
  }

  async write(
    signalType: BufferedSignalType,
    envelope: BufferedOtlpEnvelope,
  ): Promise<void> {
    if (typeof indexedDB === "undefined") return;

    try {
      const db = await openDatabase();
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, "readwrite");
        const store = tx.objectStore(STORE_NAME);
        const row: Omit<BufferedSignalRow, "id"> = {
          signalType,
          envelope,
          timestamp: Date.now(),
          retryCount: 0,
        };
        const request = store.add(row);
        request.onsuccess = () => {
          db.close();
          resolve();
        };
        request.onerror = () => {
          db.close();
          reject(request.error);
        };
      });
      await this.pruneExpired();
      await this.enforceMaxSize();
    } catch {
      // ignore — SDK continues without persistence
    }
  }

  async readAll(): Promise<BufferedSignalRow[]> {
    if (typeof indexedDB === "undefined") return [];

    try {
      const db = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, "readonly");
        const store = tx.objectStore(STORE_NAME);
        const request = store.getAll();
        request.onsuccess = () => {
          db.close();
          resolve((request.result as BufferedSignalRow[]) ?? []);
        };
        request.onerror = () => {
          db.close();
          reject(request.error);
        };
      });
    } catch {
      return [];
    }
  }

  async delete(id: number): Promise<void> {
    if (typeof indexedDB === "undefined") return;

    try {
      const db = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, "readwrite");
        const store = tx.objectStore(STORE_NAME);
        const request = store.delete(id);
        request.onsuccess = () => {
          db.close();
          resolve();
        };
        request.onerror = () => {
          db.close();
          reject(request.error);
        };
      });
    } catch {
      // ignore
    }
  }

  async clear(): Promise<void> {
    if (typeof indexedDB === "undefined") return;

    try {
      const db = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, "readwrite");
        const store = tx.objectStore(STORE_NAME);
        const request = store.clear();
        request.onsuccess = () => {
          db.close();
          resolve();
        };
        request.onerror = () => {
          db.close();
          reject(request.error);
        };
      });
    } catch {
      // ignore
    }
  }

  private async pruneExpired(): Promise<void> {
    if (typeof indexedDB === "undefined") return;
    const cutoff = Date.now() - this.maxAgeMs;
    try {
      const db = await openDatabase();
      await new Promise<void>((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, "readwrite");
        const store = tx.objectStore(STORE_NAME);
        const index = store.index("timestamp");
        const range = IDBKeyRange.upperBound(cutoff);
        const request = index.openCursor(range);
        request.onsuccess = (event) => {
          const cursor = (event.target as IDBRequest<IDBCursorWithValue | null>)
            .result;
          if (cursor) {
            cursor.delete();
            cursor.continue();
          } else {
            db.close();
            resolve();
          }
        };
        request.onerror = () => {
          db.close();
          reject(request.error);
        };
      });
    } catch {
      // ignore
    }
  }

  /** Drop oldest rows until approximate serialized size is under maxSizeBytes. */
  private async enforceMaxSize(): Promise<void> {
    if (typeof indexedDB === "undefined") return;
    try {
      const rows = await this.readAll();
      const estimate = (r: BufferedSignalRow) =>
        (r.envelope?.bodyB64?.length ?? 0) * 0.75 + 256;
      let total = rows.reduce((s, r) => s + estimate(r), 0);
      if (total <= this.maxSizeBytes) return;

      const sorted = [...rows].sort((a, b) => a.timestamp - b.timestamp);
      for (const r of sorted) {
        if (total <= this.maxSizeBytes) break;
        if (r.id != null) {
          total -= estimate(r);
          await this.delete(r.id);
        }
      }
    } catch {
      // ignore
    }
  }
}
