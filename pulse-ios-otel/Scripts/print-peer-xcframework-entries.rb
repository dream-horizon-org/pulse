#!/usr/bin/env ruby
# frozen_string_literal: true

# Prints one line per peer: "XcodeScheme:FrameworkBasename"
# (FrameworkBasename matches PRODUCT_MODULE_NAME / the .framework folder in the archive.)
#
# What it reads:
#   • Names + version strings: PulseKit.podspec (spec.dependency …)
#   • Resolved versions: Examples/PulseIOSExample/Podfile.lock (only for exact pins)
#   • Module name per pod: xcodebuild -showBuildSettings on the example workspace
#
# Prerequisites (run from a machine that can build iOS):
#   • macOS with Xcode installed (needs `xcodebuild` on PATH).
#   • Ruby with stdlib only (`open3` — no gems).
#   • Repo layout: PulseKit.podspec at root; Examples/PulseIOSExample/Podfile.lock
#     and PulseIOSExample.xcworkspace present after CocoaPods.
#   • Run once: (cd Examples/PulseIOSExample && pod install)
#
# Run locally (repository root):
#   cd /path/to/pulse-ios-sdk
#   (cd Examples/PulseIOSExample && pod install)
#   ruby Scripts/print-peer-xcframework-entries.rb
#
# Optional explicit root (default is Dir.pwd):
#   ruby Scripts/print-peer-xcframework-entries.rb /path/to/pulse-ios-sdk
#   REPO_ROOT=/path/to/pulse-ios-sdk ruby Scripts/print-peer-xcframework-entries.rb
#
# Expected stdout (example): one line per peer, same order as first occurrence in podspec.
# Exit 0 on success; exit 1 on missing files, version mismatch, or xcodebuild failure.
#
# If env PEER_XCFRAMEWORK_SUMMARY_FILE is set to a path, writes a short human snapshot there
# (for build-xcframework.sh to print after BUILD COMPLETE).

require "open3"

root = ARGV[0] || ENV.fetch("REPO_ROOT", Dir.pwd)
pulse_path = File.join(root, "PulseKit.podspec")
example_dir = File.join(root, "Examples", "PulseIOSExample")
workspace = "PulseIOSExample.xcworkspace"
lock_path = File.join(example_dir, "Podfile.lock")

unless File.file?(pulse_path)
  warn "error: PulseKit.podspec not found at #{pulse_path}"
  exit 1
end
unless File.file?(lock_path)
  warn "error: #{lock_path} missing — run pod install in Examples/PulseIOSExample"
  exit 1
end
unless File.directory?(File.join(example_dir, workspace))
  warn "error: #{example_dir}/#{workspace} missing — run pod install in Examples/PulseIOSExample"
  exit 1
end

# "  - OpenTelemetry-Swift-Api (2.2.0)"  → first line per root pod wins
def resolved_root_versions(lock_path)
  roots = {}
  File.foreach(lock_path) do |line|
    next unless line =~ /^\s{2}-\s+([^\s(]+)\s+\(([^)]+)\)/

    pod_path = ::Regexp.last_match(1)
    ver = ::Regexp.last_match(2)
    r = pod_path.split("/").first
    roots[r] ||= ver
  end
  roots
end

def normalize_requirement(req)
  return "" if req.nil?

  req.strip.sub(/\A=\s*/, "").strip
end

# Exact pin: single concrete version string (after stripping leading '='), no CocoaPods operators.
def exact_pin?(req)
  n = normalize_requirement(req)
  return false if n.empty?
  return false if n =~ /~>|>=|<=|!=/
  return false if n =~ /\A[><]/
  return false if n.include?(" ")

  true
end

pairs = File.read(pulse_path).scan(/spec\.dependency\s+['"]([^'"]+)['"]\s*(?:,\s*['"]([^'"]*)['"])?/)
if pairs.empty?
  exit 0
end

order = []
by_scheme = {}
pairs.each do |name, req|
  scheme = name.split("/").first
  next if by_scheme.key?(scheme)

  by_scheme[scheme] = [name, req]
  order << scheme
end

roots_resolved = resolved_root_versions(lock_path)

summary_rows = []

order.each do |scheme|
  _full_name, req = by_scheme[scheme]

  if exact_pin?(req)
    pin = normalize_requirement(req)
    lock_ver = roots_resolved[scheme]
    unless lock_ver
      warn "error: #{scheme} not listed in Podfile.lock — run pod install in Examples/PulseIOSExample"
      exit 1
    end
    if lock_ver != pin
      warn "error: version mismatch for #{scheme}: podspec pins #{pin.inspect} but Podfile.lock has #{lock_ver.inspect} — run pod install after editing PulseKit.podspec"
      exit 1
    end
  end

  out, status = nil
  Dir.chdir(example_dir) do
    out, status = Open3.capture2e(
      "xcodebuild",
      "-workspace", workspace,
      "-scheme", scheme,
      "-configuration", "Release",
      "-sdk", "iphoneos",
      "-showBuildSettings"
    )
  end

  unless status.success?
    warn "error: xcodebuild -showBuildSettings failed for scheme #{scheme.inspect}\n#{out.lines.first(5).join}"
    exit 1
  end

  mod = nil
  out.each_line do |line|
    next unless line =~ /^\s*PRODUCT_MODULE_NAME = (\S+)\s*$/

    mod = ::Regexp.last_match(1)
    break
  end

  if mod.nil? || mod.empty?
    warn "error: no PRODUCT_MODULE_NAME in build settings for scheme #{scheme.inspect}"
    exit 1
  end

  podspec_req =
    if req.nil? || req.strip.empty?
      "—"
    else
      normalize_requirement(req)
    end
  lock_disp = roots_resolved[scheme] || "—"
  pin_note = exact_pin?(req) ? "exact pin ✓" : "range / not checked vs lock"

  summary_rows << { scheme: scheme, mod: mod, podspec_req: podspec_req, lock_disp: lock_disp, pin_note: pin_note }

  puts "#{scheme}:#{mod}"
end

if order.empty? && !pairs.empty?
  warn "error: PulseKit.podspec lists dependencies but none resolved to schemes"
  exit 1
end

summary_path = ENV["PEER_XCFRAMEWORK_SUMMARY_FILE"].to_s.strip
if !summary_path.empty? && !summary_rows.empty?
  pulse_ver = File.read(pulse_path)[/spec\.version\s*=\s*["']([^"']+)["']/, 1] || "?"
  lines = []
  lines << "PulseKit podspec version: #{pulse_ver}"
  lines << ""
  lines << "Peers (CocoaPods scheme → podspec req → Podfile.lock → build/*.xcframework name):"
  summary_rows.each do |r|
    lines << format(
      "  %-26s  podspec %-10s  lock %-10s  → %s.xcframework  (%s)",
      r[:scheme],
      r[:podspec_req],
      r[:lock_disp],
      r[:mod],
      r[:pin_note]
    )
  end
  File.write(summary_path, "#{lines.join("\n")}\n")
end
