const apiBase = window.SHUTTLEFLOW_API || "http://localhost:8080";
const state = { page: 1, session: null };
const $ = (id) => document.getElementById(id);
const esc = (value) => String(value ?? "").replace(/[&<>"']/g, (c) => ({"&":"&amp;","<":"&lt;",">":"&gt;","\"":"&quot;","'":"&#39;"}[c]));
const request = async (path, options = {}) => {
  const response = await fetch(`${apiBase}${path}`, { credentials: "include", headers: { "Content-Type": "application/json", ...(options.headers || {}) }, ...options });
  const text = await response.text();
  const body = text ? JSON.parse(text) : null;
  if (!response.ok) throw new Error(body?.error || `Request failed (${response.status})`);
  return body;
};
const notify = (message) => { $("toast").textContent = message; $("toast").classList.add("show"); setTimeout(() => $("toast").classList.remove("show"), 2800); };
const requireLogin = () => { if (!state.session) { location.hash = "login"; notify("Sign in to continue."); return false; } return true; };

async function login(event) {
  event.preventDefault(); $("login-error").textContent = "";
  try {
    const path = $("provider-login").checked ? "/auth/provider/login" : "/auth/login";
    state.session = await request(path, { method: "POST", body: JSON.stringify({ email: $("email").value, password: $("password").value }) });
    updateIdentity(); notify(`Signed in as ${state.session.fullName}.`); location.hash = state.session.role === "PROVIDER" ? "provider" : "browse"; loadAppointments();
  } catch (error) { $("login-error").textContent = error.message; }
}
async function logout() { await request("/auth/logout", { method: "POST" }); state.session = null; updateIdentity(); notify("You are signed out."); }
function updateIdentity() { $("identity").textContent = state.session ? `${state.session.fullName} · ${state.session.role}` : "Not signed in"; $("logout").hidden = !state.session; }
async function loadSlots(event) {
  event?.preventDefault(); const params = new URLSearchParams({ page: state.page }); const type = $("session-type").value; const service = $("service-id").value; const date = $("slot-date").value;
  if (type) params.set("sessionType", type); if (service) params.set("serviceId", service); if (date) params.set("date", date);
  try { const slots = await request(`/slots?${params}`); $("slots").innerHTML = slots.length ? slots.map((slot) => `<article class="card slot"><strong>${esc(slot.serviceName)}</strong><span>${esc(slot.providerName)}</span><span>${esc(new Date(slot.startTime).toLocaleString())} – ${esc(new Date(slot.endTime).toLocaleTimeString([], {hour:"numeric", minute:"2-digit"}))}</span><span class="price">$${esc(slot.price)}</span><button class="button book" data-slot="${slot.slotId}">Book this slot</button></article>`).join("") : `<p class="muted">No open slots match those filters.</p>`; $("page-label").textContent = `Page ${state.page}`; $("previous").disabled = state.page === 1; $("browse-message").textContent = ""; document.querySelectorAll(".book").forEach((button) => button.addEventListener("click", () => book(Number(button.dataset.slot)))); } catch (error) { $("browse-message").textContent = error.message; }
}
async function book(slotId) { if (!requireLogin()) return; try { await request("/appointments", { method: "POST", body: JSON.stringify({ slotId }) }); notify("Booking confirmed."); loadSlots(); loadAppointments(); location.hash = "appointments"; } catch (error) { notify(error.message); } }
async function loadAppointments() { if (!state.session) { $("appointment-list").innerHTML = `<p class="muted">Sign in to view your bookings.</p>`; return; } try { const rows = await request(`/appointments?view=${$("appointment-view").value}`); $("appointment-list").innerHTML = rows.length ? rows.map((row) => `<article class="card appointment"><strong>${esc(row.serviceName)}</strong><span>${esc(row.providerName)}</span><span>${esc(new Date(row.startTime).toLocaleString())}</span><span>Status: ${esc(row.status)}</span>${row.status === "BOOKED" ? `<button class="cancel" data-id="${row.appointmentId}">Cancel</button>` : ""}</article>`).join("") : `<p class="muted">No bookings in this view.</p>`; document.querySelectorAll(".cancel").forEach((button) => button.addEventListener("click", () => cancelBooking(Number(button.dataset.id)))); } catch (error) { $("appointment-list").innerHTML = `<p class="error">${esc(error.message)}</p>`; } }
async function cancelBooking(id) { try { await request(`/appointments/${id}`, { method: "DELETE" }); notify("Booking cancelled."); loadAppointments(); loadSlots(); } catch (error) { notify(error.message); } }
async function createAvailability(event) { event.preventDefault(); try { await request("/provider/slots", { method: "POST", body: JSON.stringify({ serviceId: Number($("availability-service").value), startTime: $("availability-start").value, endTime: $("availability-end").value }) }); $("provider-message").textContent = "Slot created."; loadSlots(); } catch (error) { $("provider-message").textContent = error.message; } }
async function removeAvailability(event) { event.preventDefault(); try { await request(`/provider/slots/${Number($("remove-slot").value)}`, { method: "DELETE" }); $("remove-message").textContent = "Slot removed."; loadSlots(); } catch (error) { $("remove-message").textContent = error.message; } }
async function loadProviderAppointments() { if (!state.session || state.session.role !== "PROVIDER") { $("provider-appointments").innerHTML = `<p class="muted">Sign in with a provider account to view appointments.</p>`; return; } try { const rows = await request("/provider/appointments"); $("provider-appointments").innerHTML = rows.length ? rows.map((row) => `<p><strong>${esc(row.serviceName)}</strong><br>${esc(new Date(row.startTime).toLocaleString())}<br>${esc(row.customerName)} · ${esc(row.customerEmail)}</p>`).join("") : `<p class="muted">No booked appointments.</p>`; } catch (error) { $("provider-appointments").innerHTML = `<p class="error">${esc(error.message)}</p>`; } }

$("login-form").addEventListener("submit", login); $("logout").addEventListener("click", logout); $("filter-form").addEventListener("submit", (event) => { state.page = 1; loadSlots(event); }); $("appointment-view").addEventListener("change", loadAppointments); $("availability-form").addEventListener("submit", createAvailability); $("remove-form").addEventListener("submit", removeAvailability); $("previous").addEventListener("click", () => { if (state.page > 1) { state.page--; loadSlots(); } }); $("next").addEventListener("click", () => { state.page++; loadSlots(); });
window.addEventListener("hashchange", () => { if (location.hash === "#appointments") loadAppointments(); if (location.hash === "#provider") loadProviderAppointments(); });
updateIdentity(); loadSlots(); loadAppointments(); loadProviderAppointments();
