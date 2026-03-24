import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { MarkdownContentProps } from "./MarkdownContent.interface";

/**
 * Reusable markdown renderer (ReactMarkdown + remarkGfm).
 * Use this app-wide so a future markdown library change only requires updating this component.
 */
export function MarkdownContent({ content, className }: MarkdownContentProps) {
  return (
    <div className={className}>
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
    </div>
  );
}
