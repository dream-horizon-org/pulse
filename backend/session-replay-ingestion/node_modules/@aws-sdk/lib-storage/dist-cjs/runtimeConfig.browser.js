"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.runtimeConfig = void 0;
const runtimeConfig_shared_1 = require("./runtimeConfig.shared");
exports.runtimeConfig = {
    ...runtimeConfig_shared_1.runtimeConfigShared,
    runtime: "browser",
};
