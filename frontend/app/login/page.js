"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { useAuth } from "../auth";

export default function Login() {
  const { login } = useAuth();
  const router = useRouter();
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [asProvider, setAsProvider] = useState(false);
  const [error, setError] = useState("");

  async function submit(event) {
    event.preventDefault();
    setError("");
    try {
      const user = await login(email, password, asProvider);
      setPassword("");
      router.push(user.role === "PROVIDER" ? "/provider" : "/browse");
    } catch (err) {
      setError(err.message);
    }
  }

  return (
    <section className="view card narrow">
      <h2>Sign in</h2>
      <form onSubmit={submit}>
        <label htmlFor="email">Email
          <input id="email" type="email" required autoComplete="username" value={email}
            onChange={(e) => setEmail(e.target.value)} />
        </label>
        <label htmlFor="password">Password
          <input id="password" type="password" required autoComplete="current-password" value={password}
            onChange={(e) => setPassword(e.target.value)} />
        </label>
        <label className="check" htmlFor="provider-login">
          <input id="provider-login" type="checkbox" checked={asProvider}
            onChange={(e) => setAsProvider(e.target.checked)} /> Provider login
        </label>
        <button className="button">Sign in</button>
        <p className="error" role="alert">{error}</p>
      </form>
    </section>
  );
}
