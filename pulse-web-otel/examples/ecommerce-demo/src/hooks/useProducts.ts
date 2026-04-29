import { useState, useEffect } from 'react';

export interface Product {
  id: string;
  name: string;
  price: number;
  category: string;
  imageUrl: string;
  rating: number;
  stock: number;
}

export function useProducts() {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading,  setLoading]  = useState(true);
  const [error,    setError]    = useState<string | null>(null);

  useEffect(() => {
    fetch('/api/products.json')
      .then(r => { if (!r.ok) throw new Error(`HTTP ${r.status}`); return r.json(); })
      .then((data: Product[]) => setProducts(data))
      .catch((e: unknown) => setError(e instanceof Error ? e.message : 'Failed to load'))
      .finally(() => setLoading(false));
  }, []);

  return { products, loading, error };
}
