// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLlmsTxt from 'starlight-llms-txt';

export default defineConfig({
  site: 'https://chromatik-mcp.vercel.app',
  integrations: [
    starlight({
      title: 'Chromatik MCP',
      description:
        'AI-accelerated light-show composition and performance for Chromatik, over MCP.',
      customCss: [
        '@fontsource/space-grotesk/500.css',
        '@fontsource/space-grotesk/700.css',
        '@fontsource/ibm-plex-mono/400.css',
        '@fontsource/ibm-plex-mono/600.css',
        './src/styles/custom.css',
      ],
      social: [
        {
          icon: 'github',
          label: 'GitHub',
          href: 'https://github.com/oveddan/chromatik-mcp',
        },
      ],
      plugins: [
        starlightLlmsTxt({
          projectName: 'chromatik-mcp',
          description:
            'A drop-in LX/Chromatik package for AI-driven show composition over MCP.',
          details:
            'chromatik-mcp embeds an HTTP MCP server inside the LX runtime. MCP clients discover the port from ~/.chromatik-mcp/status.json and call tools that mutate LX state in-process.',
        }),
      ],
      editLink: {
        baseUrl: 'https://github.com/oveddan/chromatik-mcp/edit/main/site/',
      },
      sidebar: [
        { label: 'Getting started', slug: 'getting-started' },
        { label: 'Connect your AI client', slug: 'connect' },
        { label: 'Usage examples', slug: 'examples' },
        { label: 'Tool reference', slug: 'tools' },
        { label: 'Architecture', slug: 'architecture' },
      ],
    }),
  ],
});
