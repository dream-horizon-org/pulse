"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.runtimeConfig = void 0;
const node_fs_1 = require("node:fs");
const runtimeConfig_shared_1 = require("./runtimeConfig.shared");
exports.runtimeConfig = {
    ...runtimeConfig_shared_1.runtimeConfigShared,
    runtime: "node",
    lstatSync: node_fs_1.lstatSync,
    isFileReadStream(f) {
        return f instanceof node_fs_1.ReadStream;
    },
};
