"use client";

import { useEffect } from "react";
import Link from "next/link";
import { Pulse } from "@dreamhorizonorg/pulse-web";

export default function LotteryError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    Pulse.reportDeviceCrash(error, {
      boundary: "lottery_detail_error_boundary",
      digest: error.digest ?? "",
    });
  }, [error]);

  return (
    <div className="flex flex-col items-center justify-center min-h-[60vh] text-center px-4">
      <div className="text-5xl mb-4">⚠️</div>
      <h2 className="text-xl font-bold text-sapphire mb-2">Lottery page error</h2>
      <p className="text-sm text-gray-500 mb-6">{error.message}</p>
      <div className="flex gap-3">
        <button
          onClick={reset}
          className="px-5 py-2.5 bg-sapphire text-white rounded-xl text-sm font-semibold"
        >
          Try again
        </button>
        <Link
          href="/"
          className="px-5 py-2.5 bg-gray-100 text-gray-700 rounded-xl text-sm font-semibold"
        >
          Go home
        </Link>
      </div>
    </div>
  );
}
