"use client";

import { useOrders } from "../hooks/useOrders";
import type { Order } from "../types/lottery";

function StatusBadge({ status }: { status: Order["status"] }) {
  const map = {
    confirmed: { label: "Confirmed", cls: "bg-emerald-100 text-emerald-700" },
    draw_complete: { label: "Draw done", cls: "bg-gray-100 text-gray-600" },
    cancelled: { label: "Cancelled", cls: "bg-red-100 text-red-600" },
  };
  const { label, cls } = map[status];
  return (
    <span className={`text-[11px] font-semibold px-2 py-0.5 rounded-full ${cls}`}>
      {label}
    </span>
  );
}

function ResultBadge({ result }: { result?: Order["result"] }) {
  if (!result) return null;
  return result === "win" ? (
    <span className="text-xs font-bold text-amber-600">🏆 Winner!</span>
  ) : (
    <span className="text-xs text-gray-400">No win</span>
  );
}

export default function OrdersPage() {
  const { data, isLoading, isError, error } = useOrders();

  return (
    <div className="space-y-4">
      <h1 className="text-xl font-extrabold text-sapphire">My Orders</h1>

      {isLoading && (
        <div className="space-y-3">
          {[1, 2].map((i) => (
            <div key={i} className="h-28 bg-white rounded-2xl animate-pulse" />
          ))}
        </div>
      )}

      {isError && (
        <div className="bg-red-50 border border-red-200 rounded-2xl p-4 text-sm text-red-700">
          Failed to load orders: {(error as Error)?.message}
        </div>
      )}

      {!isLoading && !isError && (data?.items ?? []).length === 0 && (
        <div className="text-center py-16 text-gray-400">
          <div className="text-4xl mb-3">📋</div>
          <p>No orders yet. Buy a ticket to get started!</p>
        </div>
      )}

      {!isLoading &&
        !isError &&
        (data?.items ?? []).map((order) => (
          <div key={order.id} className="bg-white rounded-2xl shadow-card p-4">
            <div className="flex items-start justify-between mb-2">
              <div>
                <div className="font-bold text-sapphire text-sm">
                  {order.lotteryName}
                </div>
                <div className="text-xs text-gray-400 mt-0.5">{order.id}</div>
              </div>
              <StatusBadge status={order.status} />
            </div>

            <div className="flex flex-wrap gap-1.5 mb-3">
              {order.tickets.map((t) => (
                <span
                  key={t}
                  className="text-xs font-mono bg-gold/10 text-sapphire px-2 py-0.5 rounded-md"
                >
                  {t}
                </span>
              ))}
            </div>

            <div className="flex items-center justify-between text-xs text-gray-500">
              <span>
                Draw: {order.drawDate} at {order.drawTime}
              </span>
              <div className="flex items-center gap-2">
                <ResultBadge result={order.result} />
                <span className="font-semibold text-sapphire">
                  ₹{order.totalAmount.toLocaleString("en-IN")}
                </span>
              </div>
            </div>
          </div>
        ))}
    </div>
  );
}
