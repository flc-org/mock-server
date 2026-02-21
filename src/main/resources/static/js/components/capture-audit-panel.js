import "/js/components/material-primitives.js";

class CaptureAuditPanel extends HTMLElement {
  #root;
  #tableBody;
  #captureKeyInput;
  #proxySaltInput;
  #pageSizeInput;
  #prevButton;
  #nextButton;
  #pageInfo;
  #status;
  #statusTimerId = null;
  #pageNumber = 0;
  #pageSize = 20;
  #totalPages = 0;
  #totalElements = 0;
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
        h2 {
          margin: 0;
          font-size: 1.2rem;
        }
        .subtitle {
          margin: 2px 0 12px;
          color: #5d6473;
          font-size: 0.92rem;
        }
        .filters {
          display: grid;
          grid-template-columns: 1fr 1fr 120px;
          gap: 8px;
          margin-bottom: 10px;
        }
        .toolbar {
          display: flex;
          justify-content: space-between;
          align-items: center;
          gap: 8px;
          margin-bottom: 10px;
          flex-wrap: wrap;
        }
        .toolbar-buttons,
        .pager {
          display: flex;
          gap: 8px;
          align-items: center;
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
        .req {
          margin: 0;
          white-space: pre-wrap;
          word-break: break-word;
          max-height: 140px;
          overflow: auto;
          border: 1px solid #e8ebf1;
          border-radius: 8px;
          padding: 8px;
          background: #fafbff;
          min-width: 220px;
          max-width: 420px;
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
          color: #b3261e;
        }
        @media (max-width: 1024px) {
          .filters {
            grid-template-columns: 1fr;
          }
        }
      </style>
      <section>
        <h2>Capture Audit</h2>
        <p class="subtitle">Filtered, paginated view of capture audit entries.</p>

        <div class="filters">
          <md-filled-text-field id="capture-key-filter" label="Capture Key"></md-filled-text-field>
          <md-filled-text-field id="proxy-salt-filter" label="Proxy Salt"></md-filled-text-field>
          <md-filled-text-field id="page-size" label="Size" type="number" value="20"></md-filled-text-field>
        </div>

        <div class="toolbar">
          <div class="toolbar-buttons">
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
            <span id="page-info" class="page-meta">Page 1 / 1</span>
          </div>
        </div>

        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Capture Key</th>
              <th>Proxy Salt</th>
              <th>Request DateTime</th>
              <th>Request</th>
            </tr>
          </thead>
          <tbody id="capture-audit-table"></tbody>
        </table>

        <div id="status" class="status"></div>
      </section>
    `;

    this.#tableBody = this.#root.querySelector("#capture-audit-table");
    this.#captureKeyInput = this.#root.querySelector("#capture-key-filter");
    this.#proxySaltInput = this.#root.querySelector("#proxy-salt-filter");
    this.#pageSizeInput = this.#root.querySelector("#page-size");
    this.#prevButton = this.#root.querySelector("#prev-page-btn");
    this.#nextButton = this.#root.querySelector("#next-page-btn");
    this.#pageInfo = this.#root.querySelector("#page-info");
    this.#status = this.#root.querySelector("#status");
  }

  #bindEvents() {
    this.#root
      .querySelector("#apply-filter-btn")
      ?.addEventListener("click", () => this.#applyFilter());
    this.#root
      .querySelector("#clear-filter-btn")
      ?.addEventListener("click", () => this.#clearFilter());
    this.#prevButton?.addEventListener("click", () => this.#prevPage());
    this.#nextButton?.addEventListener("click", () => this.#nextPage());

    [this.#captureKeyInput, this.#proxySaltInput, this.#pageSizeInput].forEach(
      input => {
        input?.addEventListener("keydown", event => {
          if (event.key === "Enter") {
            event.preventDefault();
            this.#applyFilter();
          }
        });
      }
    );
  }

  #setStatus(message) {
    this.#status.textContent = message;
    if (this.#statusTimerId !== null) {
      clearTimeout(this.#statusTimerId);
    }
    this.#statusTimerId = window.setTimeout(() => {
      this.#status.textContent = "";
    }, 2600);
  }

  #applyFilter() {
    this.#pageNumber = 0;
    this.#pageSize = this.#normalizePageSize(this.#pageSizeInput.value);
    this.#pageSizeInput.value = String(this.#pageSize);
    this.#loadRows();
  }

  #clearFilter() {
    this.#captureKeyInput.value = "";
    this.#proxySaltInput.value = "";
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

  #updatePager() {
    const currentPage = this.#pageNumber + 1;
    const totalPages = this.#totalPages || 1;
    this.#pageInfo.textContent = `Page ${currentPage} / ${totalPages} (Total ${this.#totalElements})`;
    this.#prevButton.disabled = this.#pageNumber <= 0;
    this.#nextButton.disabled =
      this.#totalPages <= 0 || this.#pageNumber >= this.#totalPages - 1;
  }

  #normalizePageSize(value) {
    const size = Number(value);
    if (!Number.isFinite(size) || size <= 0) {
      return 20;
    }
    return Math.min(size, 200);
  }

  #buildQuery() {
    const params = new URLSearchParams();
    const captureKey = this.#captureKeyInput.value.trim();
    const proxySalt = this.#proxySaltInput.value.trim();
    if (captureKey) {
      params.set("captureKey", captureKey);
    }
    if (proxySalt) {
      params.set("proxySalt", proxySalt);
    }
    params.set("page", String(this.#pageNumber));
    params.set("size", String(this.#pageSize));
    return `?${params.toString()}`;
  }

  #renderRows() {
    this.#tableBody.innerHTML = "";
    if (!this.#rows || this.#rows.length === 0) {
      const row = document.createElement("tr");
      const cell = document.createElement("td");
      cell.colSpan = 5;
      cell.className = "empty";
      cell.textContent = "No capture audit records found.";
      row.appendChild(cell);
      this.#tableBody.appendChild(row);
      this.#updatePager();
      return;
    }

    for (const item of this.#rows) {
      const row = document.createElement("tr");
      row.appendChild(this.#createCell(item.id));
      row.appendChild(this.#createCell(item.captureKey));
      row.appendChild(this.#createCell(item.proxySalt));
      row.appendChild(this.#createCell(item.requestDateTime));
      row.appendChild(this.#createReqCell(item.req));
      this.#tableBody.appendChild(row);
    }

    this.#updatePager();
  }

  #createCell(value) {
    const cell = document.createElement("td");
    cell.textContent = value == null ? "" : String(value);
    return cell;
  }

  #createReqCell(value) {
    const cell = document.createElement("td");
    const pre = document.createElement("pre");
    pre.className = "req";
    pre.textContent = value ?? "";
    cell.appendChild(pre);
    return cell;
  }

  async #loadRows() {
    try {
      const response = await fetch(this.#url(`/capture-audits${this.#buildQuery()}`));
      if (!response.ok) {
        const errorText = await response.text();
        this.#setStatus(`Load failed: ${errorText || response.status}`);
        return;
      }
      const data = await response.json();
      this.#rows = data.content ?? [];
      this.#pageNumber = data.pageNumber ?? 0;
      this.#pageSize = data.pageSize ?? this.#pageSize;
      this.#totalPages = data.totalPages ?? 0;
      this.#totalElements = data.totalElements ?? 0;
      this.#renderRows();
    } catch (error) {
      this.#setStatus(`Load failed: ${error.message}`);
    }
  }
}

if (!customElements.get("capture-audit-panel")) {
  customElements.define("capture-audit-panel", CaptureAuditPanel);
}
