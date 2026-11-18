// Every call goes to /api/..., which next.config.mjs forwards to the Spring Boot API. The browser
// sends the HttpOnly login cookie automatically; page code never sees the token.
export async function request(path, options = {}) {
  // "/api" not "/api/": Next.js redirects a trailing slash, which would cost an extra round trip.
  const response = await fetch(path === "/" ? "/api" : `/api${path}`, {
    ...options,
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
  });
  const text = await response.text();
  let body = null;
  try {
    body = text ? JSON.parse(text) : null;
  } catch {
    body = null; // e.g. an HTML error page when the API is down
  }
  if (!response.ok) {
    const error = new Error(body?.error || (response.status >= 500
      ? "The booking API is not reachable right now." : `Request failed (${response.status}).`));
    error.status = response.status;
    throw error;
  }
  return body;
}

export const post = (path, data) =>
  request(path, { method: "POST", body: data === undefined ? undefined : JSON.stringify(data) });

export const when = (iso) => new Date(iso).toLocaleString([], { dateStyle: "medium", timeStyle: "short" });
export const until = (iso) => new Date(iso).toLocaleTimeString([], { hour: "numeric", minute: "2-digit" });
export const money = (amount) => `$${Number(amount).toFixed(2)}`;
