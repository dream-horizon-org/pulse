import { ReadStream } from "node:fs";
/**
 * @internal
 */
export declare const runtimeConfig: {
    runtime: string;
    lstatSync: import("fs").StatSyncFn;
    isFileReadStream(f: unknown): f is ReadStream;
};
