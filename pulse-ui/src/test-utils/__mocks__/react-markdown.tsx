import React from "react";

const ReactMarkdown = ({ children }: { children: string }) => (
  <div data-testid="react-markdown">{children}</div>
);

export default ReactMarkdown;
