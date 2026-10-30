import type { Metadata } from "next";
import { ArenasView } from "@/components/player/Discovery";
export const metadata: Metadata = { title: "Encontre sua quadra" };
export default async function Page({
  searchParams,
}: {
  searchParams: Promise<{ busca?: string }>;
}) {
  const { busca } = await searchParams;
  return <ArenasView initialSearch={typeof busca === "string" ? busca.slice(0, 120) : ""} />;
}
