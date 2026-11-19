// Regenerates the demo writers, writer biographies and reviews of the seed catalogue (AMD-003). Deterministic.
//   node scripts/generate-seed-data.mjs
// - rewrites the author of every book in backend/src/main/resources/db/seed/R__seed_books.sql (12 writers x 5 books)
// - writes db/seed/R__seed_writers.sql (one 50-word biography per writer)
// - writes db/seed/R__seed_reviews.sql (10 demo readers, 5 reviews for each of the 60 books)
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { WRITERS } from './seed-writers.mjs';

const seedDir = join(dirname(fileURLToPath(import.meta.url)), '..', 'backend', 'src', 'main', 'resources', 'db', 'seed');

const READERS = [
  'Ananya Sharma', 'Rahul Mehta', 'Priya Nair', 'Vikram Singh', 'Sneha Patil',
  'Arjun Desai', 'Fatima Khan', 'Karthik Menon', 'Ishita Bose', 'Daniel Pereira',
];

const POOLS = {
  guide: [
    [5, 'Clear, kind and genuinely practical. I started using it the same evening.'],
    [5, 'Short chapters that fit into a busy day. Already recommended it to two friends.'],
    [4, 'Good ideas without the usual preaching. A few chapters felt repeated.'],
    [5, 'Calm, honest writing. It changed one small habit and that was enough.'],
    [4, 'Easy to read and easy to apply. I wanted a few more worked examples.'],
    [3, 'Sensible advice, but much of it was familiar to me. Still a pleasant read.'],
    [5, 'I keep it on my desk and reread a page whenever I get stuck.'],
    [4, 'Thoughtful and well paced. The exercises at the end were the best part.'],
  ],
  story: [
    [5, 'Could not put it down. I read the last third in one sitting.'],
    [4, 'Lovely characters and a setting I could picture clearly. The ending felt a touch rushed.'],
    [5, 'A beautifully paced story with an ending that stayed with me for days.'],
    [4, 'Engaging from the first page. A couple of subplots could have been tighter.'],
    [5, 'Warm, clever and full of small surprises. I would love a sequel.'],
    [3, 'Good premise and a strong opening, though the middle dragged a little for me.'],
    [4, 'Well written with memorable characters. A great weekend read.'],
    [5, 'Stayed up far too late finishing this. Highly recommended.'],
  ],
  creative: [
    [5, 'Charming and full of heart. I read it twice in one week.'],
    [4, 'Short, bright and easy to enjoy. Perfect for a quiet evening.'],
    [5, 'Beautiful language and rhythm. Some pages I read aloud just to hear them.'],
    [4, 'Imaginative and fun, with a gentle message that never feels forced.'],
    [5, 'A gift I ended up keeping for myself. Warm and lovely.'],
    [3, 'Pleasant and well made, though I expected a little more depth.'],
    [4, 'Great for sharing. The tone is gentle and the pace is just right.'],
    [5, 'Fresh and original. I will look for more from this writer.'],
  ],
  life: [
    [5, 'Honest, vivid and moving. I felt like I was right there.'],
    [4, 'Beautifully observed details. Some sections move slowly but they are worth it.'],
    [5, 'Warm and absorbing. I learned something on almost every page.'],
    [4, 'Well researched and easy to read. I would happily read more by this writer.'],
    [5, 'Generous, personal storytelling. It made me call my family afterwards.'],
    [3, 'Interesting material, though the structure felt a little loose in places.'],
    [4, 'A thoughtful, well-written book that I finished faster than I expected.'],
    [5, 'Practical and inspiring in equal measure. A keeper.'],
  ],
  learn: [
    [5, 'Explains difficult ideas in plain words. I finally understand the topic.'],
    [4, 'Clear examples and a friendly tone. A good first book on the subject.'],
    [5, 'Well organised and easy to revise from. Exactly what I was looking for.'],
    [4, 'Useful and accurate. I would have liked a few more practice exercises.'],
    [5, 'Makes learning feel light. I picked up something new every day.'],
    [3, 'Helpful for beginners, though a little basic once you know the fundamentals.'],
    [4, 'Short sections make it easy to fit in daily. Good value for the price.'],
    [5, 'The best introduction I have read. Clear, patient and encouraging.'],
  ],
};

function poolFor(id) {
  if (id <= 14 || (id >= 52 && id <= 55)) return POOLS.guide;
  if (id <= 32) return POOLS.story;
  if (id <= 39) return POOLS.creative;
  if (id <= 48) return POOLS.life;
  return POOLS.learn;
}

const sql = (text) => `'${text.replace(/'/g, "''")}'`;
const words = (text) => text.trim().split(/\s+/).length;

// Sanity checks: 12 writers, 5 distinct books each, all 60 books covered once, every biography exactly 50 words.
const owner = new Map();
for (const writer of WRITERS) {
  if (writer.books.length !== 5) throw new Error(`${writer.name} must have 5 books`);
  if (words(writer.bio) !== 50) throw new Error(`${writer.name}: bio has ${words(writer.bio)} words, expected 50`);
  for (const id of writer.books) {
    if (owner.has(id)) throw new Error(`Book ${id} assigned twice`);
    owner.set(id, writer.name);
  }
}
if (owner.size !== 60) throw new Error(`Expected 60 books, got ${owner.size}`);

// 1) authors in R__seed_books.sql
const booksPath = join(seedDir, 'R__seed_books.sql');
let books = readFileSync(booksPath, 'utf8');
books = books.replace(/^\((\d+), ('(?:[^']|'')*'), '(?:[^']|'')*', /gm, (match, id, title) =>
  `(${id}, ${title}, ${sql(owner.get(Number(id)))}, `);
writeFileSync(booksPath, books);

// 2) writers
const writers = `-- Writer biographies of the demo catalogue (AMD-003): 12 writers, 5 books each, about 50 words per biography.
-- Generated by scripts/generate-seed-data.mjs. Repeatable and idempotent; dev and test only.

INSERT INTO author_profile (name, bio) VALUES
${WRITERS.map((w) => `(${sql(w.name)}, ${sql(w.bio)})`).join(',\n')}
ON CONFLICT (name) DO UPDATE SET bio = EXCLUDED.bio;
`;
writeFileSync(join(seedDir, 'R__seed_writers.sql'), writers);

// 3) readers and reviews
const reviewRows = [];
for (let id = 1; id <= 60; id += 1) {
  const pool = poolFor(id);
  for (let k = 0; k < 5; k += 1) {
    const [rating, comment] = pool[(id * 3 + k) % pool.length];
    const reader = ((id + k * 2) % 10) + 1;
    const created = new Date(Date.UTC(2026, 9, 1, 8) + (id * 5 + k * 17) * 3600 * 1000).toISOString();
    reviewRows.push(`(${id}, ${reader}, ${rating}, ${sql(comment)}, '${created}')`);
  }
}
const reviews = `-- Demo readers and 5 reviews for each of the 60 books (AMD-003). Generated by scripts/generate-seed-data.mjs.
-- The readers are placeholder accounts: their password hash is not a valid hash of any password, so nobody can log
-- in as them, and their e-mail addresses end in .invalid. Repeatable and idempotent; dev and test only.

INSERT INTO customer (full_name, email, password_hash, token_version, created_at) VALUES
${READERS.map((name, i) => `(${sql(name)}, 'reader${i + 1}@seed.bookygeek.invalid', '{pbkdf2}${'A'.repeat(64)}', 0, '2026-09-01T09:00:00+00')`).join(',\n')}
ON CONFLICT (email) DO NOTHING;

INSERT INTO review (book_id, customer_id, rating, comment, created_at)
SELECT v.book_id, c.id, v.rating, v.comment, v.created_at::timestamptz
FROM (VALUES
${reviewRows.join(',\n')}
) AS v(book_id, reader, rating, comment, created_at)
JOIN customer c ON c.email = 'reader' || v.reader || '@seed.bookygeek.invalid'
ON CONFLICT (book_id, customer_id) DO NOTHING;
`;
writeFileSync(join(seedDir, 'R__seed_reviews.sql'), reviews);

console.log(`authors updated, ${WRITERS.length} writers, ${READERS.length} readers, ${reviewRows.length} reviews`);

