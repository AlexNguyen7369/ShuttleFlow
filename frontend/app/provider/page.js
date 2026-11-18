"use client";

import { useCallback, useEffect, useState } from "react";
import { money, post, request, when } from "../../lib/api";
import { useAuth } from "../auth";

// GET /slots pages 10 at a time; walk the pages so providers can manage every open slot.
async function loadAllSlots(providerId) {
  const all = [];
  for (let page = 1; ; page++) {
    const slots = await request(`/slots?providerId=${providerId}&page=${page}`);
    all.push(...slots);
    if (slots.length < 10) return all;
  }
}

export default function Provider() {
  const { user, ready } = useAuth();
  const [services, setServices] = useState([]);
  const [slots, setSlots] = useState([]);
  const [bookings, setBookings] = useState([]);
  const [form, setForm] = useState({ serviceId: "", startTime: "", endTime: "" });
  const [message, setMessage] = useState("");
  const [removeMessage, setRemoveMessage] = useState("");
  const [reminders, setReminders] = useState("");
  const [error, setError] = useState("");

  const load = useCallback(async () => {
    try {
      const [own, open, booked] = await Promise.all([
        request("/provider/services"), loadAllSlots(user.providerId), request("/provider/appointments"),
      ]);
      setServices(own);
      setSlots(open);
      setBookings(booked);
      setForm((current) => ({ ...current, serviceId: current.serviceId || String(own[0]?.serviceId || "") }));
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, [user]);

  useEffect(() => { if (user?.role === "PROVIDER") load(); }, [user, load]);

  async function create(event) {
    event.preventDefault();
    try {
      const slot = await post("/provider/slots", { ...form, serviceId: Number(form.serviceId) });
      setMessage(`Slot #${slot.slotId} created for ${when(slot.startTime)}.`);
      load();
    } catch (err) {
      setMessage(err.message);
    }
  }

  async function remove(slotId) {
    try {
      await request(`/provider/slots/${slotId}`, { method: "DELETE" });
      setRemoveMessage(`Slot #${slotId} removed.`);
      load();
    } catch (err) {
      setRemoveMessage(err.message); // e.g. 409 when the slot is booked
    }
  }

  async function runReminders() {
    try {
      const run = await post("/ai/reminders/run");
      setReminders(`Scanned ${run.scanned}, queued ${run.queued} (${run.fallbacks} used the template).`);
    } catch (err) {
      setReminders(err.message);
    }
  }

  if (ready && user?.role !== "PROVIDER") {
    return <section className="view"><p className="muted">Sign in with a provider account to manage availability.</p></section>;
  }

  return (
    <section className="view">
      <div className="section-head">
        <div><p className="eyebrow">Provider workspace</p><h2>Manage availability</h2></div>
      </div>
      <p className="error" role="alert">{error}</p>
      <div className="provider-grid">
        <div className="stack">
          <form className="card" onSubmit={create}>
            <h3>Add an open slot</h3>
            <label htmlFor="availability-service">Service
              <select id="availability-service" required value={form.serviceId}
                onChange={(e) => setForm({ ...form, serviceId: e.target.value })}>
                {services.map((s) => (
                  <option key={s.serviceId} value={s.serviceId}>{s.name} ({s.durationMin} min · {money(s.price)})</option>
                ))}
              </select>
            </label>
            <label htmlFor="availability-start">Start
              <input id="availability-start" type="datetime-local" required value={form.startTime}
                onChange={(e) => setForm({ ...form, startTime: e.target.value })} />
            </label>
            <label htmlFor="availability-end">End
              <input id="availability-end" type="datetime-local" required value={form.endTime}
                onChange={(e) => setForm({ ...form, endTime: e.target.value })} />
            </label>
            <button className="button">Create slot</button>
            <p className="muted" role="status">{message}</p>
          </form>
          <div className="card">
            <h3>My open slots</h3>
            {slots.length === 0 && <p className="muted">No upcoming open slots.</p>}
            {slots.map((slot) => (
              <div className="row" key={slot.slotId}>
                <span><b>{slot.serviceName}</b><br /><small>{when(slot.startTime)} · Slot #{slot.slotId}</small></span>
                <button onClick={() => remove(slot.slotId)}>Remove</button>
              </div>
            ))}
            <p className="muted" role="status">{removeMessage}</p>
          </div>
        </div>
        <div className="stack">
          <div className="card">
            <h3>Booked appointments</h3>
            {bookings.length === 0 && <p className="muted">No booked appointments.</p>}
            {bookings.map((row) => (
              <div className="row" key={row.appointmentId}>
                <span><b>{row.serviceName}</b><br /><small>{when(row.startTime)} · Slot #{row.slotId}</small></span>
                <span>{row.customerName}<br /><small>{row.customerEmail}</small></span>
              </div>
            ))}
          </div>
          <div className="card">
            <h3>Reminder agent</h3>
            <p className="muted small">Runs every 15 minutes; run it now to remind customers whose appointments start within 24 hours.</p>
            <button onClick={runReminders}>Run reminders now</button>
            <p className="muted" role="status">{reminders}</p>
          </div>
        </div>
      </div>
    </section>
  );
}
