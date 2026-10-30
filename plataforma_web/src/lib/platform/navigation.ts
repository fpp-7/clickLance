/** Keep sign-in return URLs on known player routes, including after URL normalization. */
export function safePlayerReturn(value: string | null): string {
  if (
    !value ||
    Array.from(value).some((c) => c.charCodeAt(0) <= 32 || c.charCodeAt(0) === 127 || c === "\\")
  )
    return "/arenas";
  try {
    const base = "https://clicklance.invalid";
    const url = new URL(value, base);
    if (!value.startsWith("/") || url.origin !== base) return "/arenas";
    if (
      !/^\/(?:arenas(?:\/[A-Za-z0-9_-]+)?|quadras\/[A-Za-z0-9_-]+|lances\/[a-fA-F0-9]{32}|biblioteca|conta)\/?$/.test(
        url.pathname,
      )
    )
      return "/arenas";
    return url.pathname + url.search + url.hash;
  } catch {
    return "/arenas";
  }
}
