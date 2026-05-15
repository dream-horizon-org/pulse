import { useEffect, useMemo, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import rehypeSlug from 'rehype-slug';
import GithubSlugger from 'github-slugger';
import type { Doc, Heading } from '../types';
import { glossary } from '../lib/glossary';
import { rehypeEnhance } from '../lib/rehype-enhance';
import { buildRenderers } from '../lib/renderers';
import Hero from './Hero';
import MetaStrip from './MetaStrip';
import Toc from './Toc';
import styles from './DocViewer.module.css';

interface Props {
  doc: Doc;
}

export default function DocViewer({ doc }: Props) {
  const headings = useMemo(() => extractHeadings(doc.content), [doc.content]);
  const renderers = useMemo(() => buildRenderers(doc), [doc]);
  const enhancePlugin = useMemo(() => rehypeEnhance(glossary), []);
  const contentRef = useRef<HTMLElement>(null);

  // Reset scroll on doc change
  useEffect(() => {
    contentRef.current?.scrollTo({ top: 0, behavior: 'auto' });
  }, [doc.id]);

  // Intercept anchor clicks within rendered content so they scroll instead of changing route
  useEffect(() => {
    const root = contentRef.current;
    if (!root) return;
    const handler = (e: Event) => {
      const target = e.target as HTMLElement;
      const link = target.closest('a');
      if (!link) return;
      const href = link.getAttribute('href');
      if (!href) return;
      if (href.startsWith('#') && !href.startsWith('#/')) {
        e.preventDefault();
        const el = document.getElementById(href.slice(1));
        if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    };
    root.addEventListener('click', handler);
    return () => root.removeEventListener('click', handler);
  }, [doc.id]);

  return (
    <div className={styles.viewer}>
      <article className={styles.content} ref={contentRef}>
        <Hero doc={doc} />
        <MetaStrip doc={doc} />
        <ReactMarkdown
          remarkPlugins={[remarkGfm]}
          rehypePlugins={[rehypeSlug, enhancePlugin]}
          components={renderers}
        >
          {doc.content}
        </ReactMarkdown>
      </article>
      {headings.length > 0 && <Toc headings={headings} />}
    </div>
  );
}

function extractHeadings(md: string): Heading[] {
  const slugger = new GithubSlugger();
  const stripped = md.replace(/```[\s\S]*?```/g, '');
  const out: Heading[] = [];
  const re = /^(#{1,3})\s+(.+)$/gm;
  let m: RegExpExecArray | null;
  while ((m = re.exec(stripped))) {
    const level = m[1].length;
    const text = m[2].trim();
    out.push({ level, text, id: slugger.slug(text) });
  }
  return out;
}
