/** Turn API slug (e.g. `home-decoration`) into a short title for UI. */
export function labelFromCategorySlug(slug: string): string {
  return slug
    .split('-')
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1))
    .join(' ');
}
