import React from 'react';
import { Link } from 'react-router-dom';

export default function Home() {
  return (
    <div style={{ textAlign: 'center', paddingTop: 80 }}>
      <h1 style={{ fontSize: 48, fontWeight: 800, color: '#1a1a2e', marginBottom: 16 }}>
        Welcome to PulseStore
      </h1>
      <p style={{ fontSize: 18, color: '#64748b', marginBottom: 40, maxWidth: 480, margin: '0 auto 40px' }}>
        The demo storefront wired to the Pulse Web SDK. Every click, error, and route change is tracked.
      </p>
      <Link
        to="/products"
        style={{
          display: 'inline-block',
          background: '#4f46e5',
          color: '#fff',
          textDecoration: 'none',
          borderRadius: 10,
          padding: '14px 36px',
          fontWeight: 700,
          fontSize: 16,
        }}
      >
        Shop Now →
      </Link>
    </div>
  );
}
