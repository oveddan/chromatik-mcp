// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import starlightLlmsTxt from 'starlight-llms-txt';

const SITE = 'https://oveddan.github.io';
const BASE = '/chromatik-mcp';
const OG_IMAGE = `${SITE}${BASE}/og.png`;

export default defineConfig({
  site: SITE,
  base: BASE,
  integrations: [
    starlight({
      title: 'Chromatik MCP',
      description:
        'AI-accelerated light-show composition and performance for Chromatik, over MCP.',
      // Starlight emits og:title/description/url per page; the share image and
      // the large-card opt-in are ours. Absolute URLs — crawlers don't resolve
      // relative ones.
      head: [
        { tag: 'meta', attrs: { property: 'og:image', content: OG_IMAGE } },
        { tag: 'meta', attrs: { property: 'og:image:width', content: '1200' } },
        { tag: 'meta', attrs: { property: 'og:image:height', content: '630' } },
        {
          tag: 'meta',
          attrs: {
            property: 'og:image:alt',
            content: 'Chromatik MCP — AI-accelerated light-show composition and performance.',
          },
        },
        { tag: 'meta', attrs: { property: 'og:site_name', content: 'Chromatik MCP' } },
        { tag: 'meta', attrs: { name: 'twitter:card', content: 'summary_large_image' } },
        { tag: 'meta', attrs: { name: 'twitter:image', content: OG_IMAGE } },
      ],
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
