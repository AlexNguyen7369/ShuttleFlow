"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useAuth } from "./auth";

export default function Nav() {
  const { user, logout } = useAuth();
  const router = useRouter();

  async function signOut() {
    try {
      await logout();
    } catch {
      // logout() already cleared the user; nothing more to show
    }
    router.push("/");
  }

  return (
    <header className="topbar">
      <Link className="brand" href="/">ShuttleFlow</Link>
      <nav>
        <Link href="/browse">Browse</Link>
        {user?.role !== "PROVIDER" && <Link href="/bookings">My bookings</Link>}
        {user?.role !== "CUSTOMER" && <Link href="/provider">Provider</Link>}
        <Link href="/assistant">Assistant</Link>
        {!user && <Link href="/login">Sign in</Link>}
      </nav>
      <span className="muted">{user ? `${user.fullName} · ${user.role}` : "Not signed in"}</span>
      {user && <button onClick={signOut}>Log out</button>}
    </header>
  );
}
