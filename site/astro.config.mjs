// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLlmsTxt from 'starlight-llms-txt';

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
      plugins: [
        starlightLlmsTxt({
          projectName: 'lx-mcp',
          description:
            'A drop-in LX/Chromatik package for AI-driven show composition over MCP.',
          details:
            'lx-mcp embeds an HTTP MCP server inside the LX runtime. MCP clients discover the port from ~/.lx-mcp/status.json and call tools that mutate LX state in-process.',
        }),
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
