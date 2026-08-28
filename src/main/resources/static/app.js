"use strict";

/*
 * Front end for the Counter demo. Talks to the same-origin REST API:
 *   POST   /api/transactions
 *   GET    /api/transactions/{id}
 *   PATCH  /api/transactions/{id}/status
 *   GET    /api/transactions?customerId={id}
 *
 * The allowed status transitions mirror the TransactionStatus enum on the
 * server. The server is still the authority - it rejects anything not allowed;
 * this map just decides which options to offer.
 */
const API = "/api/transactions";
const TRANSITIONS = {
  PENDING: ["COMPLETED", "FAILED", "CANCELLED"],
  COMPLETED: ["REVERSED"],
  FAILED: [],
  CANCELLED: [],
  REVERSED: [],
};

const $ = (sel) => document.querySelector(sel);
const el = (tag, props = {}, ...kids) => {
  const node = Object.assign(document.createElement(tag), props);
  for (const k of kids) node.append(k);
  return node;
};

/* <tr> elements currently shown, keyed by transactionId, so status changes update in place */
const rows = new Map();

/* ---------- helpers ---------- */

function genId() {
  const ymd = new Date().toISOString().slice(0, 10).replace(/-/g, "");
  const rnd = Math.random().toString(36).slice(2, 8).toUpperCase();
  return `TXN-${ymd}-${rnd}`;
}

function money(amount, currency) {
  try {
    return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(Number(amount));
  } catch {
    return `${amount} ${currency}`;
  }
}

function when(iso) {
  const d = new Date(iso);
  if (isNaN(d)) return "—";
  return d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

async function api(path, options) {
  const res = await fetch(path, options);
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  return { ok: res.ok, status: res.status, data };
}

/* ---------- results panels ---------- */

function showResult(target, kind, title, detailNode) {
  const box = $(target);
  box.className = `result ${kind}`;
  box.hidden = false;
  box.replaceChildren(el("h3", { textContent: title }));
  if (detailNode) box.append(detailNode);
}

function apiErrorDetail(err) {
  const frag = document.createDocumentFragment();
  if (err && err.message) frag.append(el("div", { textContent: err.message }));
  if (err && Array.isArray(err.fieldErrors) && err.fieldErrors.length) {
    const ul = el("ul");
    for (const fe of err.fieldErrors) {
      ul.append(el("li", {}, el("code", { textContent: fe.field }), ` — ${fe.message}`));
    }
    frag.append(ul);
  }
  return frag;
}

/* ---------- table ---------- */

function renderTable(list, title, meta) {
  rows.clear();
  $("#txnBody").replaceChildren();
  $("#tableTitle").textContent = title;
  $("#tableMeta").textContent = meta || "";
  for (const t of list) upsertRow(t, false);
  reflectEmpty();
}

function reflectEmpty() {
  const has = rows.size > 0;
  $("#txnTable").style.display = has ? "" : "none";
  $("#tableEmpty").hidden = has;
}

function upsertRow(t, flash) {
  let row = rows.get(t.transactionId);
  const isNew = !row;
  if (isNew) {
    row = el("tr");
    rows.set(t.transactionId, row);
  }

  const nexts = TRANSITIONS[t.status] || [];
  let advance;
  if (nexts.length === 0) {
    advance = el("span", { className: "terminal", textContent: "final" });
  } else {
    const sel = el("select");
    sel.append(el("option", { value: "", textContent: "choose…" }));
    for (const s of nexts) sel.append(el("option", { value: s, textContent: s }));
    const go = el("button", { className: "mini", textContent: "Apply", type: "button" });
    go.addEventListener("click", () => {
      if (sel.value) changeStatus(t.transactionId, sel.value);
    });
    advance = el("div", { className: "advance" }, sel, go);
  }

  row.replaceChildren(
    el("td", { className: "cell-id" }, t.transactionId),
    el("td", { textContent: t.customerId }),
    el("td", { className: "num", textContent: money(t.amount, t.currency) }),
    el("td", { textContent: t.type }),
    el("td", {}, el("span", { className: `badge ${t.status}`, textContent: t.status })),
    el("td", { textContent: when(t.updatedAt) }),
    el("td", {}, advance),
  );

  if (isNew) $("#txnBody").prepend(row);
  if (flash) {
    row.classList.remove("flash");
    void row.offsetWidth;
    row.classList.add("flash");
  }
  reflectEmpty();
}

/* ---------- actions ---------- */

async function createTransaction(evt) {
  evt.preventDefault();
  const btn = $("#submitBtn");
  btn.disabled = true;
  try {
    const body = {
      transactionId: $("#transactionId").value.trim(),
      customerId: $("#customerId").value.trim(),
      amount: $("#amount").value === "" ? null : Number($("#amount").value),
      currency: $("#currency").value,
      type: $("#type").value,
    };
    const { ok, data } = await api(API, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (ok) {
      showResult("#formResult", "ok", "Recorded",
        el("div", {}, el("code", { textContent: data.transactionId }),
          ` · ${money(data.amount, data.currency)} · starts as PENDING (see the table for live status)`));
      upsertRow(data, true);
      $("#txnForm").reset();
      $("#transactionId").value = genId();
      $("#customerId").focus();
    } else {
      showResult("#formResult", "bad", "Not recorded", apiErrorDetail(data));
    }
  } catch (e) {
    showResult("#formResult", "bad", "Request failed", el("div", { textContent: String(e) }));
  } finally {
    btn.disabled = false;
  }
}

async function changeStatus(id, status) {
  try {
    const { ok, data } = await api(`${API}/${encodeURIComponent(id)}/status`, {
      method: "PATCH",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ status }),
    });
    if (ok) {
      upsertRow(data, true);
      showResult("#lookupResult", "ok", `${id} → ${status}`, null);
      $("#formResult").hidden = true;   // the "Recorded ... PENDING" receipt is now stale
    } else {
      showResult("#lookupResult", "bad", "Status not changed", apiErrorDetail(data));
    }
  } catch (e) {
    showResult("#lookupResult", "bad", "Request failed", el("div", { textContent: String(e) }));
  }
}

async function loadCustomer() {
  const cid = $("#byCustomer").value.trim();
  if (!cid) return;
  try {
    const { ok, data } = await api(`${API}?customerId=${encodeURIComponent(cid)}`);
    if (ok) {
      renderTable(data, `Customer ${cid}`, `${data.length} transaction${data.length === 1 ? "" : "s"}`);
      $("#lookupResult").hidden = true;
    } else {
      showResult("#lookupResult", "bad", "Lookup failed", apiErrorDetail(data));
    }
  } catch (e) {
    showResult("#lookupResult", "bad", "Request failed", el("div", { textContent: String(e) }));
  }
}

async function loadById() {
  const id = $("#byId").value.trim();
  if (!id) return;
  try {
    const { ok, status, data } = await api(`${API}/${encodeURIComponent(id)}`);
    if (ok) {
      renderTable([data], `Transaction ${id}`, "");
      $("#lookupResult").hidden = true;
    } else if (status === 404) {
      showResult("#lookupResult", "bad", "No such transaction",
        el("div", { textContent: `Nothing stored with id ${id}.` }));
    } else {
      showResult("#lookupResult", "bad", "Lookup failed", apiErrorDetail(data));
    }
  } catch (e) {
    showResult("#lookupResult", "bad", "Request failed", el("div", { textContent: String(e) }));
  }
}

async function pingApi() {
  const box = $("#apiStatus");
  try {
    const res = await fetch(`${API}?customerId=__ping__`);
    if (res.ok) {
      box.className = "api-status up";
      $("#apiStatusText").textContent = "API connected";
      return;
    }
    throw new Error();
  } catch {
    box.className = "api-status down";
    $("#apiStatusText").textContent = "API unreachable";
  }
}

/* ---------- wire up ---------- */

$("#transactionId").value = genId();
$("#regenId").addEventListener("click", () => { $("#transactionId").value = genId(); });
$("#txnForm").addEventListener("submit", createTransaction);
$("#loadCustomer").addEventListener("click", loadCustomer);
$("#loadId").addEventListener("click", loadById);
$("#byCustomer").addEventListener("keydown", (e) => { if (e.key === "Enter") loadCustomer(); });
$("#byId").addEventListener("keydown", (e) => { if (e.key === "Enter") loadById(); });
$("#txnTable").style.display = "none";

pingApi();
