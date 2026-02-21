import "/js/components/material-primitives.js";

class HostMappingPanel extends HTMLElement {
  #root;
  #tableBody;
  #addDialog;
  #deleteDialog;
  #newHostKeyInput;
  #newHostNameInput;
  #deleteMessage;
  #status;
  #statusTimerId = null;
  #deleteHostKey = null;
  #rows = [];

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
    this.#loadRows();
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
        .host-name-input {
          width: min(540px, 100%);
        }
        .actions {
          display: flex;
          justify-content: flex-end;
          align-items: center;
          gap: 8px;
        }
        .empty {
          color: #5d6473;
          text-align: center;
          padding: 16px 0;
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
        .dialog-grid {
          display: grid;
          gap: 10px;
          min-width: 520px;
        }
        @media (max-width: 900px) {
          .dialog-grid {
            min-width: 0;
          }
        }
      </style>
      <section>
        <div class="header">
          <div>
            <h2>Host Mappings</h2>
            <p class="subtitle">Manage host key to host name mapping used by proxy calls.</p>
          </div>
          <md-filled-button id="add-btn" type="button">
            <md-icon slot="icon">add</md-icon>
            Add
          </md-filled-button>
        </div>

        <table>
          <thead>
            <tr>
              <th>Host Key</th>
              <th>Host Name</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody id="host-mapping-table"></tbody>
        </table>

        <div id="status" class="status"></div>
      </section>

      <md-dialog id="add-dialog">
        <div slot="headline">Add Host Mapping</div>
        <div slot="content">
          <div class="dialog-grid">
            <md-filled-text-field id="new-host-key" label="Host Key"></md-filled-text-field>
            <md-filled-text-field id="new-host-name" label="Host Name"></md-filled-text-field>
          </div>
        </div>
        <div slot="actions">
          <md-text-button id="cancel-add-btn" type="button">
            <md-icon slot="icon">close</md-icon>
            Cancel
          </md-text-button>
          <md-filled-button id="save-add-btn" type="button">
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

    this.#tableBody = this.#root.querySelector("#host-mapping-table");
    this.#addDialog = this.#root.querySelector("#add-dialog");
    this.#deleteDialog = this.#root.querySelector("#delete-dialog");
    this.#newHostKeyInput = this.#root.querySelector("#new-host-key");
    this.#newHostNameInput = this.#root.querySelector("#new-host-name");
    this.#deleteMessage = this.#root.querySelector("#delete-message");
    this.#status = this.#root.querySelector("#status");
  }

  #bindEvents() {
    this.#root.querySelector("#add-btn")?.addEventListener("click", () => {
      this.#newHostKeyInput.value = "";
      this.#newHostNameInput.value = "";
      this.#addDialog.open = true;
    });

    this.#root
      .querySelector("#cancel-add-btn")
      ?.addEventListener("click", () => {
        this.#addDialog.open = false;
      });

    this.#root.querySelector("#save-add-btn")?.addEventListener("click", () => {
      this.#createRow();
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

  #createIconButton(iconName, ariaLabel, onClick) {
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

  #normalize(value) {
    return (value ?? "").trim();
  }

  #syncSaveVisibility(input, saveButton, originalValue) {
    const currentValue = this.#normalize(input.value);
    saveButton.style.visibility =
      currentValue !== this.#normalize(originalValue) ? "visible" : "hidden";
  }

  #renderRows() {
    this.#tableBody.innerHTML = "";
    if (!this.#rows || this.#rows.length === 0) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 3;
      cell.className = "empty";
      cell.textContent = "No host mappings found.";
      row.appendChild(cell);
      this.#tableBody.appendChild(row);
      return;
    }

    for (const item of this.#rows) {
      const row = document.createElement("tr");

      const hostKeyCell = document.createElement("td");
      hostKeyCell.textContent = item.hostKey ?? "";

      const hostNameCell = document.createElement("td");
      const input = document.createElement("md-outlined-text-field");
      input.className = "host-name-input";
      input.value = item.hostName ?? "";
      input.label = "Host Name";
      input.setAttribute("aria-label", `Host name for ${item.hostKey}`);

      const actionsCell = document.createElement("td");
      const actions = document.createElement("div");
      actions.className = "actions";

      const saveButton = this.#createIconButton("save", "Save", () => {
        this.#updateRow(item.hostKey, input.value, saveButton, input);
      });
      saveButton.style.visibility = "hidden";

      const deleteButton = this.#createIconButton("delete", "Delete", () => {
        this.#openDeleteDialog(item.hostKey);
      });

      const originalHostName = item.hostName ?? "";
      input.addEventListener("input", () => {
        this.#syncSaveVisibility(input, saveButton, originalHostName);
      });

      input.addEventListener("keydown", event => {
        if (event.key === "Enter") {
          event.preventDefault();
          if (saveButton.style.visibility === "visible") {
            this.#updateRow(item.hostKey, input.value, saveButton, input);
          }
        }
      });

      hostNameCell.appendChild(input);
      actions.appendChild(saveButton);
      actions.appendChild(deleteButton);
      actionsCell.appendChild(actions);

      row.appendChild(hostKeyCell);
      row.appendChild(hostNameCell);
      row.appendChild(actionsCell);

      this.#tableBody.appendChild(row);
    }
  }

  async #createRow() {
    const hostKey = this.#normalize(this.#newHostKeyInput.value);
    const hostName = this.#normalize(this.#newHostNameInput.value);
    if (!hostKey || !hostName) {
      this.#setStatus("Host key and host name are required", "error");
      return;
    }

    try {
      const response = await fetch(this.#url("/host-mappings"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ hostKey, hostName }),
      });
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Create failed: ${errorText || response.status}`, "error");
        return;
      }
      this.#addDialog.open = false;
      this.#setStatus("Saved", "success");
      await this.#loadRows();
    } catch (error) {
      this.#setStatus(`Create failed: ${error.message}`, "error");
    }
  }

  async #updateRow(hostKey, hostNameRaw, saveButton, input) {
    const hostName = this.#normalize(hostNameRaw);
    if (!hostName) {
      this.#setStatus("Host name is required", "error");
      return;
    }

    try {
      const response = await fetch(
        this.#url(`/host-mappings/${encodeURIComponent(hostKey)}`),
        {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ hostKey, hostName }),
        }
      );
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Update failed: ${errorText || response.status}`, "error");
        return;
      }

      input.value = hostName;
      saveButton.style.visibility = "hidden";
      this.#setStatus(`Saved ${hostKey}`, "success");
      await this.#loadRows();
    } catch (error) {
      this.#setStatus(`Update failed: ${error.message}`, "error");
    }
  }

  #openDeleteDialog(hostKey) {
    this.#deleteHostKey = hostKey;
    this.#deleteMessage.textContent = `Delete host mapping ${hostKey}?`;
    this.#deleteDialog.open = true;
  }

  async #confirmDelete() {
    if (!this.#deleteHostKey) {
      return;
    }

    try {
      const response = await fetch(
        this.#url(`/host-mappings/${encodeURIComponent(this.#deleteHostKey)}`),
        { method: "DELETE" }
      );
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Delete failed: ${errorText || response.status}`, "error");
        return;
      }

      this.#deleteDialog.open = false;
      this.#setStatus("Deleted", "success");
      await this.#loadRows();
    } catch (error) {
      this.#setStatus(`Delete failed: ${error.message}`, "error");
    }
  }

  async #loadRows() {
    try {
      const response = await fetch(this.#url("/host-mappings"));
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Load failed: ${errorText || response.status}`, "error");
        return;
      }
      this.#rows = await response.json();
      this.#renderRows();
    } catch (error) {
      this.#setStatus(`Load failed: ${error.message}`, "error");
    }
  }
}

if (!customElements.get("host-mapping-panel")) {
  customElements.define("host-mapping-panel", HostMappingPanel);
}
