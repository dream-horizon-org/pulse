import React from 'react';
import { useProducts } from '../hooks/useProducts';
import { useCart } from '../hooks/useCart';
import { ProductCard } from '../components/ProductCard';
import { RageClickButton } from '../components/RageClickButton';

export default function Products() {
  const { products, loading, error } = useProducts();
  const { addItem } = useCart();

  if (loading) return <p style={{ color: '#94a3b8', textAlign: 'center', padding: 40 }}>Loading products…</p>;
  if (error)   return <p style={{ color: '#ef4444', textAlign: 'center', padding: 40 }}>Error: {error}</p>;

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 28 }}>
        <h2 style={{ fontSize: 28, fontWeight: 700 }}>All Products</h2>
        <RageClickButton />
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(240px, 1fr))', gap: 20 }}>
        {products.map(p => (
          <ProductCard key={p.id} product={p} onAddToCart={addItem} />
        ))}
      </div>
    </div>
  );
}
