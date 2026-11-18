"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { post, request, SIGNED_OUT_EVENT } from "../lib/api";

// Who is signed in, restored from the login cookie on page load via GET /auth/session.
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    request("/auth/session")
      .then(setUser)
      .catch(() => setUser(null)) // 401: nobody is signed in
      .finally(() => setReady(true));
    const signedOut = () => setUser(null);
    window.addEventListener(SIGNED_OUT_EVENT, signedOut);
    return () => window.removeEventListener(SIGNED_OUT_EVENT, signedOut);
  }, []);

  const login = useCallback(async (email, password, asProvider) => {
    const identity = await post(asProvider ? "/auth/provider/login" : "/auth/login", { email, password });
    setUser(identity);
    return identity;
  }, []);

  const logout = useCallback(async () => {
    try {
      await post("/auth/logout");
    } finally {
      setUser(null); // sign out in the UI even if the API could not be reached
    }
  }, []);

  return <AuthContext.Provider value={{ user, ready, login, logout }}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
