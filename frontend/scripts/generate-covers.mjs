// Generates the book cover SVGs in public/covers/ (original artwork drawn from simple shapes; no third-party images).
// Each of the 60 seeded books gets a front cover with an illustration that suits its title, its author and a palette
// for its genre, and a back cover with the description and publisher. The titles, authors and descriptions are read
// from the seed script so the covers never drift from the catalogue.
// Usage: node scripts/generate-covers.mjs        (run from the frontend folder)
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const outDir = join(here, '..', 'public', 'covers');
const seedPath = join(here, '..', '..', 'backend', 'src', 'main', 'resources', 'db', 'seed', 'R__seed_books.sql');

// ---- catalogue ------------------------------------------------------------------------------------------------
const unquote = (s) => s.replace(/''/g, "'");
const rowPattern = /^\((\d+), '((?:[^']|'')*)', '((?:[^']|'')*)', '((?:[^']|'')*)', '((?:[^']|'')*)'/gm;
const books = [];
for (const m of readFileSync(seedPath, 'utf8').matchAll(rowPattern)) {
  books.push({ id: Number(m[1]), title: unquote(m[2]), author: unquote(m[3]), description: unquote(m[4]), publisher: unquote(m[5]) });
}
if (books.length !== 60) {
  console.error(`Expected 60 books in the seed script, found ${books.length}.`);
  process.exit(1);
}

// ---- palettes: [background, accent (text, contrast above 4.5:1), highlight] ------------------------------------
const PALETTES = [
  ['#16324f', '#ffd166', '#5fa8d3'], // 0 navy
  ['#0f4c4a', '#f4f1bb', '#4ecdc4'], // 1 teal
  ['#3c1a47', '#ffe3a3', '#c77dff'], // 2 plum
  ['#1b4332', '#f1faee', '#74c69d'], // 3 forest
  ['#5a2a1f', '#ffe0b5', '#e76f51'], // 4 rust
  ['#1f2563', '#d7e3ff', '#7b9cff'], // 5 indigo
  ['#262a33', '#ffe9a8', '#f4a261'], // 6 charcoal
  ['#5a1f35', '#ffd9e2', '#ff7096'], // 7 rose
  ['#4a3420', '#f5e6c8', '#d4a373'], // 8 sepia
  ['#023e5a', '#e0fbfc', '#48cae4'], // 9 ocean
  ['#7a3e00', '#fff3c4', '#ffb703'], // 10 sunshine
  ['#3b4a1f', '#f3f7c9', '#a7c957'], // 11 olive
  ['#5c1020', '#ffe3e0', '#ef476f'], // 12 crimson
  ['#2e2a4f', '#f1e9ff', '#9d8df1'], // 13 violet
];

// ---- illustrations: drawn in a box of about 110 x 100 centred on (0, 0); A = accent, H = highlight, B = background -
const WHITE = '#ffffff';
const wave = (y, width, stroke, a, w = 3) =>
  `<path d="M${-width} ${y} q${width / 6} -8 ${width / 3} 0 t${width / 3} 0 t${width / 3} 0 t${width / 3} 0 t${width / 3} 0 t${width / 3} 0" fill="none" stroke="${stroke}" stroke-width="${w}" stroke-linecap="round" opacity="${a}"/>`;
const gear = (cx, cy, r, color) => {
  let teeth = '';
  for (let i = 0; i < 8; i += 1) {
    teeth += `<rect x="-3" y="${-r - 5}" width="6" height="8" rx="1" fill="${color}" transform="translate(${cx} ${cy}) rotate(${i * 45})"/>`;
  }
  return `${teeth}<circle cx="${cx}" cy="${cy}" r="${r}" fill="none" stroke="${color}" stroke-width="5"/>`;
};
const pine = (x, y, s, c1, c2) =>
  `<g transform="translate(${x} ${y}) scale(${s})"><polygon points="0,-44 -20,-14 20,-14" fill="${c1}"/><polygon points="0,-28 -25,6 25,6" fill="${c2}"/><rect x="-4" y="6" width="8" height="12" fill="${c1}"/></g>`;
const diya = (x, y, s, A, H) =>
  `<g transform="translate(${x} ${y}) scale(${s})"><circle cx="0" cy="-14" r="34" fill="${H}" opacity="0.18"/><path d="M-36 6 H36 Q30 36 0 36 Q-30 36 -36 6Z" fill="${A}"/><path d="M0 -44 C-16 -22 -12 -4 0 -2 C12 -4 16 -22 0 -44Z" fill="${H}"/><path d="M0 -26 C-6 -16 -5 -8 0 -6 C5 -8 6 -16 0 -26Z" fill="${WHITE}" opacity="0.8"/></g>`;

const MOTIFS = {
  ladder: (A, H) =>
    `<rect x="-28" y="-50" width="7" height="100" rx="2" fill="${A}"/><rect x="21" y="-50" width="7" height="100" rx="2" fill="${A}"/>` +
    [-38, -18, 2, 22, 42].map((y) => `<rect x="-28" y="${y}" width="56" height="6" fill="${H}"/>`).join(''),
  lotus: (A, H) =>
    [-70, -35, 0, 35, 70].map((a) => `<ellipse cx="0" cy="-2" rx="10" ry="30" fill="${a === 0 ? A : H}" opacity="${a === 0 ? 1 : 0.9}" transform="rotate(${a} 0 28)"/>`).join('') +
    wave(40, 55, A, 0.9, 3),
  target: (A, H) =>
    `<circle r="44" fill="none" stroke="${A}" stroke-width="5"/><circle r="29" fill="none" stroke="${H}" stroke-width="5"/><circle r="14" fill="${A}"/><line x1="0" y1="0" x2="44" y2="-44" stroke="${H}" stroke-width="4" stroke-linecap="round"/><path d="M34 -50 L52 -52 L50 -34Z" fill="${H}"/>`,
  book: (A, H) =>
    `<path d="M0 -26 Q-28 -36 -52 -26 V34 Q-28 24 0 34Z" fill="${H}"/><path d="M0 -26 Q28 -36 52 -26 V34 Q28 24 0 34Z" fill="${A}"/><circle cx="0" cy="-40" r="9" fill="${WHITE}" opacity="0.9"/><path d="M-4 -30 h8 M-3 -27 h6" stroke="${A}" stroke-width="2"/>` +
    [-14, -4, 6].map((y) => `<path d="M-44 ${y} Q-28 ${y - 6} -10 ${y}" fill="none" stroke="${A}" stroke-width="2" opacity="0.8"/>`).join(''),
  notebook: (A, H) =>
    `<rect x="-30" y="-46" width="60" height="92" rx="5" fill="${H}"/>` +
    [-34, -20, -6, 8, 22, 36].map((y) => `<circle cx="-30" cy="${y}" r="3.5" fill="${A}"/>`).join('') +
    [-30, -16, -2, 12, 26].map((y) => `<line x1="-16" y1="${y}" x2="22" y2="${y}" stroke="${A}" stroke-width="2" opacity="0.7"/>`).join('') +
    `<rect x="26" y="-40" width="9" height="76" rx="2" fill="${A}" transform="rotate(22 30 0)"/>`,
  owl: (A, H) =>
    `<polygon points="-26,-22 -18,-46 -6,-28" fill="${H}"/><polygon points="26,-22 18,-46 6,-28" fill="${H}"/><ellipse cx="0" cy="6" rx="31" ry="38" fill="${H}"/>` +
    `<circle cx="-13" cy="-8" r="13" fill="${A}"/><circle cx="13" cy="-8" r="13" fill="${A}"/><circle cx="-13" cy="-8" r="5" fill="#1b1b1b"/><circle cx="13" cy="-8" r="5" fill="#1b1b1b"/>` +
    `<polygon points="-5,2 5,2 0,14" fill="${A}"/><path d="M-18 18 Q0 34 18 18" fill="none" stroke="${A}" stroke-width="2"/><rect x="-46" y="45" width="92" height="6" rx="3" fill="${A}"/>`,
  footsteps: (A, H) =>
    [[-14, 38], [14, 18], [-14, -2], [14, -22], [-14, -42]].map(([x, y], i) =>
      `<ellipse cx="${x}" cy="${y}" rx="8" ry="13" fill="${i % 2 ? H : A}"/><circle cx="${x}" cy="${y - 17}" r="4" fill="${i % 2 ? H : A}"/>`).join(''),
  sunrise: (A, H) => {
    let rays = '';
    for (let i = 0; i < 7; i += 1) {
      const a = (-180 + (i + 1) * 22.5) * (Math.PI / 180);
      rays += `<line x1="${Math.cos(a) * 40}" y1="${20 + Math.sin(a) * 40}" x2="${Math.cos(a) * 54}" y2="${20 + Math.sin(a) * 54}" stroke="${H}" stroke-width="4" stroke-linecap="round"/>`;
    }
    return `${rays}<path d="M-32 20 A32 32 0 0 1 32 20Z" fill="${H}"/><rect x="-55" y="20" width="110" height="26" rx="3" fill="${A}"/>`;
  },
  moon: (A, H, B) =>
    `<circle r="36" fill="${H}"/><circle cx="16" cy="-8" r="31" fill="${B}"/>` +
    [[34, -34, 4], [44, 6, 3], [24, 32, 3.5], [-38, 36, 3]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${A}"/>`).join(''),
  stones: (A, H) =>
    `<ellipse cx="0" cy="34" rx="42" ry="14" fill="${H}"/><ellipse cx="0" cy="12" rx="31" ry="11" fill="${A}"/><ellipse cx="0" cy="-8" rx="22" ry="9" fill="${H}"/><ellipse cx="0" cy="-24" rx="14" ry="7" fill="${A}"/>` +
    `<ellipse cx="0" cy="50" rx="52" ry="6" fill="none" stroke="${A}" stroke-width="2" opacity="0.6"/>`,
  battery: (A, H) =>
    `<rect x="-42" y="-26" width="78" height="52" rx="7" fill="none" stroke="${A}" stroke-width="5"/><rect x="36" y="-10" width="9" height="20" rx="2" fill="${A}"/>` +
    `<rect x="-35" y="-19" width="20" height="38" rx="2" fill="${H}"/><rect x="-11" y="-19" width="20" height="38" rx="2" fill="${H}"/><rect x="13" y="-19" width="20" height="38" rx="2" fill="${H}" opacity="0.3"/>`,
  boundary: (A, H) =>
    `<circle r="44" fill="none" stroke="${A}" stroke-width="4" stroke-dasharray="9 7"/><path d="M0 24 C-32 2 -28 -28 0 -13 C28 -28 32 2 0 24Z" fill="${H}"/>`,
  clock: (A, H) =>
    `<rect x="-6" y="-52" width="12" height="9" rx="2" fill="${A}"/><circle r="38" fill="none" stroke="${A}" stroke-width="6"/><path d="M0 0 L0 -32 A32 32 0 0 1 32 0Z" fill="${H}" opacity="0.65"/><line x1="0" y1="0" x2="0" y2="-26" stroke="${A}" stroke-width="5" stroke-linecap="round"/><line x1="0" y1="0" x2="18" y2="0" stroke="${A}" stroke-width="5" stroke-linecap="round"/><circle r="5" fill="${A}"/>`,
  sprout: (A, H) =>
    `<path d="M0 46 V-8" stroke="${A}" stroke-width="5" stroke-linecap="round"/><path d="M0 8 C-34 8 -42 -18 -40 -30 C-10 -30 0 -14 0 8Z" fill="${H}"/><path d="M0 -6 C34 -6 42 -32 40 -44 C10 -44 0 -28 0 -6Z" fill="${A}"/><ellipse cx="0" cy="46" rx="32" ry="7" fill="${A}" opacity="0.8"/>`,
  envelope: (A, H, B) =>
    `<rect x="-42" y="-16" width="84" height="56" rx="5" fill="${H}"/><path d="M-42 -16 L0 16 L42 -16" fill="none" stroke="${B}" stroke-width="3"/>` +
    [-30, -10, 10, 30].map((x) => `<path d="M${x} -48 l-5 11" stroke="${A}" stroke-width="3.5" stroke-linecap="round"/>`).join(''),
  tram: (A, H, B) =>
    `<path d="M-56 -42 H56" stroke="${A}" stroke-width="2"/><path d="M0 -22 L9 -42" stroke="${A}" stroke-width="3"/><rect x="-44" y="-22" width="88" height="46" rx="9" fill="${H}"/>` +
    [-34, -10, 14].map((x) => `<rect x="${x}" y="-14" width="22" height="18" rx="3" fill="${B}"/>`).join('') +
    `<rect x="-44" y="10" width="88" height="6" fill="${A}"/><circle cx="-26" cy="28" r="7" fill="${A}"/><circle cx="26" cy="28" r="7" fill="${A}"/><path d="M-56 38 H56" stroke="${A}" stroke-width="3"/>`,
  train: (A, H, B) =>
    `<circle cx="-32" cy="-40" r="5" fill="${H}" opacity="0.6"/><circle cx="-24" cy="-50" r="4" fill="${H}" opacity="0.4"/><rect x="-38" y="-30" width="12" height="18" fill="${A}"/>` +
    `<rect x="-48" y="-12" width="64" height="38" rx="5" fill="${H}"/><rect x="14" y="-30" width="34" height="56" rx="5" fill="${A}"/><rect x="21" y="-22" width="20" height="16" rx="2" fill="${B}"/>` +
    [-32, -10, 32].map((x) => `<circle cx="${x}" cy="28" r="9" fill="${A}"/><circle cx="${x}" cy="28" r="3" fill="${B}"/>`).join('') + `<path d="M-56 38 H56" stroke="${A}" stroke-width="3"/>`,
  lake: (A, H) =>
    `<circle cx="30" cy="-26" r="13" fill="${H}"/><polygon points="-56,8 -26,-24 2,8" fill="${A}"/><polygon points="-20,8 14,-16 48,8" fill="${A}" opacity="0.7"/><rect x="-56" y="8" width="112" height="36" fill="${H}" opacity="0.35"/>` +
    wave(20, 48, H, 0.9, 2.5) + wave(32, 48, H, 0.7, 2.5),
  rings: (A, H) =>
    `<circle cx="-15" cy="8" r="24" fill="none" stroke="${A}" stroke-width="7"/><circle cx="15" cy="8" r="24" fill="none" stroke="${H}" stroke-width="7"/><path d="M0 -44 L10 -32 L0 -20 L-10 -32Z" fill="${WHITE}" opacity="0.9"/><path d="M-6 -32 H6" stroke="${H}" stroke-width="2"/>`,
  lamppost: (A, H) =>
    `<circle cx="0" cy="-30" r="30" fill="${H}" opacity="0.22"/><rect x="-3" y="-18" width="6" height="64" fill="${A}"/><rect x="-13" y="40" width="26" height="8" rx="2" fill="${A}"/><path d="M-14 -18 H14 L8 -42 H-8Z" fill="${H}"/><path d="M-12 -42 H12 L0 -52Z" fill="${A}"/><path d="M-50 36 q12 -6 24 0 M26 28 q12 -6 24 0" fill="none" stroke="${A}" stroke-width="2" opacity="0.5"/>`,
  magnifier: (A, H) =>
    `<circle cx="-8" cy="-8" r="30" fill="${H}" opacity="0.25" stroke="${A}" stroke-width="7"/><line x1="14" y1="14" x2="44" y2="44" stroke="${A}" stroke-width="10" stroke-linecap="round"/><text x="-8" y="4" font-family="Georgia, serif" font-size="36" font-weight="700" text-anchor="middle" fill="${H}">?</text>`,
  fingerprint: (A, H) =>
    [8, 16, 24, 32, 40].map((r, i) => `<ellipse cx="0" cy="0" rx="${r}" ry="${r * 1.2}" fill="none" stroke="${i % 2 ? H : A}" stroke-width="3" stroke-dasharray="${36 - i * 3} 7"/>`).join('') + `<circle r="3" fill="${A}"/>`,
  orbit: (A, H) => {
    let dots = '';
    for (let i = 0; i < 6; i += 1) {
      const a = (i * 60 - 90) * (Math.PI / 180);
      dots += `<circle cx="${Math.cos(a) * 42}" cy="${Math.sin(a) * 42}" r="6" fill="${A}"/>`;
    }
    return `<circle r="42" fill="none" stroke="${H}" stroke-width="2" stroke-dasharray="4 5"/><circle r="19" fill="${H}"/><ellipse rx="30" ry="8" fill="none" stroke="${A}" stroke-width="3" transform="rotate(-20)"/>${dots}`;
  },
  lighthouse: (A, H) =>
    `<polygon points="10,-26 56,-42 56,-12" fill="${H}" opacity="0.35"/><polygon points="-10,-26 -56,-42 -56,-12" fill="${H}" opacity="0.35"/>` +
    `<path d="M-56 46 Q0 26 56 46 V52 H-56Z" fill="${A}"/><polygon points="-13,36 13,36 8,-20 -8,-20" fill="${H}"/><polygon points="-11,18 11,18 12,6 -12,6" fill="${A}"/><rect x="-9" y="-32" width="18" height="12" fill="${A}"/><polygon points="-13,-32 13,-32 0,-46" fill="${H}"/>` +
    [[-40, -44, 2], [42, 4, 2.5], [-46, 6, 2]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${A}"/>`).join(''),
  rocket: (A, H, B) =>
    `<path d="M-9 34 L0 56 L9 34Z" fill="${A}"/><path d="M-12 12 L-32 38 L-12 30Z" fill="${A}"/><path d="M12 12 L32 38 L12 30Z" fill="${A}"/><path d="M0 -50 C22 -30 22 10 13 32 H-13 C-22 10 -22 -30 0 -50Z" fill="${H}"/><circle cx="0" cy="-14" r="9" fill="${B}" stroke="${A}" stroke-width="3"/>` +
    [[-44, -34, 2.5], [44, -20, 2], [40, 20, 3], [-40, 10, 2]].map(([x, y, r]) => `<circle cx="${x}" cy="${y}" r="${r}" fill="${A}"/>`).join(''),
  crown: (A, H) =>
    `<path d="M0 -18 C-11 -31 -4 -39 0 -48 C4 -39 11 -31 0 -18Z" fill="${A}"/><polygon points="-36,22 -36,-6 -18,10 0,-14 18,10 36,-6 36,22" fill="${H}"/><rect x="-38" y="22" width="76" height="12" rx="2" fill="${A}"/>` +
    [[-36, -6], [0, -14], [36, -6]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="5" fill="${A}"/>`).join('') + `<circle cx="-16" cy="28" r="2.5" fill="${H}"/><circle cx="0" cy="28" r="2.5" fill="${H}"/><circle cx="16" cy="28" r="2.5" fill="${H}"/>`,
  forest: (A, H) =>
    `<path d="M-56 46 H56" stroke="${A}" stroke-width="4"/>${pine(-32, 30, 1.05, H, A)}${pine(30, 32, 1, A, H)}${pine(0, 26, 1.25, H, A)}` +
    [[-48, -30], [-14, -42], [46, -34], [26, -18]].map(([x, y]) => `<circle cx="${x}" cy="${y}" r="2.5" fill="${WHITE}" opacity="0.75"/>`).join(''),
  bell: (A, H) =>
    `<circle cx="0" cy="-48" r="5" fill="none" stroke="${A}" stroke-width="3"/><path d="M0 -44 C-24 -44 -26 -14 -30 6 L-40 22 H40 L30 6 C26 -14 24 -44 0 -44Z" fill="${H}"/><circle cx="0" cy="27" r="7" fill="${A}"/>` +
    wave(38, 55, A, 0.95, 4) + wave(48, 55, A, 0.6, 3),
  compass: (A, H, B) => {
    let ticks = '';
    for (let i = 0; i < 12; i += 1) {
      ticks += `<line x1="0" y1="-48" x2="0" y2="${i % 3 === 0 ? -40 : -44}" stroke="${A}" stroke-width="2.5" transform="rotate(${i * 30})"/>`;
    }
    return `<circle r="42" fill="none" stroke="${A}" stroke-width="5"/>${ticks}<polygon points="0,-34 9,0 -9,0" fill="${H}"/><polygon points="0,34 9,0 -9,0" fill="${A}"/><circle r="4" fill="${B}"/>`;
  },
  river: (A, H) =>
    `<path d="M-30 -50 C12 -30 -40 -10 0 6 C40 22 -22 34 22 52" fill="none" stroke="${H}" stroke-width="18" stroke-linecap="round"/><path d="M-30 -50 C12 -30 -40 -10 0 6 C40 22 -22 34 22 52" fill="none" stroke="${A}" stroke-width="3" stroke-dasharray="2 9" stroke-linecap="round"/>` +
    `<circle cx="-42" cy="-30" r="9" fill="${A}" opacity="0.9"/><path d="M34 -40 q8 -10 16 0" fill="none" stroke="${A}" stroke-width="2"/>`,
  palette: (A, H, B) =>
    `<path d="M0 -40 C30 -44 52 -22 50 4 C48 30 20 34 6 36 C-6 38 -4 24 -14 22 C-40 20 -52 6 -48 -14 C-44 -32 -20 -38 0 -40Z" fill="${H}"/>` +
    `<circle cx="-24" cy="-10" r="6" fill="${A}"/><circle cx="-6" cy="-24" r="6" fill="${WHITE}"/><circle cx="16" cy="-22" r="6" fill="${B}"/><circle cx="30" cy="-2" r="6" fill="${A}"/>` +
    `<line x1="16" y1="40" x2="52" y2="-28" stroke="${A}" stroke-width="6" stroke-linecap="round"/><circle cx="54" cy="-33" r="4" fill="${WHITE}"/>`,
  school: (A, H, B) =>
    `<line x1="0" y1="-36" x2="0" y2="-52" stroke="${A}" stroke-width="2.5"/><polygon points="0,-52 18,-46 0,-40" fill="${A}"/><polygon points="-48,-6 0,-36 48,-6" fill="${A}"/><rect x="-40" y="-6" width="80" height="46" fill="${H}"/>` +
    [[-32, 2], [16, 2], [-32, 22], [16, 22]].map(([x, y]) => `<rect x="${x}" y="${y}" width="16" height="12" rx="2" fill="${B}"/>`).join('') + `<rect x="-8" y="16" width="16" height="24" rx="2" fill="${B}"/>`,
  boat: (A, H) =>
    `<path d="M-4 -42 L-4 6 H-38Z" fill="${A}"/><path d="M3 -26 L3 6 H32Z" fill="${H}"/><path d="M-46 8 H46 L30 34 H-30Z" fill="${H}"/><path d="M-30 34 L-4 8 L22 34" fill="none" stroke="${A}" stroke-width="2" opacity="0.6"/>` + wave(44, 55, A, 0.9, 3),
  butterfly: (A, H, B) =>
    `<ellipse cx="-26" cy="-14" rx="28" ry="19" fill="${H}" transform="rotate(-25 -26 -14)"/><ellipse cx="26" cy="-14" rx="28" ry="19" fill="${H}" transform="rotate(25 26 -14)"/>` +
    `<ellipse cx="-19" cy="18" rx="18" ry="13" fill="${A}" transform="rotate(25 -19 18)"/><ellipse cx="19" cy="18" rx="18" ry="13" fill="${A}" transform="rotate(-25 19 18)"/>` +
    `<circle cx="-30" cy="-16" r="6" fill="${B}"/><circle cx="30" cy="-16" r="6" fill="${B}"/><rect x="-3.5" y="-24" width="7" height="56" rx="3.5" fill="${A}"/><path d="M0 -24 Q-8 -42 -18 -44 M0 -24 Q8 -42 18 -44" fill="none" stroke="${A}" stroke-width="2.5" stroke-linecap="round"/>`,
  fox: (A, H, B) =>
    `<path d="M0 42 L-38 4 L-42 -40 L-13 -20 H13 L42 -40 L38 4Z" fill="${H}"/><path d="M0 42 L-24 12 L-8 2 H8 L24 12Z" fill="${WHITE}" opacity="0.92"/><circle cx="-15" cy="-4" r="4" fill="${B}"/><circle cx="15" cy="-4" r="4" fill="${B}"/><circle cx="0" cy="38" r="4.5" fill="${B}"/>` +
    `<circle cx="-52" cy="-14" r="5" fill="${A}"/><line x1="-47" y1="-14" x2="-47" y2="-38" stroke="${A}" stroke-width="2.5"/><circle cx="52" cy="6" r="5" fill="${A}"/><line x1="57" y1="6" x2="57" y2="-18" stroke="${A}" stroke-width="2.5"/>`,
  lanterns: (A, H) =>
    `<path d="M-56 -36 Q0 -16 56 -36" fill="none" stroke="${A}" stroke-width="2"/>` +
    [[-38, -29, H], [-13, -24, A], [13, -24, H], [38, -29, A]].map(([x, y, c]) => `<line x1="${x}" y1="${y}" x2="${x}" y2="${y + 8}" stroke="${A}" stroke-width="2"/><ellipse cx="${x}" cy="${y + 20}" rx="10" ry="14" fill="${c}"/>`).join('') +
    `<polygon points="-50,20 50,20 42,6 -42,6" fill="${A}"/><rect x="-46" y="30" width="92" height="6" fill="${H}"/><rect x="-40" y="36" width="5" height="14" fill="${H}"/><rect x="35" y="36" width="5" height="14" fill="${H}"/>`,
  window: (A, H) =>
    `<path d="M-32 48 V-12 A32 32 0 0 1 32 -12 V48Z" fill="${H}" opacity="0.35" stroke="${A}" stroke-width="5"/><path d="M0 -44 V48 M-32 10 H32" stroke="${A}" stroke-width="4"/><circle cx="16" cy="-14" r="6" fill="${A}"/>`,
  skyline: (A, H, B) =>
    [[-50, -2, 16, A], [-32, -24, 18, H], [-12, -10, 16, A], [6, -36, 18, H], [26, -14, 18, A]].map(([x, y, w, c]) => `<rect x="${x}" y="${y}" width="${w}" height="${48 - y}" fill="${c}"/>`).join('') +
    [[-46, 8], [-28, -12], [-28, 8], [-8, 4], [10, -26], [10, -6], [10, 14], [30, 0], [30, 20]].map(([x, y]) => `<rect x="${x}" y="${y}" width="5" height="6" fill="${B}"/>`).join('') +
    [-36, -12, 12, 36].map((x) => `<path d="M${x} -52 l-4 9" stroke="${H}" stroke-width="2.5" stroke-linecap="round"/>`).join(''),
  doors: (A, H) =>
    [[-50, A], [-14, H], [22, A]].map(([x, c]) => `<rect x="${x}" y="-28" width="30" height="72" rx="3" fill="${c}"/><circle cx="${x + 23}" cy="10" r="2.5" fill="#1b1b1b"/><rect x="${x + 6}" y="-20" width="18" height="22" rx="2" fill="none" stroke="#1b1b1b" stroke-width="1.5" opacity="0.4"/>`).join('') +
    `<rect x="-56" y="44" width="112" height="6" rx="2" fill="${H}"/><polygon points="-10,-50 10,-50 20,-32 -20,-32" fill="${H}" opacity="0.35"/>`,
  drop: (A, H) =>
    `<path d="M0 -50 C20 -22 32 -10 32 8 C32 27 17 38 0 38 C-17 38 -32 27 -32 8 C-32 -10 -20 -22 0 -50Z" fill="${H}"/><path d="M-14 12 Q-14 24 -4 28" fill="none" stroke="${WHITE}" stroke-width="3.5" stroke-linecap="round" opacity="0.7"/>` +
    `<path d="M-44 -6 c4 -8 8 -12 8 -16 c0 4 4 8 8 16 a8 8 0 0 1 -16 0Z" fill="${A}" transform="translate(-4 10)"/><path d="M44 -6 c4 -8 8 -12 8 -16 c0 4 4 8 8 16 a8 8 0 0 1 -16 0Z" fill="${A}" transform="translate(-16 14) scale(0.8)"/>` +
    `<path d="M-54 48 H-24 V42 H24 V48 H54" fill="none" stroke="${A}" stroke-width="4" stroke-linejoin="round"/>`,
  camera: (A, H, B) =>
    `<path d="M-16 -20 L-9 -33 H9 L16 -20Z" fill="${A}"/><rect x="-46" y="-20" width="92" height="62" rx="9" fill="${H}"/><circle cx="0" cy="12" r="23" fill="${A}"/><circle cx="0" cy="12" r="15" fill="${B}"/><circle cx="0" cy="12" r="7" fill="${H}"/><rect x="26" y="-14" width="13" height="8" rx="2" fill="${A}"/>` +
    [[-30, -44], [0, -50], [30, -44]].map(([x, y]) => `<line x1="${x}" y1="${y}" x2="${x * 0.75}" y2="${y + 11}" stroke="${H}" stroke-width="3" stroke-linecap="round"/>`).join(''),
  table: (A, H) =>
    `<rect x="-50" y="16" width="100" height="8" rx="2" fill="${A}"/><rect x="-42" y="24" width="6" height="26" fill="${A}"/><rect x="36" y="24" width="6" height="26" fill="${A}"/>` +
    `<ellipse cx="-14" cy="2" rx="19" ry="15" fill="${H}"/><ellipse cx="-14" cy="-13" rx="9" ry="4" fill="${A}"/><path d="M4 0 L24 -10 L24 -5 L7 10Z" fill="${H}"/><path d="M-33 -2 C-48 -2 -48 12 -33 10" fill="none" stroke="${H}" stroke-width="4"/>` +
    `<rect x="28" y="4" width="17" height="12" rx="3" fill="${A}"/><path d="M-14 -22 q-6 -8 0 -14 t0 -14" fill="none" stroke="${H}" stroke-width="2.5" opacity="0.7" stroke-linecap="round"/>`,
  hut: (A, H, B) =>
    `<circle cx="-38" cy="-30" r="10" fill="${H}"/><rect x="-32" y="-4" width="62" height="38" fill="${H}"/><polygon points="-40,-4 -1,-34 38,-4" fill="${A}"/><rect x="-8" y="12" width="14" height="22" rx="2" fill="${B}"/><rect x="12" y="6" width="12" height="12" rx="2" fill="${B}"/>` +
    `<rect x="36" y="8" width="6" height="26" fill="${A}"/><circle cx="39" cy="-2" r="15" fill="${H}" opacity="0.85"/><path d="M-52 40 H54" stroke="${A}" stroke-width="3"/>`,
  rails: (A, H) =>
    `<circle cx="0" cy="-30" r="14" fill="${H}"/><path d="M-14 -10 L-40 48 M14 -10 L40 48" stroke="${A}" stroke-width="4" stroke-linecap="round"/><path d="M-21 4 H21 M-26 16 H26 M-32 28 H32 M-37 40 H37" stroke="${H}" stroke-width="3"/>` +
    `<path d="M-46 44 Q-42 4 -40 -20" fill="none" stroke="${A}" stroke-width="4" stroke-linecap="round"/><path d="M-40 -20 L-56 -12 M-40 -20 L-50 -32 M-40 -20 L-28 -32 M-40 -20 L-26 -12" stroke="${H}" stroke-width="4" stroke-linecap="round"/>` +
    `<path d="M46 44 Q42 4 40 -20" fill="none" stroke="${A}" stroke-width="4" stroke-linecap="round"/><path d="M40 -20 L56 -12 M40 -20 L50 -32 M40 -20 L28 -32 M40 -20 L26 -12" stroke="${H}" stroke-width="4" stroke-linecap="round"/>`,
  cactus: (A, H) =>
    `<circle cx="0" cy="-6" r="40" fill="${H}" opacity="0.4"/><rect x="-9" y="-24" width="18" height="70" rx="9" fill="${A}"/><path d="M-9 8 H-24 V-12" fill="none" stroke="${A}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/><path d="M9 -2 H24 V-24" fill="none" stroke="${A}" stroke-width="8" stroke-linecap="round" stroke-linejoin="round"/>` +
    `<circle cx="0" cy="-26" r="5" fill="${H}"/><rect x="-54" y="44" width="108" height="6" rx="3" fill="${H}"/>`,
  pot: (A, H) =>
    `<path d="M-34 -34 q-8 -8 0 -14 M0 -36 q-8 -8 0 -14 M34 -34 q-8 -8 0 -14" fill="none" stroke="${H}" stroke-width="3" stroke-linecap="round" opacity="0.75"/>` +
    `<path d="M-38 -8 H38 L32 34 Q32 42 24 42 H-24 Q-32 42 -32 34Z" fill="${H}"/><path d="M-42 -8 Q0 -30 42 -8Z" fill="${A}"/><circle cx="0" cy="-26" r="5" fill="${A}"/><rect x="-50" y="0" width="12" height="7" rx="3" fill="${A}"/><rect x="38" y="0" width="12" height="7" rx="3" fill="${A}"/>`,
  spices: (A, H) =>
    [[-36, A], [0, H], [36, A]].map(([x, c]) => `<path d="M${x - 17} 14 H${x + 17} Q${x + 17} 34 ${x} 34 Q${x - 17} 34 ${x - 17} 14Z" fill="${H === c ? A : H}"/><ellipse cx="${x}" cy="14" rx="17" ry="6" fill="${c}"/><ellipse cx="${x}" cy="10" rx="11" ry="6" fill="${c}"/>`).join('') +
    `<path d="M-14 -32 Q8 -48 22 -24 Q26 -12 16 -16 Q6 -28 -10 -26Z" fill="${A}"/><path d="M-10 -28 Q-14 -34 -20 -34" fill="none" stroke="${H}" stroke-width="3" stroke-linecap="round"/>`,
  seasons: (A, H, B) =>
    `<path d="M0 0 L0 -44 A44 44 0 0 1 44 0Z" fill="${A}"/><path d="M0 0 L44 0 A44 44 0 0 1 0 44Z" fill="${H}"/><path d="M0 0 L0 44 A44 44 0 0 1 -44 0Z" fill="${A}" opacity="0.6"/><path d="M0 0 L-44 0 A44 44 0 0 1 0 -44Z" fill="${H}" opacity="0.6"/>` +
    `<path d="M-46 0 H46 M0 -46 V46" stroke="${B}" stroke-width="3"/><circle r="9" fill="${B}"/><path d="M-4 3 Q0 -7 4 3 Q0 1 -4 3Z" fill="${A}"/>`,
  cloud: (A, H) =>
    `<circle cx="-22" cy="-14" r="18" fill="${H}"/><circle cx="4" cy="-26" r="23" fill="${H}"/><circle cx="30" cy="-12" r="17" fill="${H}"/><rect x="-40" y="-14" width="84" height="18" rx="9" fill="${H}"/>` +
    [-30, -14, 2, 18, 34].map((x, i) => `<path d="M${x} ${14 + (i % 2) * 8} l-5 14" stroke="${A}" stroke-width="3.5" stroke-linecap="round"/>`).join(''),
  cell: (A, H) =>
    `<circle r="44" fill="${H}" opacity="0.3" stroke="${H}" stroke-width="4"/><circle cx="12" cy="-12" r="15" fill="${A}"/><circle cx="12" cy="-12" r="6" fill="${H}"/>${gear(-14, 16, 13, A)}` +
    `<ellipse cx="28" cy="22" rx="9" ry="5" fill="${H}" transform="rotate(30 28 22)"/><ellipse cx="-26" cy="-22" rx="8" ry="4.5" fill="${H}" transform="rotate(-25 -26 -22)"/>`,
  flask: (A, H) =>
    `<rect x="-14" y="-48" width="28" height="7" rx="2" fill="${A}"/><rect x="-9" y="-42" width="18" height="28" fill="${H}"/><polygon points="-9,-16 9,-16 40,36 -40,36" fill="${H}"/><polygon points="-26,10 26,10 40,36 -40,36" fill="${A}"/><path d="M-40 36 Q-40 42 -34 42 H34 Q40 42 40 36" fill="${A}"/>` +
    `<circle cx="-6" cy="0" r="4" fill="${A}"/><circle cx="8" cy="-10" r="3" fill="${A}"/><circle cx="2" cy="-24" r="2.5" fill="${A}"/>`,
  thought: (A, H) => {
    let rays = '';
    for (let i = 0; i < 8; i += 1) {
      rays += `<line x1="0" y1="-21" x2="0" y2="-29" stroke="${A}" stroke-width="3.5" stroke-linecap="round" transform="translate(0 -10) rotate(${i * 45})"/>`;
    }
    return `<ellipse cx="0" cy="-10" rx="48" ry="35" fill="${H}"/><circle cx="-30" cy="38" r="8" fill="${H}"/><circle cx="-42" cy="50" r="5" fill="${H}"/>${rays}<circle cx="0" cy="-10" r="14" fill="${A}"/>`;
  },
  bird: (A, H) =>
    `<ellipse cx="0" cy="4" rx="28" ry="15" fill="${H}" transform="rotate(-18)"/><circle cx="26" cy="-12" r="10" fill="${H}"/><polygon points="34,-14 48,-9 34,-6" fill="${A}"/><path d="M-4 0 Q-24 -36 -46 -34 Q-30 -6 -2 8Z" fill="${A}"/><path d="M-24 10 L-48 22 L-40 8Z" fill="${A}"/>` +
    `<path d="M-52 34 q10 6 20 0 M-30 46 q10 6 20 0" fill="none" stroke="${A}" stroke-width="2.5" stroke-linecap="round" opacity="0.7"/>`,
  venn: (A, H) =>
    `<circle cx="-17" cy="12" r="30" fill="${H}" opacity="0.6"/><circle cx="17" cy="12" r="30" fill="${A}" opacity="0.6"/><circle cx="0" cy="-18" r="30" fill="${WHITE}" opacity="0.4"/>`,
  diyas: (A, H) => `${diya(0, -4, 1, A, H)}${diya(-42, 34, 0.5, A, H)}${diya(42, 34, 0.5, A, H)}`,
  eiffel: (A, H) =>
    `<path d="M0 -54 L-9 -10 L-32 48 M0 -54 L9 -10 L32 48" fill="none" stroke="${A}" stroke-width="5" stroke-linecap="round" stroke-linejoin="round"/><path d="M-10 -16 H10 M-18 10 H18 M-27 32 H27" stroke="${H}" stroke-width="5" stroke-linecap="round"/>` +
    `<path d="M-9 -10 L18 10 M9 -10 L-18 10 M-18 10 L27 32 M18 10 L-27 32" stroke="${A}" stroke-width="2" opacity="0.8"/><path d="M-20 48 Q0 22 20 48" fill="none" stroke="${H}" stroke-width="4"/>`,
  bubbles: (A, H, B) =>
    `<rect x="-48" y="-42" width="66" height="38" rx="13" fill="${H}"/><polygon points="-34,-5 -24,-5 -38,12" fill="${H}"/><text x="-15" y="-17" font-family="Georgia, serif" font-size="15" font-weight="700" text-anchor="middle" fill="${B}">¡Hola!</text>` +
    `<rect x="-14" y="2" width="66" height="38" rx="13" fill="${A}"/><polygon points="38,39 28,39 42,54" fill="${A}"/><text x="19" y="26" font-family="Georgia, serif" font-size="14" font-weight="700" text-anchor="middle" fill="${B}">ser · ir</text>`,
  suitcase: (A, H, B) =>
    `<path d="M-14 -16 V-30 Q-14 -36 -8 -36 H8 Q14 -36 14 -30 V-16" fill="none" stroke="${A}" stroke-width="5"/><rect x="-44" y="-16" width="88" height="58" rx="9" fill="${H}"/><rect x="-30" y="2" width="60" height="22" rx="4" fill="${B}"/>` +
    `<text x="0" y="18" font-family="Georgia, serif" font-size="13" font-weight="700" text-anchor="middle" fill="${A}">Namaste</text><circle cx="-30" cy="48" r="5" fill="${A}"/><circle cx="30" cy="48" r="5" fill="${A}"/>`,
  blocks: (A, H, B) =>
    `<rect x="-40" y="8" width="38" height="38" rx="4" fill="${H}"/><rect x="2" y="8" width="38" height="38" rx="4" fill="${A}"/><rect x="-19" y="-32" width="38" height="38" rx="4" fill="${H}"/>` +
    [[-21, 36, 'A'], [21, 36, 'B'], [0, -4, 'C']].map(([x, y, t]) => `<text x="${x}" y="${y}" font-family="Georgia, serif" font-size="26" font-weight="700" text-anchor="middle" fill="${B}">${t}</text>`).join(''),
  globe: (A, H) =>
    `<circle r="40" fill="${H}" opacity="0.25" stroke="${A}" stroke-width="5"/><ellipse rx="16" ry="40" fill="none" stroke="${A}" stroke-width="3"/><ellipse rx="30" ry="40" fill="none" stroke="${A}" stroke-width="2" opacity="0.6"/><path d="M-40 0 H40 M-35 -20 H35 M-35 20 H35" stroke="${A}" stroke-width="3"/><path d="M-24 50 H24" stroke="${A}" stroke-width="5" stroke-linecap="round"/>`,
};

// [motif, palette] for each of the 60 books, chosen to suit the title and the genre.
const PLAN = {
  1: ['ladder', 0], 2: ['lotus', 1], 3: ['target', 5], 4: ['book', 10], 5: ['notebook', 8], 6: ['owl', 6],
  7: ['footsteps', 9], 8: ['sunrise', 4], 9: ['moon', 13], 10: ['stones', 3], 11: ['battery', 6], 12: ['boundary', 7],
  13: ['clock', 1], 14: ['sprout', 11], 15: ['envelope', 9], 16: ['tram', 7], 17: ['train', 4], 18: ['lake', 1],
  19: ['rings', 12], 20: ['lamppost', 6], 21: ['magnifier', 13], 22: ['fingerprint', 8], 23: ['orbit', 5],
  24: ['lighthouse', 12], 25: ['rocket', 13], 26: ['crown', 12], 27: ['forest', 3], 28: ['bell', 9], 29: ['compass', 8],
  30: ['river', 9], 31: ['palette', 4], 32: ['school', 0], 33: ['boat', 10], 34: ['butterfly', 10], 35: ['fox', 4],
  36: ['lanterns', 6], 37: ['window', 1], 38: ['skyline', 13], 39: ['doors', 2], 40: ['drop', 9], 41: ['camera', 8],
  42: ['table', 4], 43: ['hut', 11], 44: ['rails', 3], 45: ['cactus', 10], 46: ['pot', 4], 47: ['spices', 12],
  48: ['seasons', 11], 49: ['cloud', 5], 50: ['cell', 2], 51: ['flask', 1], 52: ['thought', 0], 53: ['bird', 13],
  54: ['venn', 2], 55: ['diyas', 10], 56: ['eiffel', 0], 57: ['bubbles', 12], 58: ['suitcase', 4], 59: ['blocks', 3],
  60: ['globe', 9],
};

// ---- text helpers ---------------------------------------------------------------------------------------------
const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

function wrap(text, maxChars) {
  const lines = [];
  let line = '';
  for (const word of text.split(/\s+/)) {
    if (line && (line + ' ' + word).length > maxChars) {
      lines.push(line);
      line = word;
    } else {
      line = line ? `${line} ${word}` : word;
    }
  }
  if (line) lines.push(line);
  return lines;
}

const textLines = (lines, x, y, size, lineHeight, attrs) =>
  lines.map((l, i) => `<text x="${x}" y="${y + i * lineHeight}" font-size="${size}" ${attrs}>${esc(l)}</text>`).join('\n  ');

const SERIF = `font-family="Georgia, 'Times New Roman', serif"`;

// ---- covers ---------------------------------------------------------------------------------------------------
function front(book) {
  const [motif, paletteIndex] = PLAN[book.id];
  const [B, A, H] = PALETTES[paletteIndex];
  let size = 19;
  let lines = wrap(book.title, 13);
  if (lines.length > 3) {
    size = 16;
    lines = wrap(book.title, 16);
  }
  const lineHeight = Math.round(size * 1.22);
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 300" width="200" height="300">
  <rect width="200" height="300" fill="${B}"/>
  <rect x="10" y="10" width="180" height="280" fill="none" stroke="${A}" stroke-width="2"/>
  <rect x="15" y="15" width="170" height="270" fill="none" stroke="${H}" stroke-width="1" opacity="0.6"/>
  ${textLines(lines, 100, 46, size, lineHeight, `${SERIF} font-weight="700" text-anchor="middle" fill="${A}"`)}
  <g transform="translate(100 176)">${MOTIFS[motif](A, H, B)}</g>
  <line x1="60" y1="252" x2="140" y2="252" stroke="${H}" stroke-width="1.5"/>
  <text x="100" y="272" ${SERIF} font-size="12" text-anchor="middle" fill="${A}">${esc(book.author)}</text>
</svg>
`;
}

function back(book) {
  const [, paletteIndex] = PLAN[book.id];
  const [B, A, H] = PALETTES[paletteIndex];
  const titleLines = wrap(book.title, 20);
  const description = wrap(book.description, 25).slice(0, 9);
  const descY = 40 + titleLines.length * 17 + 14;
  let bars = '';
  for (let i = 0; i < 18; i += 1) {
    const w = i % 3 === 0 ? 2.4 : 1.2;
    bars += `<rect x="${136 + i * 2.7}" y="248" width="${w}" height="24" fill="#111111"/>`;
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 300" width="200" height="300">
  <rect width="200" height="300" fill="${B}"/>
  <rect x="10" y="10" width="180" height="280" fill="none" stroke="${A}" stroke-width="2"/>
  <rect x="15" y="15" width="170" height="270" fill="none" stroke="${H}" stroke-width="1" opacity="0.6"/>
  ${textLines(titleLines, 100, 40, 14, 17, `${SERIF} font-weight="700" text-anchor="middle" fill="${A}"`)}
  <line x1="60" y1="${descY - 12}" x2="140" y2="${descY - 12}" stroke="${H}" stroke-width="1.5"/>
  ${textLines(description, 100, descY + 6, 10.5, 15, `${SERIF} text-anchor="middle" fill="${A}"`)}
  <rect x="130" y="242" width="56" height="36" rx="2" fill="${WHITE}"/>${bars}
  ${textLines(wrap(book.publisher, 18), 24, 262, 9, 12, `${SERIF} fill="${A}"`)}
</svg>
`;
}

mkdirSync(outDir, { recursive: true });
for (const book of books) {
  writeFileSync(join(outDir, `${book.id}-front.svg`), front(book));
  writeFileSync(join(outDir, `${book.id}-back.svg`), back(book));
}

console.log(`Wrote ${books.length} front covers and ${books.length} back covers to ${outDir}`);
