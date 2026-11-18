"use client";

import { useCallback, useEffect, useState } from "react";
import { request, until, when } from "../../lib/api";
import { useAuth } from "../auth";

export default function Bookings() {
  const { user, ready } = useAuth();
  const [view, setView] = useState("upcoming");
  const [rows, setRows] = useState([]);
  const [error, setError] = useState("");
  const [cancelling, setCancelling] = useState(null);

  const load = useCallback(async () => {
    try {
      setRows(await request(`/appointments?view=${view}`));
      setError("");
    } catch (err) {
      setError(err.message);
    }
  }, [view]);

  useEffect(() => { if (user?.role === "CUSTOMER") load(); }, [user, load]);

  async function cancel(id) {
    try {
      await request(`/appointments/${id}`, { method: "DELETE" });
      setCancelling(null);
      load();
    } catch (err) {
      setError(err.message);
    }
  }

  if (ready && user?.role !== "CUSTOMER") {
    return <section className="view"><p className="muted">Sign in with a customer account to view your bookings.</p></section>;
  }

  return (
    <section className="view">
      <div className="section-head">
        <div><p className="eyebrow">Your account</p><h2>My bookings</h2></div>
        <select id="appointment-view" aria-label="Booking view" value={view} onChange={(e) => setView(e.target.value)}>
          <option value="upcoming">Upcoming</option>
          <option value="history">History</option>
        </select>
      </div>
      <p className="error" role="alert">{error}</p>
      <div className="grid">
        {rows.length === 0 && <p className="muted">No bookings in this view.</p>}
        {rows.map((row) => (
          <article className="card appointment" key={row.appointmentId}>
            <strong>{row.serviceName}</strong>
            <span>{row.providerName}</span>
            <span>{when(row.startTime)} – {until(row.endTime)}</span>
            <span className={`status status-${row.status.toLowerCase()}`}>{row.status}</span>
            {row.status === "BOOKED" && cancelling !== row.appointmentId && (
              <button onClick={() => setCancelling(row.appointmentId)}>Cancel booking</button>
            )}
            {cancelling === row.appointmentId && (
              <div className="confirm-row">
                <span className="small">The slot will be released to other players.</span>
                <button onClick={() => setCancelling(null)}>Keep it</button>
                <button className="button danger" onClick={() => cancel(row.appointmentId)}>Yes, cancel</button>
              </div>
            )}
          </article>
        ))}
      </div>
    </section>
  );
}
