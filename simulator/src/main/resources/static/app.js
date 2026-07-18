"use strict";

// SatSim frontend (SIM-REQ-UI-001/002/004): compose TCs with live hex preview,
// send via REST (ICD §8), show TM live from the /api/tm WebSocket.

const $ = (id) => document.getElementById(id);
const logBody = document.querySelector("#log tbody");
const MAX_ROWS = 200;

function ackFlags() {
  return (
    ($("ack-acceptance").checked ? 8 : 0)
    | ($("ack-start").checked ? 4 : 0)
    | ($("ack-progress").checked ? 2 : 0)
    | ($("ack-completion").checked ? 1 : 0)
  );
}

function composeBody() {
  return {
    service: Number($("service").value),
    subtype: Number($("subtype").value),
    ackFlags: ackFlags(),
    appDataHex: $("app-data").value.trim(),
  };
}

function prettyHex(hex) {
  return hex.replace(/\s/g, "").toUpperCase().replace(/(..)/g, "$1 ").trim();
}

function showError(message) {
  const box = $("error");
  box.textContent = message;
  box.hidden = !message;
}

async function post(path, body) {
  const response = await fetch(path, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(json.error || `HTTP ${response.status}`);
  }
  return json;
}

function addRow(dir, obt, type, seq, ctr, hex) {
  const row = document.createElement("tr");
  row.className = dir.toLowerCase();
  const cells = [dir, obt, type, seq, ctr];
  for (const value of cells) {
    const cell = document.createElement("td");
    cell.textContent = value;
    row.appendChild(cell);
  }
  const hexCell = document.createElement("td");
  hexCell.className = "hex-cell";
  hexCell.textContent = prettyHex(hex);
  row.appendChild(hexCell);
  logBody.prepend(row);
  while (logBody.childElementCount > MAX_ROWS) {
    logBody.lastElementChild.remove();
  }
}

// --- Compose preview (SIM-REQ-UI-004: raw bytes visible before sending) ---

let previewTimer;
function schedulePreview() {
  clearTimeout(previewTimer);
  previewTimer = setTimeout(refreshPreview, 150);
}

async function refreshPreview() {
  try {
    const { hex } = await post("/api/tc/preview", composeBody());
    $("preview").textContent = prettyHex(hex);
    showError("");
  } catch (error) {
    $("preview").textContent = "—";
    showError(`Preview failed: ${error.message}`);
  }
}

$("compose-form").addEventListener("input", schedulePreview);

// --- Sending ---

$("compose-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const body = composeBody();
    const { hex } = await post("/api/tc", body);
    addRow("TC", "—", `TC(${body.service},${body.subtype})`, "", "", hex);
    showError("");
  } catch (error) {
    showError(`Send failed: ${error.message}`);
  }
});

$("raw-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const { hex } = await post("/api/tc", { hex: $("raw-hex").value.trim() });
    addRow("TC", "—", "raw", "", "", hex);
    showError("");
  } catch (error) {
    showError(`Injection failed: ${error.message}`);
  }
});

// --- TM live log (SIM-REQ-UI-002) ---

function connectTm() {
  const scheme = location.protocol === "https:" ? "wss" : "ws";
  const socket = new WebSocket(`${scheme}://${location.host}/api/tm`);
  const status = $("link-status");

  socket.onopen = () => {
    status.textContent = "TM link: online";
    status.className = "status online";
  };
  socket.onclose = () => {
    status.textContent = "TM link: offline — retrying…";
    status.className = "status offline";
    setTimeout(connectTm, 1000);
  };
  socket.onmessage = (event) => {
    const frame = JSON.parse(event.data);
    const d = frame.decoded;
    if (d) {
      addRow(
        "TM",
        d.timeSeconds.toFixed(3),
        `TM(${d.service},${d.subtype})`,
        String(d.sequenceCount),
        String(d.messageTypeCounter),
        frame.hex,
      );
    } else {
      addRow("TM", "?", "undecodable", "", "", frame.hex);
    }
  };
}

connectTm();
refreshPreview();
