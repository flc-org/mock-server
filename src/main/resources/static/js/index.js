import "/js/components/material-primitives.js";

class MockServerUiApp {
  static DEFAULT_VIEW = "mock-configs";

  static VIEWS = {
    "mock-configs": {
      icon: "tune",
      label: "Mock Configs",
      tag: "mock-config-panel",
      modulePath: "/js/components/mock-config-panel.js",
    },
    "host-mappings": {
      icon: "dns",
      label: "Host Mappings",
      tag: "host-mapping-panel",
      modulePath: "/js/components/host-mapping-panel.js",
    },
    "capture-audits": {
      icon: "fact_check",
      label: "Capture Audits",
      tag: "capture-audit-panel",
      modulePath: "/js/components/capture-audit-panel.js",
    },
    "recorded-responses": {
      icon: "table_view",
      label: "Recorded Responses",
      tag: "recorded-response-panel",
      modulePath: "/js/components/recorded-response-panel.js",
    },
  };

  #navRoot;
  #viewRoot;
  #loadedViews = new Set();
  #activeView = MockServerUiApp.DEFAULT_VIEW;

  constructor(navRoot, viewRoot) {
    this.#navRoot = navRoot;
    this.#viewRoot = viewRoot;
  }

  init() {
    window.addEventListener("popstate", () => {
      this.#openView(this.#resolveViewFromLocation(), false);
    });
    this.#openView(this.#resolveViewFromLocation(), false);
  }

  #resolveViewFromLocation() {
    const params = new URLSearchParams(window.location.search);
    const view = params.get("view");
    if (!view || !Object.hasOwn(MockServerUiApp.VIEWS, view)) {
      return MockServerUiApp.DEFAULT_VIEW;
    }
    return view;
  }

  #setLocation(view, pushState) {
    const params = new URLSearchParams(window.location.search);
    params.set("view", view);
    const query = params.toString();
    const nextUrl = `${window.location.pathname}?${query}`;
    if (pushState) {
      history.pushState({ view }, "", nextUrl);
    } else {
      history.replaceState({ view }, "", nextUrl);
    }
  }

  #renderNav() {
    this.#navRoot.innerHTML = "";
    for (const [viewKey, view] of Object.entries(MockServerUiApp.VIEWS)) {
      const elementName =
        this.#activeView === viewKey ? "md-filled-button" : "md-outlined-button";
      const button = document.createElement(elementName);
      button.type = "button";

      const icon = document.createElement("md-icon");
      icon.slot = "icon";
      icon.textContent = view.icon;
      button.appendChild(icon);
      button.appendChild(document.createTextNode(view.label));

      button.addEventListener("click", () => {
        this.#openView(viewKey, true);
      });
      this.#navRoot.appendChild(button);
    }
  }

  async #ensureViewModule(viewKey) {
    if (this.#loadedViews.has(viewKey)) {
      return;
    }
    const view = MockServerUiApp.VIEWS[viewKey];
    await import(view.modulePath);
    this.#loadedViews.add(viewKey);
  }

  #mountView(viewKey) {
    const view = MockServerUiApp.VIEWS[viewKey];
    const panel = document.createElement(view.tag);
    this.#viewRoot.replaceChildren(panel);
  }

  async #openView(viewKey, pushState) {
    const resolvedView = Object.hasOwn(MockServerUiApp.VIEWS, viewKey)
      ? viewKey
      : MockServerUiApp.DEFAULT_VIEW;
    try {
      await this.#ensureViewModule(resolvedView);
      this.#activeView = resolvedView;
      this.#renderNav();
      this.#mountView(resolvedView);
      this.#setLocation(resolvedView, pushState);
    } catch (error) {
      this.#viewRoot.innerHTML = "";
      const message = document.createElement("p");
      message.className = "inline-message";
      message.textContent = `Failed to load view: ${error.message}`;
      this.#viewRoot.appendChild(message);
    }
  }
}

const navRoot = document.querySelector("#app-nav");
const viewRoot = document.querySelector("#app-view");
if (navRoot && viewRoot) {
  new MockServerUiApp(navRoot, viewRoot).init();
}
