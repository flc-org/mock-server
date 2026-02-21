import "/js/components/material-primitives.js";

class RecordedResponsePanel extends HTMLElement {
  #root;
  #tableBody;
  #proxySaltInput;
  #urlInput;
  #requestBodyInput;
  #headersInput;
  #pageSizeInput;
  #deleteButton;
  #deleteButtonLabel;
  #selectAllCheckbox;
  #prevButton;
  #nextButton;
  #pageInfo;
  #status;
  #deleteDialog;
  #deleteDialogMessage;
  #rows = [];
  #selectedIds = new Set();
  #pageNumber = 0;
  #pageSize = 20;
  #totalPages = 0;
  #totalElements = 0;
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
        .title {
          margin: 0;
          font-size: 1.2rem;
        }
        .subtitle {
          margin: 4px 0 12px;
          color: #5d6473;
          font-size: 0.92rem;
        }
        .filters {
          display: grid;
          grid-template-columns: repeat(5, minmax(140px, 1fr));
          gap: 10px;
          margin-bottom: 12px;
        }
        .toolbar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 8px;
          flex-wrap: wrap;
          margin-bottom: 10px;
        }
        .actions,
        .pager {
          display: flex;
          align-items: center;
          gap: 8px;
          flex-wrap: wrap;
        }
        .page-meta {
          color: #5d6473;
          font-size: 0.85rem;
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
          vertical-align: top;
        }
        th {
          color: #4f5768;
          font-weight: 600;
        }
        .checkbox-cell {
          width: 44px;
          text-align: center;
        }
        .clip {
          margin: 0;
          white-space: pre-wrap;
          word-break: break-word;
          max-height: 120px;
          overflow: auto;
          border: 1px solid #e8ebf1;
          border-radius: 8px;
          padding: 8px;
          background: #fafbff;
          min-width: 220px;
          max-width: 360px;
        }
        .empty {
          color: #5d6473;
          text-align: center;
          padding: 16px 0;
        }
        .delete-btn {
          --md-filled-button-container-color: #b3261e;
          --md-filled-button-label-text-color: #ffffff;
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
        @media (max-width: 1200px) {
          .filters {
            grid-template-columns: 1fr 1fr;
          }
        }
        @media (max-width: 700px) {
          .filters {
            grid-template-columns: 1fr;
          }
        }
      </style>
      <section>
        <h2 class="title">Recorded Responses</h2>
        <p class="subtitle">Filter cached proxy responses and bulk delete selected rows.</p>

        <div class="filters">
          <md-filled-text-field id="proxy-salt-filter" label="Proxy Salt"></md-filled-text-field>
          <md-filled-text-field id="url-filter" label="URL Contains"></md-filled-text-field>
          <md-filled-text-field id="request-body-filter" label="Req Body Contains"></md-filled-text-field>
          <md-filled-text-field id="headers-filter" label="Headers Contains"></md-filled-text-field>
          <md-filled-text-field id="page-size" label="Size" type="number" value="20"></md-filled-text-field>
        </div>

        <div class="toolbar">
          <div class="actions">
            <md-filled-button id="apply-filter-btn" type="button">
              <md-icon slot="icon">search</md-icon>
              Apply
            </md-filled-button>
            <md-outlined-button id="clear-filter-btn" type="button">
              <md-icon slot="icon">filter_alt_off</md-icon>
              Clear
            </md-outlined-button>
          </div>
          <div class="pager">
            <md-outlined-button id="prev-page-btn" type="button">
              <md-icon slot="icon">arrow_back</md-icon>
              Prev
            </md-outlined-button>
            <md-outlined-button id="next-page-btn" type="button">
              <md-icon slot="icon">arrow_forward</md-icon>
              Next
            </md-outlined-button>
            <md-filled-button id="delete-selected-btn" class="delete-btn" type="button" disabled>
              <md-icon slot="icon">delete</md-icon>
              <span id="delete-selected-label">Delete Selected</span>
            </md-filled-button>
          </div>
        </div>

        <div class="page-meta" id="page-info">Page 1 / 1</div>

        <table>
          <thead>
            <tr>
              <th class="checkbox-cell"><md-checkbox id="select-all"></md-checkbox></th>
              <th>ID</th>
              <th>Proxy Salt</th>
              <th>Method</th>
              <th>Status</th>
              <th>URL</th>
              <th>Request Body</th>
              <th>Headers</th>
            </tr>
          </thead>
          <tbody id="recorded-response-table"></tbody>
        </table>

        <div id="status" class="status"></div>
      </section>

      <md-dialog id="delete-dialog">
        <div slot="headline">Confirm Delete</div>
        <div slot="content" id="delete-dialog-message"></div>
        <div slot="actions">
          <md-text-button id="cancel-delete-btn" type="button">
            <md-icon slot="icon">close</md-icon>
            Cancel
          </md-text-button>
          <md-filled-button id="confirm-delete-btn" class="delete-btn" type="button">
            <md-icon slot="icon">delete</md-icon>
            Delete
          </md-filled-button>
        </div>
      </md-dialog>
    `;

    this.#tableBody = this.#root.querySelector("#recorded-response-table");
    this.#proxySaltInput = this.#root.querySelector("#proxy-salt-filter");
    this.#urlInput = this.#root.querySelector("#url-filter");
    this.#requestBodyInput = this.#root.querySelector("#request-body-filter");
    this.#headersInput = this.#root.querySelector("#headers-filter");
    this.#pageSizeInput = this.#root.querySelector("#page-size");
    this.#deleteButton = this.#root.querySelector("#delete-selected-btn");
    this.#deleteButtonLabel = this.#root.querySelector("#delete-selected-label");
    this.#selectAllCheckbox = this.#root.querySelector("#select-all");
    this.#prevButton = this.#root.querySelector("#prev-page-btn");
    this.#nextButton = this.#root.querySelector("#next-page-btn");
    this.#pageInfo = this.#root.querySelector("#page-info");
    this.#status = this.#root.querySelector("#status");
    this.#deleteDialog = this.#root.querySelector("#delete-dialog");
    this.#deleteDialogMessage = this.#root.querySelector("#delete-dialog-message");
  }

  #bindEvents() {
    this.#root
      .querySelector("#apply-filter-btn")
      ?.addEventListener("click", () => this.#applyFilter());
    this.#root
      .querySelector("#clear-filter-btn")
      ?.addEventListener("click", () => this.#clearFiltersAndReload());
    this.#deleteButton?.addEventListener("click", () => this.#openDeleteDialog());
    this.#root
      .querySelector("#cancel-delete-btn")
      ?.addEventListener("click", () => {
        this.#deleteDialog.open = false;
      });
    this.#root
      .querySelector("#confirm-delete-btn")
      ?.addEventListener("click", () => this.#deleteSelected());
    this.#selectAllCheckbox?.addEventListener("change", event =>
      this.#toggleSelectAll(event.target.checked)
    );
    this.#prevButton?.addEventListener("click", () => this.#prevPage());
    this.#nextButton?.addEventListener("click", () => this.#nextPage());

    [
      this.#proxySaltInput,
      this.#urlInput,
      this.#requestBodyInput,
      this.#headersInput,
      this.#pageSizeInput,
    ].forEach(input => {
      input?.addEventListener("keydown", event => {
        if (event.key === "Enter") {
          event.preventDefault();
          this.#applyFilter();
        }
      });
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

  #normalizePageSize(value) {
    const size = Number(value);
    if (!Number.isFinite(size) || size <= 0) {
      return 20;
    }
    return Math.min(size, 200);
  }

  #updateDeleteButtonState() {
    const count = this.#selectedIds.size;
    this.#deleteButton.disabled = count === 0;
    this.#deleteButtonLabel.textContent =
      count > 0 ? `Delete Selected (${count})` : "Delete Selected";
  }

  #updateSelectAllState() {
    const rowCount = this.#rows.length;
    if (rowCount === 0 || this.#selectedIds.size === 0) {
      this.#selectAllCheckbox.checked = false;
      this.#selectAllCheckbox.indeterminate = false;
      return;
    }
    if (this.#selectedIds.size === rowCount) {
      this.#selectAllCheckbox.checked = true;
      this.#selectAllCheckbox.indeterminate = false;
      return;
    }
    this.#selectAllCheckbox.checked = false;
    this.#selectAllCheckbox.indeterminate = true;
  }

  #updatePager() {
    const currentPage = this.#pageNumber + 1;
    const totalPages = this.#totalPages || 1;
    this.#pageInfo.textContent = `Page ${currentPage} / ${totalPages} (Total ${this.#totalElements})`;
    this.#prevButton.disabled = this.#pageNumber <= 0;
    this.#nextButton.disabled =
      this.#totalPages <= 0 || this.#pageNumber >= this.#totalPages - 1;
  }

  #applyFilter() {
    this.#pageNumber = 0;
    this.#pageSize = this.#normalizePageSize(this.#pageSizeInput.value);
    this.#pageSizeInput.value = String(this.#pageSize);
    this.#loadRows();
  }

  #clearFiltersAndReload() {
    this.#proxySaltInput.value = "";
    this.#urlInput.value = "";
    this.#requestBodyInput.value = "";
    this.#headersInput.value = "";
    this.#pageNumber = 0;
    this.#pageSize = 20;
    this.#pageSizeInput.value = "20";
    this.#loadRows();
  }

  #prevPage() {
    if (this.#pageNumber <= 0) {
      return;
    }
    this.#pageNumber -= 1;
    this.#loadRows();
  }

  #nextPage() {
    if (this.#totalPages <= 0 || this.#pageNumber >= this.#totalPages - 1) {
      return;
    }
    this.#pageNumber += 1;
    this.#loadRows();
  }

  #buildQuery() {
    const params = new URLSearchParams();
    const proxySalt = this.#proxySaltInput.value.trim();
    const urlContains = this.#urlInput.value.trim();
    const requestBodyContains = this.#requestBodyInput.value.trim();
    const headersContains = this.#headersInput.value.trim();

    if (proxySalt) {
      params.set("proxySalt", proxySalt);
    }
    if (urlContains) {
      params.set("urlContains", urlContains);
    }
    if (requestBodyContains) {
      params.set("requestBodyContains", requestBodyContains);
    }
    if (headersContains) {
      params.set("headersContains", headersContains);
    }
    params.set("page", String(this.#pageNumber));
    params.set("size", String(this.#pageSize));
    return `?${params.toString()}`;
  }

  #toggleSelectAll(checked) {
    this.#selectedIds.clear();
    if (checked) {
      for (const row of this.#rows) {
        this.#selectedIds.add(row.id);
      }
    }
    this.#renderRows();
  }

  #createTextCell(text) {
    const cell = document.createElement("td");
    cell.textContent = text ?? "";
    return cell;
  }

  #createClipCell(text) {
    const cell = document.createElement("td");
    const pre = document.createElement("pre");
    pre.className = "clip";
    pre.textContent = text ?? "";
    cell.appendChild(pre);
    return cell;
  }

  #renderRows() {
    this.#tableBody.innerHTML = "";

    if (!this.#rows || this.#rows.length === 0) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 8;
      cell.className = "empty";
      cell.textContent = "No recorded responses found";
      row.appendChild(cell);
      this.#tableBody.appendChild(row);
      this.#selectedIds.clear();
      this.#updateSelectAllState();
      this.#updateDeleteButtonState();
      this.#updatePager();
      return;
    }

    for (const item of this.#rows) {
      const row = document.createElement("tr");

      const checkCell = document.createElement("td");
      checkCell.className = "checkbox-cell";
      const check = document.createElement("md-checkbox");
      check.checked = this.#selectedIds.has(item.id);
      check.addEventListener("change", () => {
        if (check.checked) {
          this.#selectedIds.add(item.id);
        } else {
          this.#selectedIds.delete(item.id);
        }
        this.#updateSelectAllState();
        this.#updateDeleteButtonState();
      });
      checkCell.appendChild(check);

      row.appendChild(checkCell);
      row.appendChild(this.#createTextCell(String(item.id ?? "")));
      row.appendChild(this.#createTextCell(item.mockServerConfigProxySalt ?? ""));
      row.appendChild(this.#createTextCell(item.httpMethod ?? ""));
      row.appendChild(this.#createTextCell(String(item.responseStatus ?? "")));
      row.appendChild(this.#createTextCell(item.targetUrl ?? ""));
      row.appendChild(this.#createClipCell(item.requestBody ?? ""));
      row.appendChild(this.#createClipCell(item.requestHeadersJson ?? ""));

      this.#tableBody.appendChild(row);
    }

    this.#updateSelectAllState();
    this.#updateDeleteButtonState();
    this.#updatePager();
  }

  #openDeleteDialog() {
    const count = this.#selectedIds.size;
    if (count === 0) {
      return;
    }
    this.#deleteDialogMessage.textContent =
      `You are going to delete ${count} cached proxresponse. Continue?`;
    this.#deleteDialog.open = true;
  }

  async #deleteSelected() {
    const ids = Array.from(this.#selectedIds.values());
    if (ids.length === 0) {
      return;
    }

    try {
      const response = await fetch(this.#url("/recorded-responses"), {
        method: "DELETE",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ ids }),
      });
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Delete failed: ${errorText || response.status}`, "error");
        return;
      }
      this.#deleteDialog.open = false;
      this.#setStatus("Selected records deleted", "success");
      await this.#loadRows();
    } catch (error) {
      this.#setStatus(`Delete failed: ${error.message}`, "error");
    }
  }

  async #loadRows() {
    try {
      const response = await fetch(
        this.#url(`/recorded-responses${this.#buildQuery()}`)
      );
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Load failed: ${errorText || response.status}`, "error");
        return;
      }
      const data = await response.json();
      this.#rows = data.content ?? [];
      this.#pageNumber = data.pageNumber ?? 0;
      this.#pageSize = data.pageSize ?? this.#pageSize;
      this.#totalPages = data.totalPages ?? 0;
      this.#totalElements = data.totalElements ?? 0;
      this.#selectedIds.clear();
      this.#renderRows();
    } catch (error) {
      this.#setStatus(`Load failed: ${error.message}`, "error");
    }
  }
}

if (!customElements.get("recorded-response-panel")) {
  customElements.define("recorded-response-panel", RecordedResponsePanel);
}
