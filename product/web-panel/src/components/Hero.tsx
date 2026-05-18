import type { Doc } from '../types';
import styles from './Hero.module.css';

interface Props {
  doc: Doc;
}

const LAYER_LABELS: Record<string, string> = {
  detect: 'Detect',
  diagnose: 'Diagnose',
  quantify: 'Quantify',
  resolve: 'Resolve',
  predict: 'Predict',
  framework: 'Framework',
  meta: 'Meta',
};

export default function Hero({ doc }: Props) {
  const fm = doc.frontmatter;
  const variant = (fm.hero || 'flat').toLowerCase();
  if (variant === 'none') return null;

  const layer = fm.layer?.toLowerCase();
  const layerLabel = layer ? LAYER_LABELS[layer] : null;
  const personaLabel = fm.persona ? formatPersona(fm.persona) : null;

  return (
    <header className={`${styles.hero} ${styles[`variant-${variant}`] ?? ''} ${layer ? styles[`layer-${layer}`] : ''}`}>
      <div className={styles.eyebrow}>
        {layerLabel && <span className={`${styles.chip} ${styles.chipLayer}`}>{layerLabel}</span>}
        {personaLabel && <span className={`${styles.chip} ${styles.chipPersona}`}>{personaLabel}</span>}
      </div>
      <h1 className={styles.title}>{doc.title}</h1>
    </header>
  );
}

function formatPersona(value: string): string {
  return value
    .split(',')
    .map((p) => p.trim())
    .filter(Boolean)
    .map((p) => (p.toLowerCase() === 'ux' ? 'UX' : capitalize(p)))
    .join(' · ');
}

function capitalize(s: string): string {
  return s.charAt(0).toUpperCase() + s.slice(1).toLowerCase();
}
