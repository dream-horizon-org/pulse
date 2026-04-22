import React from "react";
import ReactDOM from "react-dom/client";
import App from "./App";
import { maybeLoadMockPulseSdkConfig } from "./maybeLoadMockPulseSdkConfig";

void maybeLoadMockPulseSdkConfig().then(() => {
  ReactDOM.createRoot(document.getElementById("root")!).render(
    <React.StrictMode>
      <App />
    </React.StrictMode>,
  );
});
