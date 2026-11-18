"use client";

import { createContext, useCallback, useContext, useEffect, useState } from "react";
import { post, request } from "../lib/api";

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
  }, []);

  const login = useCallback(async (email, password, asProvider) => {
    const identity = await post(asProvider ? "/auth/provider/login" : "/auth/login", { email, password });
    setUser(identity);
    return identity;
  }, []);

  const logout = useCallback(async () => {
    await post("/auth/logout");
    setUser(null);
  }, []);

  return <AuthContext.Provider value={{ user, ready, login, logout }}>{children}</AuthContext.Provider>;
}

export const useAuth = () => useContext(AuthContext);
