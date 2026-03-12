declare module 'zstd-codec' {
    export const ZstdCodec: {
        run(callback: (zstd: ZstdModule) => void): void
    }

    interface ZstdModule {
        Simple: new () => ZstdSimple
        Streaming: new () => ZstdStreaming
    }

    interface ZstdSimple {
        compress(data: Uint8Array, level?: number): Uint8Array
        decompress(data: Uint8Array): Uint8Array
    }

    interface ZstdStreaming {
        compress(data: Uint8Array, level?: number): Uint8Array
        decompress(data: Uint8Array, sizeHint?: number): Uint8Array
    }
}
