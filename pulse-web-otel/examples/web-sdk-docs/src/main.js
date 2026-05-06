import "./styles.css";
import { maybeLoadMockPulseSdkConfig } from "./maybeLoadMockPulseSdkConfig.js";
import { buildPulseConfig } from "./pulseConfig.js";
import { createRouter } from "./router.js";
import { routes, routeLabels } from "./pages.js";
import { PulseWeb } from "@dreamhorizon/pulse-web";

async function main() {
  await maybeLoadMockPulseSdkConfig();
  const config = buildPulseConfig();
  PulseWeb.start(config);
  globalThis.PulseWeb = PulseWeb;

  const outlet = document.getElementById("outlet");
  const navRoot = document.getElementById("nav");
  if (!outlet || !navRoot) {
    throw new Error("[web-sdk-docs] #outlet or #nav missing");
  }

  const router = createRouter({
    routes,
    outlet,
    navRoot,
    routeLabels,
  });
  router.start();
}

void main();
