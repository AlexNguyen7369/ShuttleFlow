// The browser only talks to this Next.js server. /api/* is forwarded to the Spring Boot API, so the
// login cookie (HttpOnly JWT) is first-party and no CORS is involved.
const apiUrl = process.env.API_URL || "http://localhost:8080";

/** @type {import('next').NextConfig} */
const nextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/:path*` }];
  },
};

export default nextConfig;
