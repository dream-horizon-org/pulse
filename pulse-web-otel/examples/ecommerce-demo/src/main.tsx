import React from "react";
import ReactDOM from "react-dom/client";
import { PulseWeb, PulseDataCollectionConsent, PulseLogLevel } from "@dreamhorizon/pulse-web";
import App from "./App";
import { maybeLoadMockInteractionConfig } from "./maybeLoadMockInteractionConfig";
import { maybeLoadMockPulseSdkConfig } from "./maybeLoadMockPulseSdkConfig";

void Promise.all([
  maybeLoadMockPulseSdkConfig(),
  maybeLoadMockInteractionConfig(),
]).then(() => {
  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
});
