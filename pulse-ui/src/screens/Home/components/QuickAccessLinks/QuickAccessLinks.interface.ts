export interface QuickLink {
  title: string;
  description: string;
  route: string;
  icon: React.ElementType;
}

export interface QuickAccessLinksProps {
  links: QuickLink[];
  onLinkClick: (route: string) => void;
}
