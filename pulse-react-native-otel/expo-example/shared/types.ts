export type Product = {
  id: number;
  title: string;
  price: number;
  description: string;
  category: string;
  image: string;
  rating?: { rate: number; count: number };
};

export type CartLine = {
  productId: number;
  title: string;
  price: number;
  image: string;
  quantity: number;
};

/** Snapshot for wishlist (API-free display). */
export type WishlistItem = {
  productId: number;
  title: string;
  price: number;
  image: string;
};

export type OrderLine = CartLine;

export type Order = {
  id: string;
  createdAt: string;
  lines: OrderLine[];
  total: number;
};

export type AuthState = {
  token: string | null;
  username: string | null;
};

export type SortKey = 'default' | 'price-asc' | 'price-desc' | 'title';
