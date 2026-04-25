import { lstatSync, ReadStream } from "node:fs";
import { runtimeConfigShared as shared } from "./runtimeConfig.shared";
export const runtimeConfig = {
    ...shared,
    runtime: "node",
    lstatSync,
    isFileReadStream(f) {
        return f instanceof ReadStream;
    },
};
