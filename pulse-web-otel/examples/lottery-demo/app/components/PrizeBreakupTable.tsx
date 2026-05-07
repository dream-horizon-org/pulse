import type { PrizeBreakupItem } from "../types/lottery";

export function PrizeBreakupTable({ items }: { items: PrizeBreakupItem[] }) {
  return (
    <div className="bg-white rounded-xl overflow-hidden shadow-card">
      <div className="px-4 py-3 bg-sapphire">
        <h3 className="text-sm font-bold text-white">Prize Breakup</h3>
      </div>
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-gray-100">
            <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500">
              Rank
            </th>
            <th className="px-4 py-2 text-left text-xs font-semibold text-gray-500">
              Prize
            </th>
            <th className="px-4 py-2 text-right text-xs font-semibold text-gray-500">
              Winners
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((item, i) => (
            <tr
              key={i}
              className={`border-b border-gray-50 ${i === 0 ? "bg-gold/5" : ""}`}
            >
              <td className="px-4 py-2.5 font-semibold text-sapphire">
                {item.rank}
              </td>
              <td className="px-4 py-2.5 font-bold text-emerald-600">
                {item.amount}
              </td>
              <td className="px-4 py-2.5 text-right text-gray-600">
                {item.winners ?? "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
