// Regenerates public/og.png (the social-share card). Run manually — `npm run og` —
// not part of the site build; the PNG is committed.
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import satori from 'satori';
import sharp from 'sharp';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));

const [display500, display700, mono400] = await Promise.all([
  readFile(here('../node_modules/@fontsource/space-grotesk/files/space-grotesk-latin-500-normal.woff')),
  readFile(here('../node_modules/@fontsource/space-grotesk/files/space-grotesk-latin-700-normal.woff')),
  readFile(here('../node_modules/@fontsource/ibm-plex-mono/files/ibm-plex-mono-latin-400-normal.woff')),
]);

const BG = '#08060f';
const AMBER = '#ffb347';
const MAGENTA = '#ff3fd8';
const CYAN = '#22d3ee';
const VIOLET = '#8b5cf6';

// The hero's LED-dot field, flattened to explicit dots — satori has no
// background-image/mask support.
const dots = [];
for (let y = -40; y < 700; y += 42) {
  for (let x = -20; x < 1240; x += 42) {
    // Bright cluster off the right edge, falling away behind the copy.
    const d = Math.hypot((x - 1180) / 700, (y - 315) / 430);
    const opacity = Math.max(0, 0.62 - d * 0.62);
    if (opacity < 0.02) continue;
    const color = [VIOLET, CYAN, MAGENTA][(((x / 42) | 0) + ((y / 42) | 0) * 2) % 3];
    dots.push({
      type: 'div',
      props: {
        style: {
          position: 'absolute',
          left: x,
          top: y,
          width: 4,
          height: 4,
          borderRadius: 2,
          background: color,
          opacity,
        },
      },
    });
  }
}

const markup = {
  type: 'div',
  props: {
    style: {
      width: 1200,
      height: 630,
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      background: BG,
      fontFamily: 'Space Grotesk',
      padding: '0 84px',
      position: 'relative',
    },
    children: [
      ...dots,
      {
        type: 'div',
        props: {
          style: {
            fontSize: 20,
            fontFamily: 'IBM Plex Mono',
            letterSpacing: '0.22em',
            textTransform: 'uppercase',
            color: CYAN,
            marginBottom: 26,
          },
          children: 'Model Context Protocol × LX',
        },
      },
      {
        type: 'div',
        props: {
          style: {
            fontSize: 92,
            fontWeight: 700,
            letterSpacing: '-0.03em',
            lineHeight: 1.05,
            // shrink-to-fit so the gradient spans the glyphs, not the column
            alignSelf: 'flex-start',
            backgroundImage: `linear-gradient(100deg, ${AMBER} 0%, ${MAGENTA} 45%, ${CYAN} 100%)`,
            backgroundClip: 'text',
            color: 'transparent',
          },
          children: 'Chromatik MCP',
        },
      },
      {
        type: 'div',
        props: {
          style: {
            fontSize: 34,
            fontWeight: 500,
            lineHeight: 1.35,
            color: '#c9c2e4',
            marginTop: 26,
            maxWidth: 900,
          },
          children:
            'An agent inside the Chromatik runtime — it parses your scene, explains how it is wired, and composes into it — long before the doors open.',
        },
      },
      {
        type: 'div',
        props: {
          style: {
            display: 'flex',
            alignItems: 'center',
            marginTop: 44,
            fontSize: 22,
            fontFamily: 'IBM Plex Mono',
            color: '#7d74a6',
          },
          children: [
            {
              type: 'div',
              props: {
                style: {
                  width: 10,
                  height: 10,
                  borderRadius: 5,
                  background: MAGENTA,
                  marginRight: 14,
                },
              },
            },
            { type: 'div', props: { children: 'oveddan.github.io/chromatik-mcp' } },
          ],
        },
      },
    ],
  },
};

const svg = await satori(markup, {
  width: 1200,
  height: 630,
  fonts: [
    { name: 'Space Grotesk', data: display500, weight: 500, style: 'normal' },
    { name: 'Space Grotesk', data: display700, weight: 700, style: 'normal' },
    { name: 'IBM Plex Mono', data: mono400, weight: 400, style: 'normal' },
  ],
});

await writeFile(here('../public/og.png'), await sharp(Buffer.from(svg)).png().toBuffer());
console.log('wrote site/public/og.png');
