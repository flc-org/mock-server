import "/js/components/material-primitives.js";

class MockConfigPanel extends HTMLElement {
  #root;
  #tableBody;
  #dialog;
  #deleteDialog;
  #dialogTitle;
  #endpointDescInput;
  #hostKeyInput;
  #hashFnInput;
  #deleteMessage;
  #status;
  #editingProxySalt = null;
  #deleteProxySalt = null;
  #statusTimerId = null;

  constructor() {
    super();
    this.#root = this.attachShadow({ mode: "open" });
  }

  connectedCallback() {
    if (this.#root.childElementCount > 0) {
      return;
    }
    this.#render();
    this.#bindEvents();
    this.#loadConfigs();
  }

  disconnectedCallback() {
    if (this.#statusTimerId !== null) {
      clearTimeout(this.#statusTimerId);
    }
  }

  get #apiBase() {
    const value = this.getAttribute("api-base") ?? "";
    return value.endsWith("/") ? value.slice(0, -1) : value;
  }

  #url(path) {
    return `${this.#apiBase}${path}`;
  }

  #render() {
    this.#root.innerHTML = `
      <style>
        :host {
          display: block;
          color: #1b1b1f;
          font-family: Roboto, "Segoe UI", Tahoma, sans-serif;
        }
        .header {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 10px;
          margin-bottom: 12px;
        }
        h2 {
          margin: 0;
          font-size: 1.2rem;
        }
        .subtitle {
          margin: 2px 0 0;
          color: #5d6473;
          font-size: 0.92rem;
        }
        table {
          width: 100%;
          border-collapse: collapse;
          font-size: 0.9rem;
        }
        th,
        td {
          border-bottom: 1px solid #e2e6ee;
          text-align: left;
          padding: 9px 8px;
          vertical-align: middle;
        }
        th {
          color: #4f5768;
          font-weight: 600;
        }
        .actions {
          display: flex;
          gap: 8px;
          align-items: center;
          justify-content: flex-end;
        }
        .empty {
          color: #5d6473;
          text-align: center;
          padding: 16px 0;
        }
        .dialog-grid {
          display: grid;
          gap: 10px;
          grid-template-columns: 1fr 1fr;
          min-width: 560px;
        }
        .full {
          grid-column: 1 / -1;
        }
        .status {
          margin-top: 10px;
          min-height: 18px;
          font-size: 0.85rem;
        }
        .status.success {
          color: #12652f;
        }
        .status.error {
          color: #b3261e;
        }
        @media (max-width: 900px) {
          .dialog-grid {
            min-width: 0;
            grid-template-columns: 1fr;
          }
        }
      </style>
      <section>
        <div class="header">
          <div>
            <h2>Mock Configs</h2>
            <p class="subtitle">Create, edit, and delete mock configurations.</p>
          </div>
          <md-filled-button id="add-btn" type="button">
            <md-icon slot="icon">add</md-icon>
            Add
          </md-filled-button>
        </div>

        <table>
          <thead>
            <tr>
              <th>Proxy Salt</th>
              <th>Endpoint Desc</th>
              <th>Host Key</th>
              <th>Hash Function</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody id="mock-config-table"></tbody>
        </table>

        <div class="status" id="status"></div>
      </section>

      <md-dialog id="mock-dialog">
        <div slot="headline" id="dialog-title">Add Mock Config</div>
        <div slot="content">
          <div class="dialog-grid">
            <md-filled-text-field id="endpoint-desc" label="Endpoint Desc"></md-filled-text-field>
            <md-filled-text-field id="host-key" label="Host Key"></md-filled-text-field>
            <md-filled-text-field id="hash-fn" class="full" label="Hash Generation Function"></md-filled-text-field>
          </div>
        </div>
        <div slot="actions">
          <md-text-button id="cancel-btn" type="button">
            <md-icon slot="icon">close</md-icon>
            Cancel
          </md-text-button>
          <md-filled-button id="save-btn" type="button">
            <md-icon slot="icon">save</md-icon>
            Save
          </md-filled-button>
        </div>
      </md-dialog>

      <md-dialog id="delete-dialog">
        <div slot="headline">Confirm Delete</div>
        <div slot="content" id="delete-message"></div>
        <div slot="actions">
          <md-text-button id="cancel-delete-btn" type="button">
            <md-icon slot="icon">close</md-icon>
            Cancel
          </md-text-button>
          <md-filled-button id="confirm-delete-btn" type="button">
            <md-icon slot="icon">delete</md-icon>
            Delete
          </md-filled-button>
        </div>
      </md-dialog>
    `;

    this.#tableBody = this.#root.querySelector("#mock-config-table");
    this.#dialog = this.#root.querySelector("#mock-dialog");
    this.#deleteDialog = this.#root.querySelector("#delete-dialog");
    this.#dialogTitle = this.#root.querySelector("#dialog-title");
    this.#endpointDescInput = this.#root.querySelector("#endpoint-desc");
    this.#hostKeyInput = this.#root.querySelector("#host-key");
    this.#hashFnInput = this.#root.querySelector("#hash-fn");
    this.#deleteMessage = this.#root.querySelector("#delete-message");
    this.#status = this.#root.querySelector("#status");
  }

  #bindEvents() {
    this.#root.querySelector("#add-btn")?.addEventListener("click", () => {
      this.#openAddDialog();
    });
    this.#root.querySelector("#cancel-btn")?.addEventListener("click", () => {
      this.#dialog.open = false;
    });
    this.#root.querySelector("#save-btn")?.addEventListener("click", () => {
      this.#saveConfig();
    });
    this.#root
      .querySelector("#cancel-delete-btn")
      ?.addEventListener("click", () => {
        this.#deleteDialog.open = false;
      });
    this.#root
      .querySelector("#confirm-delete-btn")
      ?.addEventListener("click", () => {
        this.#confirmDelete();
      });
  }

  #setStatus(message, kind = "success") {
    this.#status.textContent = message;
    this.#status.classList.remove("success", "error");
    this.#status.classList.add(kind);
    if (this.#statusTimerId !== null) {
      clearTimeout(this.#statusTimerId);
    }
    this.#statusTimerId = window.setTimeout(() => {
      this.#status.textContent = "";
      this.#status.classList.remove("success", "error");
    }, 2600);
  }

  #openAddDialog() {
    this.#editingProxySalt = null;
    this.#dialogTitle.textContent = "Add Mock Config";
    this.#endpointDescInput.value = "";
    this.#hostKeyInput.value = "";
    this.#hashFnInput.value = "";
    this.#dialog.open = true;
  }

  #openEditDialog(config) {
    this.#editingProxySalt = config.proxySalt;
    this.#dialogTitle.textContent = `Edit ${config.proxySalt}`;
    this.#endpointDescInput.value = config.endpointDesc ?? "";
    this.#hostKeyInput.value = config.hostKey ?? "";
    this.#hashFnInput.value = config.hashGenerationFunction ?? "";
    this.#dialog.open = true;
  }

  #openDeleteDialog(proxySalt) {
    this.#deleteProxySalt = proxySalt;
    this.#deleteMessage.textContent = `Delete mock config ${proxySalt}?`;
    this.#deleteDialog.open = true;
  }

  async #saveConfig() {
    const payload = {
      endpointDesc: this.#endpointDescInput.value.trim(),
      hostKey: this.#hostKeyInput.value.trim(),
      hashGenerationFunction: this.#hashFnInput.value.trim(),
    };
    const isEdit = this.#editingProxySalt !== null;
    const method = isEdit ? "PUT" : "POST";
    const url = isEdit
      ? this.#url(
          `/mock-server-configs/${encodeURIComponent(this.#editingProxySalt)}`
        )
      : this.#url("/mock-server-configs");

    try {
      const response = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload),
      });
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Save failed: ${errorText || response.status}`, "error");
        return;
      }
      this.#dialog.open = false;
      this.#setStatus("Saved", "success");
      await this.#loadConfigs();
    } catch (error) {
      this.#setStatus(`Save failed: ${error.message}`, "error");
    }
  }

  async #confirmDelete() {
    if (!this.#deleteProxySalt) {
      return;
    }
    const proxySalt = this.#deleteProxySalt;
    try {
      const response = await fetch(
        this.#url(`/mock-server-configs/${encodeURIComponent(proxySalt)}`),
        { method: "DELETE" }
      );
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Delete failed: ${errorText || response.status}`, "error");
        return;
      }
      this.#deleteDialog.open = false;
      this.#setStatus("Deleted", "success");
      await this.#loadConfigs();
    } catch (error) {
      this.#setStatus(`Delete failed: ${error.message}`, "error");
    }
  }

  #createActionButton(iconName, ariaLabel, onClick) {
    const button = document.createElement("md-outlined-icon-button");
    button.type = "button";
    button.setAttribute("aria-label", ariaLabel);
    button.title = ariaLabel;

    const icon = document.createElement("md-icon");
    icon.textContent = iconName;
    button.appendChild(icon);
    button.addEventListener("click", onClick);
    return button;
  }

  #renderConfigs(configs) {
    this.#tableBody.innerHTML = "";
    if (!configs || configs.length === 0) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 5;
      cell.className = "empty";
      cell.textContent = "No mock configs found.";
      row.appendChild(cell);
      this.#tableBody.appendChild(row);
      return;
    }

    for (const config of configs) {
      const row = document.createElement("tr");
      row.appendChild(this.#createTextCell(config.proxySalt ?? ""));
      row.appendChild(this.#createTextCell(config.endpointDesc ?? ""));
      row.appendChild(this.#createTextCell(config.hostKey ?? ""));
      row.appendChild(this.#createTextCell(config.hashGenerationFunction ?? ""));

      const actionsCell = document.createElement("td");
      const actions = document.createElement("div");
      actions.className = "actions";

      const editButton = this.#createActionButton("edit", "Edit", () => {
        this.#openEditDialog(config);
      });
      const deleteButton = this.#createActionButton("delete", "Delete", () => {
        this.#openDeleteDialog(config.proxySalt);
      });

      actions.appendChild(editButton);
      actions.appendChild(deleteButton);
      actionsCell.appendChild(actions);
      row.appendChild(actionsCell);

      this.#tableBody.appendChild(row);
    }
  }

  #createTextCell(text) {
    const cell = document.createElement("td");
    cell.textContent = text;
    return cell;
  }

  async #loadConfigs() {
    try {
      const response = await fetch(this.#url("/mock-server-configs"));
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Load failed: ${errorText || response.status}`, "error");
        return;
      }
      const data = await response.json();
      this.#renderConfigs(data);
    } catch (error) {
      this.#setStatus(`Load failed: ${error.message}`, "error");
    }
  }
}

if (!customElements.get("mock-config-panel")) {
  customElements.define("mock-config-panel", MockConfigPanel);
}
