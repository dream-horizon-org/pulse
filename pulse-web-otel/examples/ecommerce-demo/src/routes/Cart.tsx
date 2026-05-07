import React, { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { Pulse } from '@dreamhorizon/pulse-web';
import { useCart } from '../hooks/useCart';

export default function Cart() {
  const { items, removeItem, total } = useCart();

  useEffect(() => {
    Pulse.trackEvent("cart_open", { item_count: items.length });
  }, [items.length]);

  if (items.length === 0) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 80 }}>
        <p style={{ fontSize: 48, marginBottom: 16 }}>🛒</p>
        <p style={{ color: '#64748b', marginBottom: 24 }}>Your cart is empty.</p>
        <Link to="/products" style={{ color: '#4f46e5', textDecoration: 'none', fontWeight: 600 }}>Browse products →</Link>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 640, margin: '0 auto' }}>
      <h2 style={{ fontSize: 28, fontWeight: 700, marginBottom: 24 }}>Your Cart</h2>
      <div style={{ background: '#fff', borderRadius: 16, overflow: 'hidden', boxShadow: '0 1px 3px rgba(0,0,0,.08)' }}>
        {items.map((item, i) => (
          <div
            key={item.id}
            style={{
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              padding: '16px 24px',
              borderBottom: i < items.length - 1 ? '1px solid #f1f5f9' : 'none',
            }}
          >
            <div>
              <p style={{ fontWeight: 600 }}>{item.name}</p>
              <p style={{ fontSize: 13, color: '#94a3b8' }}>Qty: {item.qty} × ${item.price.toFixed(2)}</p>
            </div>
            <div style={{ display: 'flex', gap: 16, alignItems: 'center' }}>
              <span style={{ fontWeight: 700, color: '#4f46e5' }}>${(item.price * item.qty).toFixed(2)}</span>
              <button
                onClick={() => { removeItem(item.id); Pulse.trackEvent('cart_remove_item', { item_id: item.id, item_name: item.name }); }}
                style={{ background: 'none', border: '1px solid #e2e8f0', borderRadius: 6, padding: '4px 10px', cursor: 'pointer', color: '#ef4444', fontSize: 13 }}
              >
                Remove
              </button>
            </div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <span style={{ fontSize: 20, fontWeight: 700 }}>Total: ${total.toFixed(2)}</span>
        <Link
          to="/checkout"
          onClick={() => Pulse.trackEvent('cart_checkout_click', { item_count: items.length, total })}
          style={{ background: '#4f46e5', color: '#fff', textDecoration: 'none', borderRadius: 10, padding: '12px 28px', fontWeight: 700 }}
        >
          Checkout →
        </Link>
      </div>
    </div>
  );
}
