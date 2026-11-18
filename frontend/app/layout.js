import "./globals.css";
import { AuthProvider } from "./auth";
import Nav from "./nav";

export const metadata = {
  title: "ShuttleFlow",
  description: "Badminton court and coach booking",
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>
          <Nav />
          <main>{children}</main>
        </AuthProvider>
      </body>
    </html>
  );
}
