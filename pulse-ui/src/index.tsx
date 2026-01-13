import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import App from "./App";
import reportWebVitals from "./reportWebVitals";
import { GoogleOAuthProvider } from "@react-oauth/google";
// core styles are required for all packages
import "@mantine/core/styles.css";
import "@mantine/notifications/styles.css";
import "mantine-datatable/styles.css";

// other css files are required only if
// you are using components from the corresponding package
// import '@mantine/dates/styles.css';
// import '@mantine/dropzone/styles.css';
// import '@mantine/code-highlight/styles.css';
// ...

// Suppress ResizeObserver loop error - this is a known harmless error
// that occurs with dynamic layouts and is safe to ignore
// https://github.com/WICG/resize-observer/issues/38

// Method 1: Patch ResizeObserver to use requestAnimationFrame
const OriginalResizeObserver = window.ResizeObserver;
window.ResizeObserver = class ResizeObserver extends OriginalResizeObserver {
  constructor(callback: ResizeObserverCallback) {
    super((entries, observer) => {
      window.requestAnimationFrame(() => {
        try {
          callback(entries, observer);
        } catch {
          // Ignore callback errors
        }
      });
    });
  }
};

// Method 2: Suppress the error via event listener
window.addEventListener("error", (event) => {
  if (event.message?.includes?.("ResizeObserver") || 
      event.message?.includes?.("ResizeObserver loop")) {
    event.stopImmediatePropagation();
    event.preventDefault();
    return true;
  }
});

// Method 3: Suppress via unhandledrejection
window.addEventListener("unhandledrejection", (event) => {
  if (event.reason?.message?.includes?.("ResizeObserver")) {
    event.preventDefault();
  }
});

const root = ReactDOM.createRoot(
  document.getElementById("root") as HTMLElement,
);

const GOOGLE_CLIENT_ID = process.env.REACT_APP_GOOGLE_CLIENT_ID ?? "";

root.render(
  <React.StrictMode>
    <GoogleOAuthProvider clientId={GOOGLE_CLIENT_ID}>
      <App />
    </GoogleOAuthProvider>
  </React.StrictMode>,
);

// If you want to start measuring performance in your app, pass a function
// to log results (for example: reportWebVitals(console.log))
// or send to an analytics endpoint. Learn more: https://bit.ly/CRA-vitals
reportWebVitals();
