import React from "react";
import ReactDOM from "react-dom/client";
import {
  Pulse,
  PulseDataCollectionConsent,
  PulseLogLevel,
} from "@dreamhorizonorg/pulse-web";
import App from "./App";
import { PulseProvider } from "@dreamhorizonorg/pulse-web/react";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <PulseProvider
      config={{
        apiKey: import.meta.env.VITE_PULSE_API_KEY!,
        serviceName: "my-app",
        dataCollectionState: PulseDataCollectionConsent.ALLOWED,
        logLevel: PulseLogLevel.DEBUG,
        serviceVersion: "1.0.0",
        export: {
          format: "protobuf",
        },
      }}
      shutdownOnUnmount={false}
    >
      <App />
    </PulseProvider>
  </React.StrictMode>,
);
