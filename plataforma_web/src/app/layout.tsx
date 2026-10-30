import type { Metadata, Viewport } from "next";
import { Barlow_Condensed, Manrope } from "next/font/google";
import "./globals.css";
import { Provedores } from "./provedores";

const manrope = Manrope({
  subsets: ["latin"],
  variable: "--font-manrope",
  display: "swap",
});

const barlow = Barlow_Condensed({
  subsets: ["latin"],
  weight: ["500", "600", "700"],
  variable: "--font-barlow",
  display: "swap",
});

const siteUrl = process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000";

export const metadata: Metadata = {
  metadataBase: new URL(siteUrl),
  title: {
    default: "Click Lance",
    template: "%s · Click Lance",
  },
  description:
    "O jogo acaba. O lance fica. Encontre sua quadra, reveja suas jogadas e guarde seus melhores momentos.",
  applicationName: "Click Lance",
};

export const viewport: Viewport = {
  themeColor: "#f3f6f5",
  colorScheme: "light",
  width: "device-width",
  initialScale: 1,
  viewportFit: "cover",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="pt-BR" className={`${manrope.variable} ${barlow.variable} h-full antialiased`}>
      <body className="flex min-h-full flex-col">
        <Provedores>{children}</Provedores>
      </body>
    </html>
  );
}
