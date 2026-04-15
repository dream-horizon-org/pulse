// M1: IndexedDB signal buffer for offline/retry persistence.
// Stores OTLP signals when export fails, drains on reconnect.

interface BufferedSignal {
  id?: number; // autoIncrement
  signalType: 'trace' | 'log' | 'metric';
  payload: string;
  timestamp: number;
  retryCount: number;
}

const DB_NAME = 'pulse_signal_buffer';
const DB_VERSION = 1;
const STORE_NAME = 'signals';

const DEFAULT_MAX_AGE_MS = 24 * 60 * 60 * 1000; // 24 hours
const DEFAULT_MAX_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

function openDatabase(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    if (typeof indexedDB === 'undefined') {
      reject(new Error('IndexedDB is not available'));
      return;
    }

    const request = indexedDB.open(DB_NAME, DB_VERSION);

    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_NAME)) {
        const store = db.createObjectStore(STORE_NAME, {
          keyPath: 'id',
          autoIncrement: true,
        });
        store.createIndex('timestamp', 'timestamp', { unique: false });
        store.createIndex('signalType', 'signalType', { unique: false });
      }
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

  async write(signalType: BufferedSignal['signalType'], payload: string): Promise<void> {
    if (typeof indexedDB === 'undefined') return;

    try {
      const db = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);

        const signal: Omit<BufferedSignal, 'id'> = {
          signalType,
          payload,
          timestamp: Date.now(),
          retryCount: 0,
        };

        const request = store.add(signal);
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
      // ignore IndexedDB errors — signals are lost but SDK continues
    }
  }

  async drain(): Promise<BufferedSignal[]> {
    if (typeof indexedDB === 'undefined') return [];

    try {
      const db = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readonly');
        const store = tx.objectStore(STORE_NAME);
        const request = store.getAll();

        request.onsuccess = () => {
          db.close();
          resolve(request.result as BufferedSignal[]);
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
    if (typeof indexedDB === 'undefined') return;

    try {
      const db = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readwrite');
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
      // ignore errors
    }
  }

  async clear(): Promise<void> {
    if (typeof indexedDB === 'undefined') return;

    try {
      const db = await openDatabase();
      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readwrite');
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
      // ignore errors
    }
  }

  async prune(): Promise<void> {
    if (typeof indexedDB === 'undefined') return;

    try {
      const cutoff = Date.now() - this.maxAgeMs;
      const db = await openDatabase();

      return new Promise((resolve, reject) => {
        const tx = db.transaction(STORE_NAME, 'readwrite');
        const store = tx.objectStore(STORE_NAME);
        const index = store.index('timestamp');
        const range = IDBKeyRange.upperBound(cutoff);
        const request = index.openCursor(range);

        request.onsuccess = (event) => {
          const cursor = (event.target as IDBRequest<IDBCursorWithValue | null>).result;
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
      // ignore errors
    }

    void this.maxSizeBytes; // referenced to avoid unused warning
  }
}
