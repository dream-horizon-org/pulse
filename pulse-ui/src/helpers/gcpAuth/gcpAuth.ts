import {
  signInWithPopup,
  GoogleAuthProvider,
  User,
  Auth,
} from "firebase/auth";
import {
  getFirebaseAuth,
  isGcpMultiTenantEnabled,
} from "../../config/firebase";

export type GcpAuthResult = {
  idToken: string;
  user: User;
  email: string;
  tenantId: string | undefined;
};

/**
 * Signs in with Google using Firebase Authentication with multi-tenancy.
 *
 * @param gcpTenantId - The GCP tenant ID obtained from the tenant lookup API
 * @returns Promise with authentication result including idToken
 */
export async function signInWithGoogleGcp(
  gcpTenantId: string,
): Promise<GcpAuthResult> {
  const auth = getFirebaseAuth() as Auth & { tenantId?: string | null };

  // Set the tenant ID for multi-tenant authentication
  auth.tenantId = gcpTenantId;

  const provider = new GoogleAuthProvider();

  try {
    const result = await signInWithPopup(auth, provider);
    const idToken = await result.user.getIdToken();
    const email = result.user.email ?? "";

    return {
      idToken,
      user: result.user,
      email,
      tenantId: gcpTenantId,
    };
  } finally {
    // Clear tenant ID after sign-in attempt
    auth.tenantId = null;
  }
}

export async function getFirebaseIdToken(): Promise<string | null> {
  if (!isGcpMultiTenantEnabled()) return null;
  const auth = getFirebaseAuth();
  const user = auth.currentUser;
  if (!user) return null;
  return user.getIdToken();
}

export async function signOutFirebase(): Promise<void> {
  if (!isGcpMultiTenantEnabled()) return;
  const auth = getFirebaseAuth();
  await auth.signOut();
}

export { isGcpMultiTenantEnabled };
