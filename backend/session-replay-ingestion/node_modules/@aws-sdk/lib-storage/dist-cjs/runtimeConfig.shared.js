"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.runtimeConfigShared = void 0;
exports.runtimeConfigShared = {
    lstatSync: () => { },
    isFileReadStream(f) {
        return false;
    },
};
