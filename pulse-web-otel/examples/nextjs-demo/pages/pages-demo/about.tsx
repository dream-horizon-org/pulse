/**
 * /pages-demo/about — Pages Router about page.
 * screen.name = /pages-demo/about
 */
import React from "react";
import { PagesNavBar } from "../_pages-nav";

export default function PagesDemoAbout(): React.JSX.Element {
  return (
    <>
      <PagesNavBar />
      <main style={{ padding: "1rem" }}>
        <h1>About</h1>
        <p>
          Navigating here triggered <code>routeChangeComplete</code> →{" "}
          <code>PulseWeb.setScreenName("/pages-demo/about")</code>.
        </p>
      </main>
    </>
  );
}
