import type { FrontMatter } from './lib/frontmatter';

export interface Doc {
  id: string;
  title: string;
  path: string;
  group: string;
  fileName: string;
  content: string;
  frontmatter: FrontMatter;
}

export interface Heading {
  level: number;
  text: string;
  id: string;
}
