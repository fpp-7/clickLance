import type { Metadata } from "next";
import { LibraryView } from "@/components/player/Library";
export const metadata: Metadata = { title: "Minha biblioteca" };
export default function Page() {
  return <LibraryView />;
}
