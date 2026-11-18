"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { request } from "../lib/api";

export default function Home() {
  const [count, setCount] = useState("");

  useEffect(() => {
    request("/")
      .then((home) => setCount(`${home.openSlotCount} open slots right now.`))
      .catch(() => setCount("The booking API is not reachable. Start it with mvn spring-boot:run."));
  }, []);

  return (
    <section className="view hero">
      <p className="eyebrow">Badminton court and coach booking</p>
      <h1>Find your next court time or coaching session.</h1>
      <p>Browse open slots, reserve one securely, manage your bookings, or just ask the assistant.</p>
      <p className="muted small">{count}</p>
      <div className="row-actions">
        <Link className="button" href="/browse">Browse available slots</Link>
        <Link className="button secondary" href="/assistant">Ask the assistant</Link>
      </div>
    </section>
  );
}
