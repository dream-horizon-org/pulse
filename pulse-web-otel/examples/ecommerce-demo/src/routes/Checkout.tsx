import React, { useState } from 'react';
import { Pulse } from '@dreamhorizonorg/pulse-web';

const STEPS = ['Shipping', 'Payment', 'Review'] as const;

const inputStyle: React.CSSProperties = {
  width: '100%',
  padding: '10px 14px',
  border: '1px solid #e2e8f0',
  borderRadius: 8,
  fontSize: 15,
  outline: 'none',
};

export default function Checkout() {
  const [step, setStep] = useState(0);
  const [done, setDone] = useState(false);

  function advance() {
    const next = step + 1;
    Pulse.trackEvent(`checkout_step_${step + 1}`);

    if (next < STEPS.length) {
      setStep(next);
    } else {
      Pulse.trackEvent('checkout_complete');
      setDone(true);
    }
  }

  if (done) {
    return (
      <div style={{ textAlign: 'center', paddingTop: 80 }}>
        <p style={{ fontSize: 48, marginBottom: 16 }}>🎉</p>
        <h2 style={{ fontWeight: 700, marginBottom: 8 }}>Order placed!</h2>
        <p style={{ color: '#64748b' }}>Your interaction span with APDEX score is now in ClickHouse.</p>
      </div>
    );
  }

  return (
    <div style={{ maxWidth: 540, margin: '0 auto' }}>
      <h2 style={{ fontSize: 28, fontWeight: 700, marginBottom: 8 }}>Checkout</h2>

      {/* Step progress */}
      <div style={{ display: 'flex', gap: 0, marginBottom: 32 }}>
        {STEPS.map((label, i) => (
          <div key={label} style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6 }}>
            <div style={{
              width: 32, height: 32, borderRadius: '50%',
              background: i <= step ? '#4f46e5' : '#e2e8f0',
              color: i <= step ? '#fff' : '#94a3b8',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              fontWeight: 700, fontSize: 14,
            }}>
              {i < step ? '✓' : i + 1}
            </div>
            <span style={{ fontSize: 12, color: i <= step ? '#4f46e5' : '#94a3b8', fontWeight: i === step ? 700 : 400 }}>
              {label}
            </span>
          </div>
        ))}
      </div>

      <div style={{ background: '#fff', borderRadius: 16, padding: 32, boxShadow: '0 1px 3px rgba(0,0,0,.08)' }}>
        {step === 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h3 style={{ fontWeight: 700, marginBottom: 4 }}>Shipping address</h3>
            <input style={inputStyle} placeholder="Full name" defaultValue="Jane Smith" />
            <input style={inputStyle} placeholder="Street address" defaultValue="123 Main St" />
            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: 12 }}>
              <input style={inputStyle} placeholder="City" defaultValue="San Francisco" />
              <input style={inputStyle} placeholder="ZIP" defaultValue="94105" />
            </div>
            <button
              data-testid="checkout-step-1-next"
              onClick={advance}
              style={{ background: '#4f46e5', color: '#fff', border: 'none', borderRadius: 10, padding: '13px', fontWeight: 700, fontSize: 15, cursor: 'pointer', marginTop: 8 }}
            >
              Continue to Payment →
            </button>
          </div>
        )}

        {step === 1 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h3 style={{ fontWeight: 700, marginBottom: 4 }}>Payment details</h3>
            <input style={inputStyle} placeholder="Card number" defaultValue="4242 4242 4242 4242" />
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <input style={inputStyle} placeholder="MM / YY" defaultValue="12 / 27" />
              <input style={inputStyle} placeholder="CVC" defaultValue="123" />
            </div>
            <button
              data-testid="checkout-step-2-next"
              onClick={advance}
              style={{ background: '#4f46e5', color: '#fff', border: 'none', borderRadius: 10, padding: '13px', fontWeight: 700, fontSize: 15, cursor: 'pointer', marginTop: 8 }}
            >
              Review Order →
            </button>
          </div>
        )}

        {step === 2 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <h3 style={{ fontWeight: 700, marginBottom: 4 }}>Review &amp; confirm</h3>
            <p style={{ color: '#64748b', lineHeight: 1.6 }}>
              Shipping to <strong>Jane Smith, 123 Main St, San Francisco 94105</strong>.<br />
              Charged to card ending in <strong>4242</strong>.
            </p>
            <button
              data-testid="checkout-step-3-confirm"
              onClick={advance}
              style={{ background: '#10b981', color: '#fff', border: 'none', borderRadius: 10, padding: '13px', fontWeight: 700, fontSize: 15, cursor: 'pointer', marginTop: 8 }}
            >
              Place Order ✓
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
