// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

export default defineConfig({
  site: 'https://lx-mcp.vercel.app',
  integrations: [
    starlight({
      title: 'lx-mcp',
      description:
        'A drop-in LX/Chromatik package for AI-driven show composition over MCP.',
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/oveddan/lx-mcp',
        },
      ],
      editLink: {
        baseUrl: 'https://github.com/oveddan/lx-mcp/edit/main/site/',
      },
      sidebar: [
        { label: 'Getting started', slug: 'getting-started' },
        { label: 'Connect your AI client', slug: 'connect' },
        { label: 'Usage examples', slug: 'examples' },
        { label: 'Tool reference', slug: 'tools' },
        { label: 'Architecture', slug: 'architecture' },
        { label: 'How it was built', slug: 'how-it-was-built' },
      ],
    }),
  ],
});
