package org.dreamhorizon.pulseserver.errorgrouping

data class AndroidNdkLlvmConfig(
    val llvmSymbolizerPath: String = resolveSymbolizerPath(),
    val maxCaptureChars: Int = 2_000_000,
) {
    companion object {
        /** AGP / ndk-stack ABI directory names under {@code obj/<abi>}. */
        val ANDROID_ABI_SEGMENTS: Set<String> =
            setOf("arm64-v8a", "armeabi-v7a", "armeabi", "x86", "x86_64")

        /**
         * Maps Android ABI (from {@code pulse.native.binary_arch}) to llvm-symbolizer
         * {@code --default-arch}, matching ndk-stack's unstripped {@code obj/<abi>} layout.
         */
        @JvmStatic
        fun llvmDefaultArch(binaryArch: String?): String =
            when (binaryArch?.trim()?.lowercase()) {
                "arm64-v8a", "aarch64" -> "aarch64"
                "armeabi-v7a", "armeabi", "arm" -> "arm"
                "x86", "i386" -> "i386"
                "x86_64", "x64" -> "x86_64"
                else -> "aarch64"
            }

        /**
         * Parses ABI folder from a zip entry path, e.g. {@code arm64-v8a/libfoo.so}.
         * Same convention as [ndk-stack -sym](https://developer.android.com/ndk/guides/ndk-stack).
         */
        @JvmStatic
        fun abiFromZipEntry(entryName: String): String? {
            val normalized = entryName.replace('\\', '/').trim('/')
            if (normalized.isEmpty()) {
                return null
            }
            val segments = normalized.split('/')
            if (segments.size < 2) {
                return null
            }
            val parent = segments[segments.size - 2]
            return if (ANDROID_ABI_SEGMENTS.contains(parent)) parent else null
        }

        private fun firstEnv(key: String, default: String? = null): String? {
            val value = System.getenv(key)?.trim()
            return if (value.isNullOrBlank()) default else value
        }

        private fun resolveSymbolizerPath(): String =
            firstEnv("ANDROID_NDK_SYMBOLIZER_PATH")
                ?: error("ANDROID_NDK_SYMBOLIZER_PATH not set")
    }
}
