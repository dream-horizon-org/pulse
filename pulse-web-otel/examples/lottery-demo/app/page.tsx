"use client";

import { useEffect, useState } from "react";
import { Pulse } from "@dreamhorizon/pulse-web";
import { BannerCarousel } from "./components/BannerCarousel";
import { LotteryCard } from "./components/LotteryCard";
import { useUser } from "./context/UserContext";
import { useLotteries } from "./hooks/useLotteries";

export default function Home() {
  const { user } = useUser();
  // Scenario selector lets testers trigger server_error / slow from the UI
  const [scenario, setScenario] = useState<string | undefined>(undefined);
  const { data, isLoading, isError, error, refetch } = useLotteries(scenario);

  useEffect(() => {
    Pulse.trackEvent("home_screen_loaded", {
      user_id: user?.userId ?? "guest",
      is_new_user: !user,
    });
  }, [user]);

  return (
    <div className="space-y-4">
      <BannerCarousel />

      {/* Dev scenario controls */}
      <div className="flex gap-2 flex-wrap px-0.5">
        <span className="text-xs text-gray-400 self-center">Simulate:</span>
        {[
          { label: "Normal", value: undefined },
          { label: "Server error (500)", value: "server_error" },
          { label: "Slow (poor LCP)", value: "slow" },
        ].map((s) => (
          <button
            key={s.label}
            onClick={() => { setScenario(s.value); setTimeout(() => refetch(), 50); }}
            className={`text-xs px-2.5 py-1 rounded-full font-medium transition-colors ${
              scenario === s.value
                ? "bg-sapphire text-white"
                : "bg-white text-gray-600 border border-gray-200"
            }`}
          >
            {s.label}
          </button>
        ))}
      </div>

      <section>
        <h2 className="text-lg font-extrabold text-sapphire mb-3">
          Upcoming Draws
        </h2>

        {isLoading && (
          <div className="space-y-3">
            {[1, 2, 3].map((i) => (
              <div
                key={i}
                className="bg-white rounded-2xl h-36 animate-pulse"
              />
            ))}
          </div>
        )}

        {isError && (
          <div className="bg-red-50 border border-red-200 rounded-2xl p-4 text-center">
            <div className="text-2xl mb-2">⚠️</div>
            <p className="text-sm text-red-700 font-medium mb-1">
              Failed to load lotteries
            </p>
            <p className="text-xs text-red-500 mb-3">
              {(error as Error)?.message}
            </p>
            <button
              onClick={() => refetch()}
              className="text-xs px-4 py-1.5 bg-red-600 text-white rounded-lg"
            >
              Retry
            </button>
          </div>
        )}

        {!isLoading && !isError && (
          <div className="space-y-3">
            {(data?.items ?? []).map((lottery) => (
              <LotteryCard key={lottery.id} lottery={lottery} />
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
