// ShuttleFlow browser client. Every action is one fetch to the Spring Boot API:
// UI → Controller → Service → Repository → Database → DTO JSON → UI.
// The session lives server-side; the browser only carries the JSESSIONID cookie
// (credentials: "include"), so the UI never sees or stores a password or token.

const apiBase = window.SHUTTLEFLOW_API || "http://localhost:8080";
const state = { page: 1, session: null, pendingSlot: null };
const $ = (id) => document.getElementById(id);

// ---------- helpers ----------

const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => (
  { "&": "&amp;", "<": "&lt;", ">": "&gt;", "\"": "&quot;", "'": "&#39;" }[c]));
const when = (iso) => new Date(iso).toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
const money = (amount) => `$${Number(amount).toFixed(2)}`;
const until = (iso) => new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });

async function request(path, options = {}) {
  const response = await fetch(`${apiBase}${path}`, {
    credentials: "include",
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) {
    const error = new Error(body?.error || `Request failed (${response.status})`);
    error.status = response.status;
    throw error;
  }
  return body;
}

function notify(message) {
  $("toast").textContent = message;
  $("toast").classList.add("show");
  setTimeout(() => $("toast").classList.remove("show"), 2800);
}

const isRole = (role) => state.session?.role === role;

// ---------- session ----------

function updateIdentity() {
  const s = state.session;
  $("identity").textContent = s ? `${s.fullName} · ${s.role}` : "Not signed in";
  $("logout").hidden = !s;
  $("login-link").hidden = Boolean(s);
  document.querySelectorAll("[data-role]").forEach((link) => {
    link.hidden = Boolean(s) && link.dataset.role !== s.role;
  });
}

async function restoreSession() {
  try {
    state.session = await request("/auth/session");
  } catch {
    state.session = null; // 401: nobody is signed in yet
  }
  updateIdentity();
}

async function login(event) {
  event.preventDefault();
  $("login-error").textContent = "";
  const path = $("provider-login").checked ? "/auth/provider/login" : "/auth/login";
  try {
    state.session = await request(path, {
      method: "POST",
      body: JSON.stringify({ email: $("email").value, password: $("password").value }),
    });
    $("password").value = "";
    updateIdentity();
    notify(`Signed in as ${state.session.fullName}.`);
    location.hash = isRole("PROVIDER") ? "provider" : "browse";
    refreshAll();
  } catch (error) {
    $("login-error").textContent = error.message;
  }
}

async function logout() {
  await request("/auth/logout", { method: "POST" });
  state.session = null;
  updateIdentity();
  notify("You are signed out.");
  location.hash = "home";
  refreshAll();
}

// ---------- browse, filter, paginate ----------

async function loadSlots() {
  const params = new URLSearchParams({ page: state.page });
  const filters = { sessionType: "session-type", providerId: "provider-filter", serviceId: "service-id", date: "slot-date" };
  Object.entries(filters).forEach(([param, id]) => { if ($(id).value) params.set(param, $(id).value); });
  try {
    const slots = await request(`/slots?${params}`);
    $("slots").innerHTML = slots.length ? slots.map(slotCard).join("")
      : `<p class="muted">No open slots match those filters.</p>`;
    $("page-label").textContent = `Page ${state.page}`;
    $("previous").disabled = state.page === 1;
    $("next").disabled = slots.length < 10; // the API pages 10 at a time with SQL LIMIT/OFFSET
    $("browse-message").textContent = "";
    document.querySelectorAll(".book").forEach((button) => button.addEventListener("click",
      () => openBooking(slots.find((slot) => slot.slotId === Number(button.dataset.slot)))));
  } catch (error) {
    $("browse-message").textContent = error.message;
  }
}

const slotCard = (slot) => `
  <article class="card slot">
    <strong>${esc(slot.serviceName)}</strong>
    <span>${esc(slot.providerName)}</span>
    <span>${esc(when(slot.startTime))} – ${esc(until(slot.endTime))}</span>
    <span class="price">${esc(money(slot.price))}</span>
    <small class="muted">Slot #${esc(slot.slotId)}</small>
    ${isRole("PROVIDER") ? "" : `<button class="button book" data-slot="${slot.slotId}">Book this slot</button>`}
  </article>`;

// ---------- booking + confirmation ----------

function openBooking(slot) {
  if (!state.session) {
    location.hash = "login";
    notify("Sign in to book a slot.");
    return;
  }
  state.pendingSlot = slot;
  $("booking-error").textContent = "";
  $("booking-confirm").hidden = false;
  $("booking-body").innerHTML = `
    <p class="eyebrow">Confirm your booking</p>
    <h3>${esc(slot.serviceName)}</h3>
    <p>${esc(slot.providerName)}<br>${esc(when(slot.startTime))} – ${esc(until(slot.endTime))}</p>
    <p class="price">${esc(money(slot.price))}</p>`;
  $("booking-dialog").showModal();
}

async function confirmBooking() {
  const slot = state.pendingSlot;
  try {
    const booking = await request("/appointments", { method: "POST", body: JSON.stringify({ slotId: slot.slotId }) });
    $("booking-confirm").hidden = true;
    $("booking-body").innerHTML = `
      <p class="eyebrow">Booking confirmed</p>
      <h3>${esc(booking.serviceName)}</h3>
      <p>${esc(booking.providerName)}<br>${esc(when(booking.startTime))} – ${esc(until(booking.endTime))}</p>
      <p>Confirmation #${esc(booking.appointmentId)} · Status ${esc(booking.status)}</p>`;
    loadSlots();
    loadAppointments();
  } catch (error) {
    // 409 means another player won the race for this slot.
    $("booking-error").textContent = error.message;
    loadSlots();
  }
}

// ---------- customer appointments ----------

async function loadAppointments() {
  if (!isRole("CUSTOMER")) {
    $("appointment-list").innerHTML = `<p class="muted">Sign in with a customer account to view your bookings.</p>`;
    return;
  }
  try {
    const rows = await request(`/appointments?view=${$("appointment-view").value}`);
    $("appointment-list").innerHTML = rows.length ? rows.map((row) => `
      <article class="card appointment">
        <strong>${esc(row.serviceName)}</strong>
        <span>${esc(row.providerName)}</span>
        <span>${esc(when(row.startTime))} – ${esc(until(row.endTime))}</span>
        <span class="status status-${esc(row.status.toLowerCase())}">${esc(row.status)}</span>
        ${row.status === "BOOKED" ? `<button class="cancel" data-id="${row.appointmentId}">Cancel booking</button>` : ""}
      </article>`).join("") : `<p class="muted">No bookings in this view.</p>`;
    document.querySelectorAll(".cancel").forEach((button) => button.addEventListener("click",
      () => cancelBooking(Number(button.dataset.id))));
  } catch (error) {
    $("appointment-list").innerHTML = `<p class="error">${esc(error.message)}</p>`;
  }
}

async function cancelBooking(id) {
  if (!confirm("Cancel this booking? The slot will be released to other players.")) return;
  try {
    await request(`/appointments/${id}`, { method: "DELETE" });
    notify("Booking cancelled.");
    loadAppointments();
    loadSlots();
  } catch (error) {
    notify(error.message);
  }
}

// ---------- provider workspace ----------

async function loadProviderWorkspace() {
  if (!isRole("PROVIDER")) {
    const message = `<p class="muted">Sign in with a provider account to manage availability.</p>`;
    $("provider-appointments").innerHTML = message;
    $("provider-slots").innerHTML = message;
    $("availability-service").innerHTML = "";
    return;
  }
  try {
    const [services, slots, bookings] = await Promise.all([
      request("/provider/services"),
      loadAllProviderSlots(state.session.providerId),
      request("/provider/appointments"),
    ]);
    $("availability-service").innerHTML = services.map((s) =>
      `<option value="${s.serviceId}">${esc(s.name)} (${s.durationMin} min · ${esc(money(s.price))})</option>`).join("");
    $("provider-slots").innerHTML = slots.length ? slots.map((slot) => `
      <div class="row">
        <span><b>${esc(slot.serviceName)}</b><br><small>${esc(when(slot.startTime))} · Slot #${slot.slotId}</small></span>
        <button class="remove" data-slot="${slot.slotId}">Remove</button>
      </div>`).join("") : `<p class="muted">No upcoming open slots.</p>`;
    document.querySelectorAll(".remove").forEach((button) => button.addEventListener("click",
      () => removeAvailability(Number(button.dataset.slot))));
    $("provider-appointments").innerHTML = bookings.length ? bookings.map((row) => `
      <div class="row">
        <span><b>${esc(row.serviceName)}</b><br><small>${esc(when(row.startTime))} · Slot #${row.slotId}</small></span>
        <span>${esc(row.customerName)}<br><small>${esc(row.customerEmail)}</small></span>
      </div>`).join("") : `<p class="muted">No booked appointments.</p>`;
  } catch (error) {
    $("provider-appointments").innerHTML = `<p class="error">${esc(error.message)}</p>`;
  }
}

// GET /slots pages 10 at a time; walk the pages so providers can manage every open slot.
async function loadAllProviderSlots(providerId) {
  const all = [];
  for (let page = 1; ; page++) {
    const slots = await request(`/slots?providerId=${providerId}&page=${page}`);
    all.push(...slots);
    if (slots.length < 10) return all;
  }
}

async function createAvailability(event) {
  event.preventDefault();
  try {
    const slot = await request("/provider/slots", {
      method: "POST",
      body: JSON.stringify({
        serviceId: Number($("availability-service").value),
        startTime: $("availability-start").value,
        endTime: $("availability-end").value,
      }),
    });
    $("provider-message").textContent = `Slot #${slot.slotId} created for ${when(slot.startTime)}.`;
    loadProviderWorkspace();
    loadSlots();
  } catch (error) {
    $("provider-message").textContent = error.message;
  }
}

async function removeAvailability(slotId) {
  try {
    await request(`/provider/slots/${slotId}`, { method: "DELETE" });
    $("remove-message").textContent = `Slot #${slotId} removed.`;
    loadProviderWorkspace();
    loadSlots();
  } catch (error) {
    $("remove-message").textContent = error.message; // e.g. 409 when the slot is booked
  }
}

// ---------- wiring ----------

async function loadHome() {
  try {
    const home = await request("/");
    $("home-count").textContent = `${home.openSlotCount} open slots right now.`;
  } catch {
    $("home-count").textContent = "The booking API is not reachable on " + apiBase + ".";
  }
}

function refreshAll() {
  loadHome();
  loadSlots();
  loadAppointments();
  loadProviderWorkspace();
}

$("login-form").addEventListener("submit", login);
$("logout").addEventListener("click", logout);
$("filter-form").addEventListener("submit", (event) => { event.preventDefault(); state.page = 1; loadSlots(); });
$("appointment-view").addEventListener("change", loadAppointments);
$("availability-form").addEventListener("submit", createAvailability);
$("previous").addEventListener("click", () => { if (state.page > 1) { state.page--; loadSlots(); } });
$("next").addEventListener("click", () => { state.page++; loadSlots(); });
$("booking-confirm").addEventListener("click", confirmBooking);
$("booking-cancel").addEventListener("click", () => $("booking-dialog").close());
window.addEventListener("hashchange", () => {
  if (location.hash === "#appointments") loadAppointments();
  if (location.hash === "#provider") loadProviderWorkspace();
});

restoreSession().then(refreshAll);
