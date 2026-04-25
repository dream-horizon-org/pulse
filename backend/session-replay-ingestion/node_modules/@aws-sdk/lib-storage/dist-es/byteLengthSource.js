import { runtimeConfig } from "./runtimeConfig";
export var BYTE_LENGTH_SOURCE;
(function (BYTE_LENGTH_SOURCE) {
    BYTE_LENGTH_SOURCE["EMPTY_INPUT"] = "a null or undefined Body";
    BYTE_LENGTH_SOURCE["CONTENT_LENGTH"] = "the ContentLength property of the params set by the caller";
    BYTE_LENGTH_SOURCE["STRING_LENGTH"] = "the encoded byte length of the Body string";
    BYTE_LENGTH_SOURCE["TYPED_ARRAY"] = "the byteLength of a typed byte array such as Uint8Array";
    BYTE_LENGTH_SOURCE["LENGTH"] = "the value of Body.length";
    BYTE_LENGTH_SOURCE["SIZE"] = "the value of Body.size";
    BYTE_LENGTH_SOURCE["START_END_DIFF"] = "the numeric difference between Body.start and Body.end";
    BYTE_LENGTH_SOURCE["LSTAT"] = "the size of the file given by Body.path on disk as reported by lstatSync";
})(BYTE_LENGTH_SOURCE || (BYTE_LENGTH_SOURCE = {}));
export const byteLengthSource = (input, override) => {
    if (override != null) {
        return BYTE_LENGTH_SOURCE.CONTENT_LENGTH;
    }
    if (input == null) {
        return BYTE_LENGTH_SOURCE.EMPTY_INPUT;
    }
    if (typeof input === "string") {
        return BYTE_LENGTH_SOURCE.STRING_LENGTH;
    }
    if (typeof input.byteLength === "number") {
        return BYTE_LENGTH_SOURCE.TYPED_ARRAY;
    }
    else if (typeof input.length === "number") {
        return BYTE_LENGTH_SOURCE.LENGTH;
    }
    else if (typeof input.size === "number") {
        return BYTE_LENGTH_SOURCE.SIZE;
    }
    else if (typeof input.start === "number" && typeof input.end === "number") {
        return BYTE_LENGTH_SOURCE.START_END_DIFF;
    }
    else if (runtimeConfig.isFileReadStream(input)) {
        try {
            runtimeConfig.lstatSync(input.path).size;
            return BYTE_LENGTH_SOURCE.LSTAT;
        }
        catch (error) {
            return undefined;
        }
    }
    return undefined;
};
