import React, { useEffect, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import { Pulse } from '@dreamhorizonorg/pulse-web';
import { useCart } from "../hooks/useCart";

interface ProductDetail {
  id: string;
  name: string;
  price: number;
  category: string;
  imageUrl: string;
  rating: number;
  description: string;
  specs: Array<{ label: string; value: string }>;
}

export default function ProductDetail() {
  const { id } = useParams<{ id: string }>();
  const [product, setProduct] = useState<ProductDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const { addItem } = useCart();

  useEffect(() => {
    fetch(`/api/product-detail.json?id=${id}`)
      .then(r => r.json())
      .then(setProduct)
      .catch(() => setProduct(null))
      .finally(() => setLoading(false));
  }, [id]);

  useEffect(() => {
    if (!id) return;
    Pulse.trackEvent("product_detail_open", {
      product_id: id,
      path: window.location.pathname,
    });
  }, [id]);

  if (loading) return <p style={{ color: '#94a3b8', textAlign: 'center', padding: 40 }}>Loading…</p>;
  if (!product) return <p style={{ color: '#ef4444', textAlign: 'center', padding: 40 }}>Product not found.</p>;

  return (
    <div style={{ maxWidth: 800, margin: '0 auto' }}>
      <Link to="/products" style={{ color: '#4f46e5', textDecoration: 'none', fontSize: 14, display: 'inline-block', marginBottom: 24 }}>
        ← Back to products
      </Link>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 40, background: '#fff', borderRadius: 16, padding: 32, boxShadow: '0 1px 3px rgba(0,0,0,.08)' }}>
        <img src={product.imageUrl} alt={product.name} style={{ width: '100%', borderRadius: 10, objectFit: 'cover' }} />
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          <span style={{ fontSize: 12, color: '#94a3b8', textTransform: 'uppercase', letterSpacing: 1 }}>{product.category}</span>
          <h1 style={{ fontSize: 24, fontWeight: 700, lineHeight: 1.3 }}>{product.name}</h1>
          <p style={{ color: '#64748b', lineHeight: 1.6 }}>{product.description}</p>
          <div style={{ fontWeight: 800, fontSize: 28, color: '#4f46e5' }}>${product.price.toFixed(2)}</div>
          <button
            onClick={() => {
              addItem({
                id: product.id,
                name: product.name,
                price: product.price,
              });
              Pulse.trackEvent("add_to_cart", {
                product_id: product.id,
                product_name: product.name,
                price: product.price,
                source: "product_detail",
              });
            }}
            style={{ background: '#4f46e5', color: '#fff', border: 'none', borderRadius: 10, padding: '12px 28px', fontWeight: 700, fontSize: 15, cursor: 'pointer' }}
          >
            Add to Cart
          </button>
        </div>
      </div>
      <div style={{ marginTop: 32, background: '#fff', borderRadius: 16, padding: 32, boxShadow: '0 1px 3px rgba(0,0,0,.08)' }}>
        <h3 style={{ fontWeight: 700, marginBottom: 16 }}>Specifications</h3>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <tbody>
            {product.specs.map(s => (
              <tr key={s.label} style={{ borderBottom: '1px solid #f1f5f9' }}>
                <td style={{ padding: '10px 0', color: '#64748b', width: '40%' }}>{s.label}</td>
                <td style={{ padding: '10px 0', fontWeight: 600 }}>{s.value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
