import AsyncStorage from '@react-native-async-storage/async-storage';
import React, {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import type { CartLine, Order, WishlistItem } from '../lib/types';

const KEYS = {
  cart: '@expo-shop/cart',
  wishlist: '@expo-shop/wishlist',
  orders: '@expo-shop/orders',
  auth: '@expo-shop/auth',
  recent: '@expo-shop/recent',
} as const;

type AuthPersist = { token: string; username: string } | null;

type ShopContextValue = {
  ready: boolean;
  token: string | null;
  username: string | null;
  cart: CartLine[];
  wishlist: WishlistItem[];
  orders: Order[];
  recentProductIds: number[];
  cartCount: number;
  cartTotal: number;
  setAuth: (token: string, username: string) => Promise<void>;
  logout: () => Promise<void>;
  addToCart: (
    line: Omit<CartLine, 'quantity'>,
    qty?: number
  ) => Promise<{ lineQty: number; cartItems: number }>;
  setLineQuantity: (productId: number, quantity: number) => Promise<void>;
  removeFromCart: (productId: number) => Promise<void>;
  toggleWishlist: (item: WishlistItem) => Promise<void>;
  isWishlisted: (productId: number) => boolean;
  recordProductView: (productId: number) => Promise<void>;
  placeOrder: (lines: CartLine[]) => Promise<string>;
};

const ShopContext = createContext<ShopContextValue | null>(null);

function newOrderId(): string {
  return `${Date.now()}-${Math.random().toString(36).slice(2, 9)}`;
}

export function ShopProvider({ children }: { children: React.ReactNode }) {
  const [ready, setReady] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [username, setUsername] = useState<string | null>(null);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [wishlist, setWishlist] = useState<WishlistItem[]>([]);
  const [orders, setOrders] = useState<Order[]>([]);
  const [recentProductIds, setRecentProductIds] = useState<number[]>([]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      try {
        const [cRaw, wRaw, oRaw, aRaw, rRaw] = await Promise.all([
          AsyncStorage.getItem(KEYS.cart),
          AsyncStorage.getItem(KEYS.wishlist),
          AsyncStorage.getItem(KEYS.orders),
          AsyncStorage.getItem(KEYS.auth),
          AsyncStorage.getItem(KEYS.recent),
        ]);
        if (cancelled) return;
        if (cRaw) setCart(JSON.parse(cRaw) as CartLine[]);
        if (wRaw) setWishlist(JSON.parse(wRaw) as WishlistItem[]);
        if (oRaw) setOrders(JSON.parse(oRaw) as Order[]);
        if (aRaw) {
          const a = JSON.parse(aRaw) as AuthPersist;
          if (a?.token && a?.username) {
            setToken(a.token);
            setUsername(a.username);
          }
        }
        if (rRaw) setRecentProductIds(JSON.parse(rRaw) as number[]);
      } catch {
        /* ignore corrupt storage */
      } finally {
        if (!cancelled) setReady(true);
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const persistCart = useCallback(async (next: CartLine[]) => {
    setCart(next);
    await AsyncStorage.setItem(KEYS.cart, JSON.stringify(next));
  }, []);

  const setAuth = useCallback(async (t: string, u: string) => {
    setToken(t);
    setUsername(u);
    const payload: AuthPersist = { token: t, username: u };
    await AsyncStorage.setItem(KEYS.auth, JSON.stringify(payload));
  }, []);

  const logout = useCallback(async () => {
    setToken(null);
    setUsername(null);
    await AsyncStorage.removeItem(KEYS.auth);
  }, []);

  const addToCart = useCallback(
    async (line: Omit<CartLine, 'quantity'>, qty = 1) => {
      const q = Math.max(1, Math.floor(qty));
      return new Promise<{ lineQty: number; cartItems: number }>((resolve) => {
        setCart((prev) => {
          const i = prev.findIndex((l) => l.productId === line.productId);
          let next: CartLine[];
          if (i >= 0) {
            const existing = prev[i]!;
            next = [...prev];
            next[i] = { ...existing, quantity: existing.quantity + q };
          } else {
            next = [...prev, { ...line, quantity: q }];
          }
          void AsyncStorage.setItem(KEYS.cart, JSON.stringify(next));
          const row = next.find((l) => l.productId === line.productId)!;
          const cartItems = next.reduce((n, l) => n + l.quantity, 0);
          queueMicrotask(() => resolve({ lineQty: row.quantity, cartItems }));
          return next;
        });
      });
    },
    []
  );

  const setLineQuantity = useCallback(
    async (productId: number, quantity: number) => {
      const q = Math.floor(quantity);
      if (q <= 0) {
        setCart((prev) => {
          const next = prev.filter((l) => l.productId !== productId);
          void AsyncStorage.setItem(KEYS.cart, JSON.stringify(next));
          return next;
        });
        return;
      }
      setCart((prev) => {
        const next = prev.map((l) =>
          l.productId === productId ? { ...l, quantity: q } : l
        );
        void AsyncStorage.setItem(KEYS.cart, JSON.stringify(next));
        return next;
      });
    },
    []
  );

  const removeFromCart = useCallback(async (productId: number) => {
    setCart((prev) => {
      const next = prev.filter((l) => l.productId !== productId);
      void AsyncStorage.setItem(KEYS.cart, JSON.stringify(next));
      return next;
    });
  }, []);

  const toggleWishlist = useCallback(async (item: WishlistItem) => {
    setWishlist((prev) => {
      const exists = prev.some((w) => w.productId === item.productId);
      const next = exists
        ? prev.filter((w) => w.productId !== item.productId)
        : [...prev, item];
      void AsyncStorage.setItem(KEYS.wishlist, JSON.stringify(next));
      return next;
    });
  }, []);

  const isWishlisted = useCallback(
    (productId: number) => wishlist.some((w) => w.productId === productId),
    [wishlist]
  );

  const recordProductView = useCallback(async (productId: number) => {
    setRecentProductIds((prev) => {
      const rest = prev.filter((id) => id !== productId);
      const next = [productId, ...rest].slice(0, 20);
      void AsyncStorage.setItem(KEYS.recent, JSON.stringify(next));
      return next;
    });
  }, []);

  const placeOrder = useCallback(
    async (lines: CartLine[]) => {
      const total = lines.reduce((s, l) => s + l.price * l.quantity, 0);
      const id = newOrderId();
      const order: Order = {
        id,
        createdAt: new Date().toISOString(),
        lines: lines.map((l) => ({ ...l })),
        total,
      };
      setOrders((prev) => {
        const next = [order, ...prev];
        void AsyncStorage.setItem(KEYS.orders, JSON.stringify(next));
        return next;
      });
      await persistCart([]);
      return id;
    },
    [persistCart]
  );

  const cartCount = useMemo(
    () => cart.reduce((n, l) => n + l.quantity, 0),
    [cart]
  );
  const cartTotal = useMemo(
    () => cart.reduce((s, l) => s + l.price * l.quantity, 0),
    [cart]
  );

  const value = useMemo<ShopContextValue>(
    () => ({
      ready,
      token,
      username,
      cart,
      wishlist,
      orders,
      recentProductIds,
      cartCount,
      cartTotal,
      setAuth,
      logout,
      addToCart,
      setLineQuantity,
      removeFromCart,
      toggleWishlist,
      isWishlisted,
      recordProductView,
      placeOrder,
    }),
    [
      ready,
      token,
      username,
      cart,
      wishlist,
      orders,
      recentProductIds,
      cartCount,
      cartTotal,
      setAuth,
      logout,
      addToCart,
      setLineQuantity,
      removeFromCart,
      toggleWishlist,
      isWishlisted,
      recordProductView,
      placeOrder,
    ]
  );

  return <ShopContext.Provider value={value}>{children}</ShopContext.Provider>;
}

export function useShop(): ShopContextValue {
  const ctx = useContext(ShopContext);
  if (!ctx) throw new Error('useShop must be used within ShopProvider');
  return ctx;
}
