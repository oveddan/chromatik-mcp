// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

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
      editLink: {
        baseUrl: 'https://github.com/oveddan/chromatik-mcp/edit/main/site/',
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
