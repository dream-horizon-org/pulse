import React from 'react';
import { Link } from 'react-router-dom';
import type { Product } from '../hooks/useProducts';

interface Props {
  product: Product;
  onAddToCart: (product: Product) => void;
}

export function ProductCard({ product, onAddToCart }: Props) {
  return (
    <div
      data-testid="product-card"
      style={{
        background: '#fff',
        borderRadius: 12,
        overflow: 'hidden',
        boxShadow: '0 1px 3px rgba(0,0,0,.08)',
        display: 'flex',
        flexDirection: 'column',
        transition: 'box-shadow .15s',
      }}
    >
      <Link to={`/products/${product.id}`} style={{ display: 'block' }}>
        <img
          src={product.imageUrl}
          alt={product.name}
          style={{ width: '100%', height: 180, objectFit: 'cover', display: 'block' }}
          onError={e => { (e.target as HTMLImageElement).src = 'https://picsum.photos/300/180'; }}
        />
      </Link>
      <div style={{ padding: '16px', flex: 1, display: 'flex', flexDirection: 'column', gap: 8 }}>
        <span style={{ fontSize: 11, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1 }}>
          {product.category}
        </span>
        <Link
          to={`/products/${product.id}`}
          style={{ color: '#1a1a2e', textDecoration: 'none', fontWeight: 600, fontSize: 15, lineHeight: 1.4 }}
        >
          {product.name}
        </Link>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <span style={{ color: '#f59e0b', fontSize: 13 }}>{'★'.repeat(Math.round(product.rating))}</span>
          <span style={{ fontSize: 12, color: '#94a3b8' }}>{product.rating.toFixed(1)}</span>
        </div>
        <div style={{ marginTop: 'auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontWeight: 700, fontSize: 18, color: '#4f46e5' }}>${product.price.toFixed(2)}</span>
          <button
            onClick={() => onAddToCart(product)}
            style={{
              background: '#4f46e5',
              color: '#fff',
              border: 'none',
              borderRadius: 8,
              padding: '8px 16px',
              fontSize: 13,
              fontWeight: 600,
              cursor: 'pointer',
            }}
          >
            Add to cart
          </button>
        </div>
      </div>
    </div>
  );
}
