"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { PulseWeb } from "@dreamhorizon/pulse-web";
import { api, ApiError } from "../lib/api";
import { useUser } from "../context/UserContext";
import type { MockUser } from "../context/UserContext";

type Step = "mobile" | "otp";

interface MobileForm { mobile: string }
interface OtpForm { otp: string }

// Simulate which error scenario to hit — visible selector for testers
const OTP_SCENARIOS = [
  { label: "Valid OTP (1234)", value: "ok" },
  { label: "Wrong OTP", value: "wrong_otp" },
  { label: "Expired OTP", value: "expired" },
  { label: "Rate limited (send)", value: "rate_limited" },
];

export default function LoginPage() {
  const [step, setStep] = useState<Step>("mobile");
  const [requestId, setRequestId] = useState("");
  const [mobile, setMobile] = useState("");
  const [scenario, setScenario] = useState("ok");
  const [apiError, setApiError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const { setUser } = useUser();
  const router = useRouter();

  const mobileForm = useForm<MobileForm>();
  const otpForm = useForm<OtpForm>();

  async function sendOtp(data: MobileForm) {
    setLoading(true);
    setApiError(null);
    const sendScenario = scenario === "rate_limited" ? "rate_limited" : undefined;
    const url = sendScenario
      ? `/api/otp/send?scenario=${sendScenario}`
      : "/api/otp/send";

    try {
      const res = await api.post<{ requestId: string; maskedMobile: string }>(
        url,
        { mobile: data.mobile },
      );
      setRequestId(res.requestId);
      setMobile(data.mobile);
      PulseWeb.trackEvent("otp_sent", { masked_mobile: res.maskedMobile });
      setStep("otp");
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Failed to send OTP";
      setApiError(msg);
      PulseWeb.trackNonFatal("otp_send_failed", { reason: msg });
    } finally {
      setLoading(false);
    }
  }

  async function verifyOtp(data: OtpForm) {
    setLoading(true);
    setApiError(null);
    const verifyScenario =
      scenario === "wrong_otp" || scenario === "expired" ? scenario : undefined;
    const url = verifyScenario
      ? `/api/otp/verify?scenario=${verifyScenario}`
      : "/api/otp/verify";

    try {
      const res = await api.post<{ accessToken: string; user: MockUser }>(url, {
        requestId,
        mobile,
        otp: data.otp,
      });

      PulseWeb.trackEvent("otp_verified", { is_new_user: res.user.onBoarding });
      setUser(res.user);
      router.push("/");
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Verification failed";
      setApiError(msg);
      PulseWeb.trackNonFatal("otp_verify_failed", {
        reason: msg,
        scenario: verifyScenario ?? "normal",
      });
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="max-w-sm mx-auto pt-8">
      <div className="text-center mb-8">
        <div className="text-5xl mb-3">🎟</div>
        <h1 className="text-2xl font-extrabold text-sapphire">Welcome to DreamLotto</h1>
        <p className="text-sm text-gray-500 mt-1">Login to buy tickets and track your draws</p>
      </div>

      {/* Scenario selector */}
      <div className="mb-5 bg-white rounded-xl p-3 shadow-card">
        <div className="text-[10px] text-gray-400 uppercase tracking-wider mb-2">
          Simulate auth scenario
        </div>
        <div className="flex flex-wrap gap-1.5">
          {OTP_SCENARIOS.map((s) => (
            <button
              key={s.value}
              onClick={() => setScenario(s.value)}
              className={`text-[11px] px-2.5 py-1 rounded-full font-medium transition-colors ${
                scenario === s.value
                  ? "bg-sapphire text-white"
                  : "bg-gray-100 text-gray-600"
              }`}
            >
              {s.label}
            </button>
          ))}
        </div>
      </div>

      <div className="bg-white rounded-2xl shadow-card p-5">
        {step === "mobile" ? (
          <form onSubmit={mobileForm.handleSubmit(sendOtp)} className="space-y-4">
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Mobile number
              </label>
              <div className="flex">
                <span className="px-3 py-2.5 bg-gray-50 border border-r-0 border-gray-200 rounded-l-xl text-sm text-gray-500">
                  +91
                </span>
                <input
                  {...mobileForm.register("mobile", {
                    required: "Mobile required",
                    pattern: { value: /^\d{10}$/, message: "Enter 10-digit number" },
                  })}
                  type="tel"
                  placeholder="9876543210"
                  className="flex-1 px-3 py-2.5 border border-gray-200 rounded-r-xl text-sm outline-none focus:border-sapphire"
                />
              </div>
              {mobileForm.formState.errors.mobile && (
                <p className="text-xs text-red-500 mt-1">
                  {mobileForm.formState.errors.mobile.message}
                </p>
              )}
            </div>

            {apiError && (
              <div className="bg-red-50 text-red-700 text-xs p-2.5 rounded-lg">
                {apiError}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-sapphire text-white rounded-xl font-bold text-sm disabled:opacity-60"
            >
              {loading ? "Sending OTP…" : "Get OTP"}
            </button>
          </form>
        ) : (
          <form onSubmit={otpForm.handleSubmit(verifyOtp)} className="space-y-4">
            <div className="text-center text-sm text-gray-600 mb-2">
              OTP sent to +91 ******{mobile.slice(-4)}
            </div>
            <div>
              <label className="block text-sm font-semibold text-gray-700 mb-1.5">
                Enter OTP <span className="text-gray-400 font-normal">(use 1234 for success)</span>
              </label>
              <input
                {...otpForm.register("otp", { required: "OTP required" })}
                type="text"
                inputMode="numeric"
                maxLength={6}
                placeholder="1234"
                className="w-full px-4 py-3 border border-gray-200 rounded-xl text-center text-xl font-mono tracking-widest outline-none focus:border-sapphire"
              />
            </div>

            {apiError && (
              <div className="bg-red-50 text-red-700 text-xs p-2.5 rounded-lg">
                {apiError}
              </div>
            )}

            <button
              type="submit"
              disabled={loading}
              className="w-full py-3 bg-sapphire text-white rounded-xl font-bold text-sm disabled:opacity-60"
            >
              {loading ? "Verifying…" : "Verify OTP"}
            </button>
            <button
              type="button"
              onClick={() => { setStep("mobile"); setApiError(null); }}
              className="w-full py-2 text-sm text-gray-500"
            >
              Change number
            </button>
          </form>
        )}
      </div>
    </div>
  );
}
