import { getApp, getApps, initializeApp, FirebaseApp } from "firebase/app";
import { getAuth, connectAuthEmulator, Auth } from "firebase/auth";

const firebaseConfig = {
  apiKey: process.env.REACT_APP_FIREBASE_API_KEY,
  authDomain:
    process.env.REACT_APP_FIREBASE_AUTH_DOMAIN ||
    (process.env.REACT_APP_FIREBASE_PROJECT_ID
      ? `${process.env.REACT_APP_FIREBASE_PROJECT_ID}.firebaseapp.com`
      : undefined),
  projectId: process.env.REACT_APP_FIREBASE_PROJECT_ID,
  storageBucket: process.env.REACT_APP_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.REACT_APP_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.REACT_APP_FIREBASE_APP_ID,
};

let app: FirebaseApp;
let auth: Auth;

export function getFirebaseAuth(): Auth {
  if (!auth) {
    app = getApps().length ? getApp() : initializeApp(firebaseConfig);
    auth = getAuth(app);
    if (process.env.REACT_APP_FIREBASE_AUTH_EMULATOR === "true") {
      try {
        connectAuthEmulator(auth, "http://127.0.0.1:9099");
      } catch {}
    }
  }
  return auth;
}

/**
 * Check if GCP multi-tenant authentication is enabled.
 * When enabled, the app will use the tenant lookup API to get the gcpTenantId
 * based on the current hostname subdomain.
 */
export function isGcpMultiTenantEnabled(): boolean {
  return process.env.REACT_APP_GCP_MULTI_TENANT_ENABLED === "true";
}
