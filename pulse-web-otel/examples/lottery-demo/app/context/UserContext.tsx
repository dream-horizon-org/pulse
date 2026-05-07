"use client";

import React, {
  createContext,
  useContext,
  useState,
  useEffect,
  useCallback,
} from "react";

export interface MockUser {
  userId: string;
  name: string;
  mobile: string;
  walletBalance: number;
  onBoarding: boolean;
}

interface UserContextValue {
  user: MockUser | null;
  isLoading: boolean;
  setUser: (u: MockUser) => void;
  clearUser: () => void;
}

const UserContext = createContext<UserContextValue | null>(null);

const STORAGE_KEY = "lottery_demo_user";

export function UserProvider({ children }: { children: React.ReactNode }) {
  const [user, setUserState] = useState<MockUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) setUserState(JSON.parse(raw) as MockUser);
    } catch {
      // ignore
    }
    setIsLoading(false);
  }, []);

  const setUser = useCallback((u: MockUser) => {
    setUserState(u);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(u));
  }, []);

  const clearUser = useCallback(() => {
    setUserState(null);
    localStorage.removeItem(STORAGE_KEY);
  }, []);

  return (
    <UserContext.Provider value={{ user, isLoading, setUser, clearUser }}>
      {children}
    </UserContext.Provider>
  );
}

export function useUser() {
  const ctx = useContext(UserContext);
  if (!ctx) throw new Error("useUser must be inside UserProvider");
  return ctx;
}
