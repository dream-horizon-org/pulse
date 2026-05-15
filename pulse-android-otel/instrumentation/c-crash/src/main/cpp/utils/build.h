#pragma once

/**
 * Optional Clang analyzer hook for async-signal-safe regions.
 * Define \c CLANG_ANALYZE_ASYNCSAFE when using a toolchain/analyzer that understands
 * \c __attribute__((asyncsafe)) (no trailing semicolon in the macro — use site supplies `;`
 * on declarations).
 */
#ifdef CLANG_ANALYZE_ASYNCSAFE
#define __asyncsafe __attribute__((asyncsafe))
#else
#define __asyncsafe
#endif
