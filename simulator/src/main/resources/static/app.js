"use strict";

// SatSim frontend (SIM-REQ-UI-001/002/004..010): dropdown TC compose with
// live hex preview (ICD §8.1), live TM/time/rejection log from the /api/tm
// WebSocket (ICD §8.2), OBT clock, log filters, packet detail view.

const $ = (id) => document.getElementById(id);
const logBody = document.querySelector("#log tbody");
const MAX_ROWS = 200;
const OBT_EPOCH_MILLIS = Date.UTC(2026, 0, 1);

// Tailored TC set (ICD): grows with the milestones (M1b adds ST[3]).
const TC_TYPES = [
  {
    type: 17,
    name: "ST[17] Test",
    subtypes: [{ subtype: 1, name: "Are-you-alive (ping)" }],
  },
  {
    type: 3,
    name: "ST[3] Housekeeping",
    subtypes: [
      { subtype: 1, name: "Create HK structure" },
      { subtype: 5, name: "Enable periodic reports" },
      { subtype: 7, name: "Disable periodic reports" },
    ],
  },
];
const CUSTOM = "custom";

// --- Compose dropdowns (SIM-REQ-UI-010) ---

function fillTypeSelect() {
  for (const entry of TC_TYPES) {
    const option = document.createElement("option");
    option.value = String(entry.type);
    option.textContent = `${entry.type} — ${entry.name}`;
    $("type-select").appendChild(option);
  }
  const custom = document.createElement("option");
  custom.value = CUSTOM;
  custom.textContent = "custom…";
  $("type-select").appendChild(custom);
  fillSubtypeSelect();
}

function fillSubtypeSelect() {
  const select = $("subtype-select");
  select.replaceChildren();
  const entry = TC_TYPES.find((t) => String(t.type) === $("type-select").value);
  for (const sub of entry ? entry.subtypes : []) {
    const option = document.createElement("option");
    option.value = String(sub.subtype);
    option.textContent = `${sub.subtype} — ${sub.name}`;
    select.appendChild(option);
  }
  const custom = document.createElement("option");
  custom.value = CUSTOM;
  custom.textContent = "custom…";
  select.appendChild(custom);
  if (!entry) {
    select.value = CUSTOM;
  }
  syncCustomInputs();
}

function syncCustomInputs() {
  $("type-custom").hidden = $("type-select").value !== CUSTOM;
  $("subtype-custom").hidden = $("subtype-select").value !== CUSTOM;
}

function selectedType() {
  const value = $("type-select").value;
  return Number(value === CUSTOM ? $("type-custom").value : value);
}

function selectedSubtype() {
  const value = $("subtype-select").value;
  return Number(value === CUSTOM ? $("subtype-custom").value : value);
}

$("type-select").addEventListener("change", fillSubtypeSelect);
$("subtype-select").addEventListener("change", syncCustomInputs);

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
    service: selectedType(),
    subtype: selectedSubtype(),
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

// --- OBT clock (SIM-REQ-UI-005) ---

let obtSeconds = null;

function updateObt(seconds) {
  if (obtSeconds !== null && seconds <= obtSeconds) {
    return;
  }
  obtSeconds = seconds;
  const utc = new Date(OBT_EPOCH_MILLIS + seconds * 1000);
  $("obt-utc").textContent = utc.toISOString().replace("T", " ").replace(/\.\d+Z/, " UTC");
  $("obt-seconds").textContent = `T+ ${seconds.toFixed(1)} s`;
}

// --- Live packet log (SIM-REQ-UI-002/006/007/008/009) ---

let paused = false;
const pendingRows = [];

function rowVisible(row) {
  const kindShown = { TC: $("show-tc"), TM: $("show-tm"), REJ: $("show-rej") }[row.dataset.kind];
  if (kindShown && !kindShown.checked) {
    return false;
  }
  const serviceFilter = $("service-filter").value;
  if (serviceFilter === "") {
    return true;
  }
  return row.dataset.service === serviceFilter;
}

function applyFilters() {
  for (const row of logBody.children) {
    row.hidden = !rowVisible(row);
  }
}

function addRow(kind, obt, type, seq, ctr, hex, service, detail) {
  const row = document.createElement("tr");
  row.className = kind.toLowerCase();
  row.dataset.kind = kind;
  row.dataset.service = service === null || service === undefined ? "" : String(service);
  row.dataset.detail = JSON.stringify(detail);
  const chevron = document.createElement("td");
  chevron.className = "chev";
  chevron.textContent = "▸";
  row.appendChild(chevron);
  const badge = document.createElement("td");
  const pill = document.createElement("span");
  pill.className = `badge ${kind.toLowerCase()}`;
  pill.textContent = kind;
  badge.appendChild(pill);
  row.appendChild(badge);
  for (const value of [obt, type, seq, ctr]) {
    const cell = document.createElement("td");
    cell.textContent = value;
    row.appendChild(cell);
  }
  const hexCell = document.createElement("td");
  hexCell.className = "hex-cell";
  hexCell.title = "Click to copy";
  hexCell.textContent = prettyHex(hex);
  row.appendChild(hexCell);

  if (paused) {
    pendingRows.push(row);
    $("pause").textContent = `Resume (${pendingRows.length})`;
    return;
  }
  insertRow(row);
}

function insertRow(row) {
  row.hidden = !rowVisible(row);
  logBody.prepend(row);
  while (logBody.childElementCount > MAX_ROWS) {
    logBody.lastElementChild.remove();
  }
}

// Detail view (SIM-REQ-UI-009) and copy-to-clipboard on hex cells.
logBody.addEventListener("click", (event) => {
  const hexCell = event.target.closest("td.hex-cell");
  const row = event.target.closest("tr");
  if (!row || row.classList.contains("detail-row")) {
    return;
  }
  if (hexCell) {
    navigator.clipboard?.writeText(hexCell.textContent).then(() => {
      hexCell.classList.add("copied");
      setTimeout(() => hexCell.classList.remove("copied"), 600);
    });
    return;
  }
  const existing = row.nextElementSibling;
  if (existing && existing.classList.contains("detail-row")) {
    existing.remove();
    setExpanded(row, false);
    return;
  }
  document.querySelectorAll("tr.detail-row").forEach((detailRow) => detailRow.remove());
  document.querySelectorAll("tr.expanded").forEach((open) => setExpanded(open, false));
  const detailRow = document.createElement("tr");
  detailRow.className = "detail-row";
  const cell = document.createElement("td");
  cell.colSpan = 7;
  cell.appendChild(renderDetail(JSON.parse(row.dataset.detail)));
  detailRow.appendChild(cell);
  row.after(detailRow);
  setExpanded(row, true);
});

function setExpanded(row, expanded) {
  row.classList.toggle("expanded", expanded);
  const chevron = row.querySelector("td.chev");
  if (chevron) {
    chevron.textContent = expanded ? "▾" : "▸";
  }
}

function renderDetail(detail) {
  const container = document.createElement("div");
  container.className = "detail";
  for (const [groupName, fields] of detail.groups) {
    const heading = document.createElement("h3");
    heading.textContent = groupName;
    container.appendChild(heading);
    const list = document.createElement("dl");
    for (const [key, value] of fields) {
      const term = document.createElement("dt");
      term.textContent = key;
      const definition = document.createElement("dd");
      definition.textContent = String(value);
      list.appendChild(term);
      list.appendChild(definition);
    }
    container.appendChild(list);
  }
  return container;
}

function tcDetail(response) {
  const groups = [];
  const decoded = response.decoded;
  if (decoded) {
    groups.push(["Primary header", [
      ["APID", decoded.apid],
      ["Sequence count", decoded.sequenceCount],
    ]]);
    groups.push(["TC secondary header", [
      ["PUS version", decoded.pusVersion],
      ["Ack flags", `0b${decoded.ackFlags.toString(2).padStart(4, "0")}`],
      ["Type", decoded.service],
      ["Subtype", decoded.subtype],
      ["Source ID", decoded.sourceId],
    ]]);
    groups.push(["Application data", [["Hex", decoded.appDataHex || "(empty)"]]]);
  } else {
    groups.push(["Not decodable", [["First failed check (ICD §6.3)", response.decodeError]]]);
  }
  groups.push(["Injection", [["OBT", `${response.timeSeconds.toFixed(6)} s`]]]);
  groups.push(["Raw packet", [["Hex", prettyHex(response.hex)]]]);
  return { groups };
}

// ICD §10.4 failure codes (TM(1,2)/TM(1,8) app data); 4..8 are the ST[3]
// semantic error codes added under OP-3 at M1b.
const ST1_FAILURE_CODES = {
  1: "ILLEGAL_PUS_VERSION",
  2: "ILLEGAL_SERVICE_OR_SUBTYPE",
  3: "ILLEGAL_APPLICATION_DATA",
  4: "ILLEGAL_SID",
  5: "DUPLICATE_SID",
  6: "UNKNOWN_SID",
  7: "ILLEGAL_COLLECTION_INTERVAL",
  8: "UNKNOWN_PARAMETER",
};

// TM frames carry no decoded appDataHex field; the app data is sliced out of
// the raw packet hex instead (primary header 6 octets + TM secondary header
// 13 octets, up to the trailing 2-octet CRC, ICD §2/§4/§7).
function tmAppDataHex(frame) {
  const raw = frame.hex.replace(/\s/g, "");
  const start = 2 * (6 + 13);
  const end = raw.length - 4;
  return start <= end ? raw.slice(start, end).toUpperCase() : "";
}

function st1VerificationGroup(frame, decoded) {
  const appDataHex = tmAppDataHex(frame);
  const rows = [["Request ID", appDataHex.slice(0, 8) || "(missing)"]];
  if (decoded.subtype === 2 || decoded.subtype === 8) {
    const codeHex = appDataHex.slice(-4);
    const code = parseInt(codeHex, 16);
    const name = ST1_FAILURE_CODES[code];
    rows.push(["Failure code", codeHex ? (name ? `0x${codeHex} ${name}` : `0x${codeHex}`) : "(missing)"]);
  }
  return ["ST[1] verification", rows];
}

// ICD §9.6: SID 1 is the default structure (HK-P001/002/003, fixed order);
// its layout is known statically. Any other SID's layout was defined by
// whatever TC(3,1) created it (§9.2), which the ground UI has not observed
// (no structure-definition tracking here), so we can only show raw hex.
function st3HousekeepingGroup(frame) {
  const appDataHex = tmAppDataHex(frame);
  const sidHex = appDataHex.slice(0, 4);
  const sid = sidHex ? parseInt(sidHex, 16) : null;
  const rows = [["SID", sid === null ? "(missing)" : String(sid)]];
  if (sid === 1) {
    const tcAccepted = parseInt(appDataHex.slice(4, 12), 16);
    const tmEmitted = parseInt(appDataHex.slice(12, 20), 16);
    const batteryMv = parseInt(appDataHex.slice(20, 24), 16);
    rows.push(["HK-P001 TC accepted count", tcAccepted]);
    rows.push(["HK-P002 TM emitted count", tmEmitted]);
    rows.push(["HK-P003 battery voltage", `${batteryMv} mV`]);
  } else {
    rows.push(["Parameter values (hex)", appDataHex.slice(4) || "(empty)"]);
    rows.push(["Note", "structure layout ground-side unknown (defined by the TC(3,1) that created this SID, ICD §9.2)"]);
  }
  return ["ST[3] housekeeping report", rows];
}

function tmDetail(frame) {
  const decoded = frame.decoded;
  const groups = [];
  if (decoded) {
    groups.push(["Primary header", [
      ["APID", decoded.apid],
      ["Sequence count", decoded.sequenceCount],
    ]]);
    groups.push(["TM secondary header", [
      ["Type", decoded.service],
      ["Subtype", decoded.subtype],
      ["Message type counter", decoded.messageTypeCounter],
      ["Destination ID", decoded.destinationId],
      ["OBT", `${decoded.timeCoarse} s + ${decoded.timeFine}/65536 = ${decoded.timeSeconds.toFixed(6)} s`],
    ]]);
    if (decoded.service === 1) {
      groups.push(st1VerificationGroup(frame, decoded));
    }
    if (decoded.service === 3 && decoded.subtype === 25) {
      groups.push(st3HousekeepingGroup(frame));
    }
  } else {
    groups.push(["Not decodable", [["Note", "emitted TM failed to decode (defect)"]]]);
  }
  groups.push(["Raw packet", [["Hex", prettyHex(frame.hex)]]]);
  return { groups };
}

function rejectionDetail(frame) {
  return { groups: [
    ["Rejection (simulator diagnostic, ICD §8.2)", [
      ["Reason", frame.reason],
      ["OBT", `${frame.timeSeconds.toFixed(6)} s`],
    ]],
    ["Offending packet", [["Hex", prettyHex(frame.hex)]]],
  ] };
}

$("pause").addEventListener("click", () => {
  paused = !paused;
  $("pause").setAttribute("aria-pressed", String(paused));
  if (!paused) {
    while (pendingRows.length > 0) {
      insertRow(pendingRows.shift());
    }
    $("pause").textContent = "Pause";
  } else {
    $("pause").textContent = "Resume (0)";
  }
});

$("clear").addEventListener("click", () => {
  logBody.replaceChildren();
  pendingRows.length = 0;
  if (paused) {
    $("pause").textContent = "Resume (0)";
  }
});

for (const id of ["show-tc", "show-tm", "show-rej", "service-filter"]) {
  $(id).addEventListener("input", applyFilters);
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

// --- Sending (SIM-REQ-UI-001/006) ---

function addTcRow(response, fallbackLabel) {
  updateObt(response.timeSeconds);
  const decoded = response.decoded;
  const label = decoded ? `TC(${decoded.service},${decoded.subtype})` : fallbackLabel;
  addRow(
    "TC",
    response.timeSeconds.toFixed(3),
    label,
    response.sequenceCount === undefined ? "" : String(response.sequenceCount),
    "",
    response.hex,
    decoded ? decoded.service : null,
    tcDetail(response),
  );
}

async function sendCompose() {
  try {
    const response = await post("/api/tc", composeBody());
    addTcRow(response, "TC(?)");
    showError("");
  } catch (error) {
    showError(`Send failed: ${error.message}`);
  }
}

$("compose-form").addEventListener("submit", (event) => {
  event.preventDefault();
  sendCompose();
});

$("ping").addEventListener("click", async () => {
  try {
    const response = await post("/api/tc",
        { service: 17, subtype: 1, ackFlags: ackFlags(), appDataHex: "" });
    addTcRow(response, "TC(17,1)");
    showError("");
  } catch (error) {
    showError(`Ping failed: ${error.message}`);
  }
});

$("raw-form").addEventListener("submit", async (event) => {
  event.preventDefault();
  try {
    const response = await post("/api/tc", { hex: $("raw-hex").value.trim() });
    addTcRow(response, "raw");
    showError("");
  } catch (error) {
    showError(`Injection failed: ${error.message}`);
  }
});

// --- WebSocket TM/time/rejection stream (ICD §8.2) ---

function handleFrame(frame) {
  if (frame.kind === "time") {
    updateObt(frame.timeSeconds);
    return;
  }
  if (frame.kind === "rejection") {
    updateObt(frame.timeSeconds);
    addRow("REJ", frame.timeSeconds.toFixed(3), frame.reason, "", "", frame.hex,
        null, rejectionDetail(frame));
    return;
  }
  const decoded = frame.decoded;
  if (decoded) {
    updateObt(decoded.timeSeconds);
    addRow(
      "TM",
      decoded.timeSeconds.toFixed(3),
      `TM(${decoded.service},${decoded.subtype})`,
      String(decoded.sequenceCount),
      String(decoded.messageTypeCounter),
      frame.hex,
      decoded.service,
      tmDetail(frame),
    );
  } else {
    addRow("TM", "?", "undecodable", "", "", frame.hex, null, tmDetail(frame));
  }
}

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
  socket.onmessage = (event) => handleFrame(JSON.parse(event.data));
}

fillTypeSelect();
connectTm();
refreshPreview();
