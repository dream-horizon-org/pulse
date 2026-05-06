"use client";

import React, {
  createContext,
  useContext,
  useState,
  useCallback,
} from "react";
import type { CartItem } from "../types/lottery";

interface CartContextValue {
  items: CartItem[];
  addItem: (series: string, number: string) => void;
  removeItem: (series: string, number: string) => void;
  clearCart: () => void;
  totalTickets: number;
  totalAmount: (pricePerTicket: number) => number;
}

const CartContext = createContext<CartContextValue | null>(null);

export function CartProvider({ children }: { children: React.ReactNode }) {
  const [items, setItems] = useState<CartItem[]>([]);

  const addItem = useCallback((series: string, number: string) => {
    setItems((prev) => {
      const existing = prev.find(
        (i) => i.series === series && i.number === number,
      );
      if (existing) {
        return prev.map((i) =>
          i.series === series && i.number === number
            ? { ...i, quantity: i.quantity + 1 }
            : i,
        );
      }
      return [...prev, { series, number, quantity: 1 }];
    });
  }, []);

  const removeItem = useCallback((series: string, number: string) => {
    setItems((prev) =>
      prev.filter((i) => !(i.series === series && i.number === number)),
    );
  }, []);

  const clearCart = useCallback(() => setItems([]), []);

  const totalTickets = items.reduce((s, i) => s + i.quantity, 0);
  const totalAmount = (pricePerTicket: number) => totalTickets * pricePerTicket;

  return (
    <CartContext.Provider
      value={{ items, addItem, removeItem, clearCart, totalTickets, totalAmount }}
    >
      {children}
    </CartContext.Provider>
  );
}

export function useCart() {
  const ctx = useContext(CartContext);
  if (!ctx) throw new Error("useCart must be inside CartProvider");
  return ctx;
}
