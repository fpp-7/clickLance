import type { Metadata } from "next";
import { ArenaView } from "@/components/player/Discovery";
export const metadata: Metadata = { title: "Escolha sua quadra" };
export default async function Page({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>;
  searchParams: Promise<{ nome?: string }>;
}) {
  const { slug } = await params;
  const { nome } = await searchParams;
  return <ArenaView id={slug} name={typeof nome === "string" ? nome : undefined} />;
}
