"use client";

import { useState, useEffect } from "react";

const BANNERS = [
  {
    id: 1,
    title: "Diwali Bumper 2026",
    subtitle: "Win ₹10 Crores! Draw on May 10",
    bg: "from-amber-500 to-orange-600",
    emoji: "🪔",
  },
  {
    id: 2,
    title: "Navratri Special",
    subtitle: "₹5 Crores Prize Pool — Limited tickets left!",
    bg: "from-pink-500 to-rose-600",
    emoji: "🎉",
  },
  {
    id: 3,
    title: "New! Holi Jackpot",
    subtitle: "₹50 ticket — ₹2 Crores at stake",
    bg: "from-violet-500 to-purple-700",
    emoji: "🌈",
  },
];

export function BannerCarousel() {
  const [idx, setIdx] = useState(0);

  // Auto-advance — also causes layout shift if banner height isn't reserved
  useEffect(() => {
    const t = setInterval(() => setIdx((i) => (i + 1) % BANNERS.length), 3500);
    return () => clearInterval(t);
  }, []);

  const banner = BANNERS[idx];

  return (
    <div className="relative overflow-hidden rounded-2xl mx-4 mt-4 h-32">
      <div
        className={`absolute inset-0 bg-gradient-to-r ${banner.bg} transition-all duration-500`}
      />
      <div className="relative h-full flex items-center px-5 gap-4">
        <span className="text-5xl">{banner.emoji}</span>
        <div>
          <div className="text-white font-extrabold text-lg leading-tight">
            {banner.title}
          </div>
          <div className="text-white/80 text-sm mt-0.5">{banner.subtitle}</div>
        </div>
      </div>
      {/* Dots */}
      <div className="absolute bottom-2 right-3 flex gap-1">
        {BANNERS.map((_, i) => (
          <button
            key={i}
            onClick={() => setIdx(i)}
            className={`w-1.5 h-1.5 rounded-full transition-colors ${
              i === idx ? "bg-white" : "bg-white/40"
            }`}
          />
        ))}
      </div>
    </div>
  );
}
