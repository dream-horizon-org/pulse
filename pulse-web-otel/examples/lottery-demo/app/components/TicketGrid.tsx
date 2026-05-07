"use client";

import { useState, useMemo } from "react";
import { Pulse } from "@dreamhorizon/pulse-web";
import { useCart } from "../context/CartContext";

const SERIES_LABELS = ["A", "B", "C"];
const TOTAL_TICKETS = 10_000;
const PAGE_SIZE = 200;

interface Props {
  series: string[];
  lotteryId: string;
}

export function TicketGrid({ series, lotteryId }: Props) {
  const { addItem, items: cartItems } = useCart();
  const [search, setSearch] = useState("");
  const [selectedSeries, setSelectedSeries] = useState(series[0] ?? "A");
  const [page, setPage] = useState(0);

  const allTickets = useMemo(
    () =>
      Array.from({ length: TOTAL_TICKETS }, (_, i) =>
        String(i).padStart(4, "0"),
      ),
    [],
  );

  const filtered = useMemo(() => {
    const q = search.trim();
    if (!q) return allTickets;
    return allTickets.filter((t) => t.includes(q));
  }, [allTickets, search]);

  const paginated = filtered.slice(page * PAGE_SIZE, (page + 1) * PAGE_SIZE);
  const totalPages = Math.ceil(filtered.length / PAGE_SIZE);

  const inCart = (num: string) =>
    cartItems.some((i) => i.series === selectedSeries && i.number === num);

  function handleTicketClick(num: string) {
    addItem(selectedSeries, num);
    Pulse.trackEvent("ticket_pick_manual", {
      lottery_id: lotteryId,
      ticket: `${selectedSeries}/${num}`,
    });
  }

  return (
    <div className="bg-white rounded-2xl shadow-card overflow-hidden">
      {/* Controls */}
      <div className="p-4 border-b border-gray-100 space-y-3">
        <input
          type="text"
          value={search}
          onChange={(e) => {
            setSearch(e.target.value);
            setPage(0);
          }}
          placeholder="Search ticket number (e.g. 1234)"
          className="w-full px-3 py-2 rounded-lg border border-gray-200 text-sm outline-none focus:border-sapphire transition-colors"
        />
        {series.length > 1 && (
          <div className="flex gap-2">
            {series.map((s) => (
              <button
                key={s}
                onClick={() => { setSelectedSeries(s); setPage(0); }}
                className={`px-4 py-1.5 rounded-full text-xs font-semibold transition-colors ${
                  selectedSeries === s
                    ? "bg-sapphire text-white"
                    : "bg-gray-100 text-gray-600"
                }`}
              >
                Series {s}
              </button>
            ))}
          </div>
        )}
        <div className="text-xs text-gray-400">
          Showing {paginated.length} of {filtered.length} tickets
        </div>
      </div>

      {/* Grid */}
      <div className="p-3 grid grid-cols-5 gap-1.5 max-h-96 overflow-y-auto">
        {paginated.map((num) => (
          <button
            key={num}
            onClick={() => handleTicketClick(num)}
            className={`aspect-square flex items-center justify-center rounded-lg text-xs font-mono font-semibold transition-all ${
              inCart(num)
                ? "bg-sapphire text-white scale-95"
                : "bg-gray-50 text-gray-700 hover:bg-gold/20 active:scale-95"
            }`}
          >
            {num}
          </button>
        ))}
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="p-3 border-t border-gray-100 flex items-center justify-between">
          <button
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            disabled={page === 0}
            className="px-3 py-1 text-xs bg-gray-100 rounded-lg disabled:opacity-40"
          >
            ← Prev
          </button>
          <span className="text-xs text-gray-500">
            Page {page + 1} / {totalPages}
          </span>
          <button
            onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
            disabled={page === totalPages - 1}
            className="px-3 py-1 text-xs bg-gray-100 rounded-lg disabled:opacity-40"
          >
            Next →
          </button>
        </div>
      )}
    </div>
  );
}

// Invisible series label list (for a11y label reference)
export { SERIES_LABELS };
