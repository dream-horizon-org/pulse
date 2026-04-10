import {
  getFirebaseAuth,
  isGcpMultiTenantEnabled,
} from "../../config/firebase";

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
