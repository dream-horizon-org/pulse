import type { SnapshotEvent } from "./sessionReplaySnapshotTypes";

const DB_NAME = "PulseSessionReplaySnapshots";
const DB_VERSION = 1;
const STORE_BLOB_RANGES = "blobRanges";
const STORE_SESSION_ACCESS = "sessionAccess";
const TTL_MS = 3 * 60 * 60 * 1000;

export interface CachedBlobRange {
  sessionId: string;
  startBlobKey: string;
  endBlobKey: string;
  snapshots: SnapshotEvent[];
  fetchedAt: number;
}

export interface SessionAccessRecord {
  sessionId: string;
  lastAccessedAt: number;
}

function openDb(): Promise<IDBDatabase> {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DB_NAME, DB_VERSION);
    request.onerror = () => reject(request.error);
    request.onsuccess = () => resolve(request.result);
    request.onupgradeneeded = (event) => {
      const db = (event.target as IDBOpenDBRequest).result;
      if (!db.objectStoreNames.contains(STORE_BLOB_RANGES)) {
        const blobStore = db.createObjectStore(STORE_BLOB_RANGES, {
          keyPath: ["sessionId", "startBlobKey", "endBlobKey"],
        });
        blobStore.createIndex("sessionId", "sessionId", { unique: false });
      }
      if (!db.objectStoreNames.contains(STORE_SESSION_ACCESS)) {
        db.createObjectStore(STORE_SESSION_ACCESS, { keyPath: "sessionId" });
      }
    };
  });
}

function getRangeKey(startBlobKey: string, endBlobKey: string): string {
  return `${startBlobKey}-${endBlobKey}`;
}

/**
 * Mark session as accessed (for TTL). Call when user opens or views a session.
 */
export async function touchSession(sessionId: string): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_SESSION_ACCESS, "readwrite");
    const store = tx.objectStore(STORE_SESSION_ACCESS);
    const record: SessionAccessRecord = {
      sessionId,
      lastAccessedAt: Date.now(),
    };
    const request = store.put(record);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
    tx.oncomplete = () => db.close();
  });
}

/**
 * Get cached snapshot data for a blob range, if present.
 */
export async function getCachedBlobRange(
  sessionId: string,
  startBlobKey: string,
  endBlobKey: string,
): Promise<SnapshotEvent[] | null> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_BLOB_RANGES, "readonly");
    const store = tx.objectStore(STORE_BLOB_RANGES);
    const request = store.get([sessionId, startBlobKey, endBlobKey]);
    request.onsuccess = () => {
      const row = request.result as CachedBlobRange | undefined;
      resolve(row ? row.snapshots : null);
    };
    request.onerror = () => reject(request.error);
    tx.oncomplete = () => db.close();
  });
}

/**
 * Store snapshot data for a blob range.
 */
export async function setCachedBlobRange(
  sessionId: string,
  startBlobKey: string,
  endBlobKey: string,
  snapshots: SnapshotEvent[],
): Promise<void> {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(STORE_BLOB_RANGES, "readwrite");
    const store = tx.objectStore(STORE_BLOB_RANGES);
    const record: CachedBlobRange = {
      sessionId,
      startBlobKey,
      endBlobKey,
      snapshots,
      fetchedAt: Date.now(),
    };
    const request = store.put(record);
    request.onsuccess = () => resolve();
    request.onerror = () => reject(request.error);
    tx.oncomplete = () => db.close();
  });
}

/**
 * Remove all blob ranges and access records for sessions not accessed in the last 3 hours.
 * Call on app init or when entering session replay.
 */
export async function cleanupStaleSessions(): Promise<void> {
  const db = await openDb();
  const cutoff = Date.now() - TTL_MS;

  return new Promise((resolve, reject) => {
    const accessTx = db.transaction(STORE_SESSION_ACCESS, "readonly");
    const accessStore = accessTx.objectStore(STORE_SESSION_ACCESS);
    const listRequest = accessStore.getAll();

    listRequest.onsuccess = () => {
      const records = (listRequest.result as SessionAccessRecord[]) || [];
      const staleSessionIds = records
        .filter((r) => r.lastAccessedAt < cutoff)
        .map((r) => r.sessionId);

      if (staleSessionIds.length === 0) {
        db.close();
        return resolve();
      }

      const deleteTx = db.transaction(
        [STORE_BLOB_RANGES, STORE_SESSION_ACCESS],
        "readwrite",
      );
      const blobStore = deleteTx.objectStore(STORE_BLOB_RANGES);
      const blobIndex = blobStore.index("sessionId");
      const accessStoreWrite = deleteTx.objectStore(STORE_SESSION_ACCESS);

      let pending = staleSessionIds.length * 2; // delete blobs + delete access
      const done = () => {
        pending--;
        if (pending === 0) {
          db.close();
          resolve();
        }
      };

      staleSessionIds.forEach((sessionId) => {
        const rangeRequest = blobIndex.openCursor(IDBKeyRange.only(sessionId));
        rangeRequest.onsuccess = () => {
          const cursor = rangeRequest.result;
          if (cursor) {
            cursor.delete();
            cursor.continue();
          } else {
            done();
          }
        };
        rangeRequest.onerror = () => {
          done();
        };
        accessStoreWrite.delete(sessionId);
        done();
      });
    };

    listRequest.onerror = () => reject(listRequest.error);
    accessTx.oncomplete = () => {};
  });
}
