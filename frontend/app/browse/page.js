"use client";

import { useRouter } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { money, post, request, until, when } from "../../lib/api";
import { useAuth } from "../auth";

const PAGE_SIZE = 10; // the API pages 10 at a time with SQL LIMIT/OFFSET

export default function Browse() {
  const { user } = useAuth();
  const router = useRouter();
  const [filters, setFilters] = useState({ sessionType: "", providerId: "", serviceId: "", date: "" });
  const [page, setPage] = useState(1);
  const [slots, setSlots] = useState([]);
  const [message, setMessage] = useState("");
  const [pending, setPending] = useState(null);   // slot chosen, waiting for confirmation
  const [booked, setBooked] = useState(null);     // stored booking returned by the API
  const [bookingError, setBookingError] = useState("");
  const dialog = useRef(null);

  const load = useCallback(async (pageNumber, current) => {
    const params = new URLSearchParams({ page: pageNumber });
    Object.entries(current).forEach(([key, value]) => { if (value) params.set(key, value); });
    try {
      setSlots(await request(`/slots?${params}`));
      setMessage("");
    } catch (error) {
      setMessage(error.message);
    }
  }, []);

  useEffect(() => { load(page, filters); }, [page]); // eslint-disable-line react-hooks/exhaustive-deps

  function applyFilters(event) {
    event.preventDefault();
    setPage(1);
    load(1, filters);
  }

  function openBooking(slot) {
    if (!user) {
      router.push("/login");
      return;
    }
    setPending(slot);
    setBooked(null);
    setBookingError("");
    dialog.current.showModal();
  }

  async function confirm() {
    try {
      setBooked(await post("/appointments", { slotId: pending.slotId }));
      load(page, filters);
    } catch (error) {
      // 409 means another player won the race for this slot.
      setBookingError(error.message);
      load(page, filters);
    }
  }

  const field = (key) => ({ value: filters[key], onChange: (e) => setFilters({ ...filters, [key]: e.target.value }) });

  return (
    <section className="view">
      <div className="section-head">
        <div><p className="eyebrow">Availability</p><h2>Open slots</h2></div>
        <form className="filters" onSubmit={applyFilters}>
          <select id="session-type" aria-label="Session type" {...field("sessionType")}>
            <option value="">All types</option>
            <option value="OPEN_PLAY">Open play</option>
            <option value="COACHING">Coaching</option>
          </select>
          <select id="provider-filter" aria-label="Provider" {...field("providerId")}>
            <option value="">All providers</option>
            <option value="1">Court 3</option>
            <option value="2">Coach Kim</option>
          </select>
          <input id="service-id" type="number" min="1" placeholder="Service ID" aria-label="Service ID" {...field("serviceId")} />
          <input id="slot-date" type="date" aria-label="Date" {...field("date")} />
          <button className="button">Filter</button>
        </form>
      </div>
      <p className="muted">{message}</p>
      <div className="grid">
        {slots.length === 0 && !message && <p className="muted">No open slots match those filters.</p>}
        {slots.map((slot) => (
          <article className="card slot" key={slot.slotId}>
            <strong>{slot.serviceName}</strong>
            <span>{slot.providerName}</span>
            <span>{when(slot.startTime)} – {until(slot.endTime)}</span>
            <span className="price">{money(slot.price)}</span>
            <small className="muted">Slot #{slot.slotId}</small>
            {user?.role !== "PROVIDER" && <button className="button" onClick={() => openBooking(slot)}>Book this slot</button>}
          </article>
        ))}
      </div>
      <div className="pager">
        <button disabled={page === 1} onClick={() => setPage(page - 1)}>Previous</button>
        <span>Page {page}</span>
        <button disabled={slots.length < PAGE_SIZE} onClick={() => setPage(page + 1)}>Next</button>
      </div>

      {/* Step 1 confirms the choice, step 2 shows the stored booking. */}
      <dialog ref={dialog} className="card">
        {pending && !booked && (
          <div>
            <p className="eyebrow">Confirm your booking</p>
            <h3>{pending.serviceName}</h3>
            <p>{pending.providerName}<br />{when(pending.startTime)} – {until(pending.endTime)}</p>
            <p className="price">{money(pending.price)}</p>
          </div>
        )}
        {booked && (
          <div>
            <p className="eyebrow">Booking confirmed</p>
            <h3>{booked.serviceName}</h3>
            <p>{booked.providerName}<br />{when(booked.startTime)} – {until(booked.endTime)}</p>
            <p>Confirmation #{booked.appointmentId} · Status {booked.status}</p>
          </div>
        )}
        <p className="error" role="alert">{bookingError}</p>
        <div className="dialog-actions">
          <button type="button" onClick={() => dialog.current.close()}>Close</button>
          {!booked && !bookingError && <button className="button" type="button" onClick={confirm}>Confirm booking</button>}
        </div>
      </dialog>
    </section>
  );
}
