import type { Metadata } from "next";
import { CourtView } from "@/components/player/Court";
export const metadata: Metadata = { title: "Lances da quadra" };
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ nome?: string; arena?: string }>;
}) {
  const { id } = await params;
  const { nome, arena } = await searchParams;
  return (
    <CourtView
      id={id}
      name={typeof nome === "string" ? nome : undefined}
      arena={typeof arena === "string" ? arena : undefined}
    />
  );
}
