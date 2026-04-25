"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.toStream = toStream;
const node_stream_1 = require("node:stream");
function toStream(bytes) {
    return node_stream_1.Readable.from(Buffer.from(bytes));
}
