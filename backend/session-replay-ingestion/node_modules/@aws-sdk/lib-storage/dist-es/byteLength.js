import { Buffer } from "buffer";
import { runtimeConfig } from "./runtimeConfig";
export const byteLength = (input) => {
    if (input == null) {
        return 0;
    }
    if (typeof input === "string") {
        return Buffer.byteLength(input);
    }
    if (typeof input.byteLength === "number") {
        return input.byteLength;
    }
    else if (typeof input.length === "number") {
        return input.length;
    }
    else if (typeof input.size === "number") {
        return input.size;
    }
    else if (typeof input.start === "number" && typeof input.end === "number") {
        return input.end + 1 - input.start;
    }
    else if (runtimeConfig.isFileReadStream(input)) {
        try {
            return runtimeConfig.lstatSync(input.path).size;
        }
        catch (error) {
            return undefined;
        }
    }
    return undefined;
};
