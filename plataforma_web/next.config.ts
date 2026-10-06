import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // O Next gera AGENTS.md/CLAUDE.md sozinho no dev; não queremos esses arquivos no repositório
  agentRules: false,
  images: {
    remotePatterns: [
      // Vídeos e thumbnails públicos de exemplo usados pelo mock
      { protocol: "https", hostname: "storage.googleapis.com" },
    ],
  },
};

export default nextConfig;
