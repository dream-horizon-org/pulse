export interface TopInteractionsHealthProps {
  onViewAll: () => void;
  onCardClick: (interaction: { id: number; name: string }) => void;
}
