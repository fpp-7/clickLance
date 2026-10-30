import type { Metadata } from "next";
import { ReplayView } from "@/components/player/Replay";
export const metadata: Metadata = { title: "Seu lance" };
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ fuso?: string }>;
}) {
  const { id } = await params;
  const { fuso } = await searchParams;
  return <ReplayView id={id} zone={typeof fuso === "string" ? fuso : undefined} />;
}
