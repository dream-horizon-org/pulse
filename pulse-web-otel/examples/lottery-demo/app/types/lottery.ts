export interface PrizeBreakupItem {
  rank: string;
  amount: string;
  winners?: string;
}

export interface Lottery {
  id: string;
  name: string;
  tagVariant: "success" | "warning";
  prize: string;
  winnersCount: string;
  prizePool: string;
  timeLeft: string;
  drawDate: string;
  drawTime: string;
  saleStopMinutesBefore: number;
  price: number;
  buyerCount: number;
  series: string[];
  status: "live" | "expired";
  prizeBreakup: PrizeBreakupItem[];
}

export interface Order {
  id: string;
  lotteryId: string;
  lotteryName: string;
  tickets: string[];
  totalAmount: number;
  status: "confirmed" | "draw_complete" | "cancelled";
  drawDate: string;
  drawTime: string;
  purchasedAt: string;
  result?: "win" | "no_win";
}

export interface CartItem {
  series: string;
  number: string;
  quantity: number;
}
