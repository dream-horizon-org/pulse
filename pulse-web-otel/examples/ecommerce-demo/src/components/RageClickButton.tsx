import React, { useState } from 'react';

/** Click this button 3× within 700ms to trigger rage-click detection in M3. */
export function RageClickButton() {
  const [count, setCount] = useState(0);
  return (
    <div style={{ display: 'inline-flex', flexDirection: 'column', alignItems: 'center', gap: 8 }}>
      <button
        data-testid="rage-click-button"
        onClick={() => setCount(c => c + 1)}
        style={{
          background: '#ef4444',
          color: '#fff',
          border: 'none',
          borderRadius: 8,
          padding: '10px 20px',
          fontSize: 14,
          fontWeight: 600,
          cursor: 'pointer',
        }}
      >
        Click fast! 👆
      </button>
      <span style={{ fontSize: 12, color: '#94a3b8' }}>Clicks this session: {count}</span>
    </div>
  );
}
