"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
exports.runtimeConfig = void 0;
const runtimeConfig_browser_1 = require("./runtimeConfig.browser");
exports.runtimeConfig = {
    ...runtimeConfig_browser_1.runtimeConfig,
    runtime: "react-native",
};
