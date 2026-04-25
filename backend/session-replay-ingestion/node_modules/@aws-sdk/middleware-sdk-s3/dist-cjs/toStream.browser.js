"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.toStream = toStream;
function toStream(bytes) {
    return new ReadableStream({
        start(controller) {
            controller.enqueue(bytes);
            controller.close();
        },
    });
}
