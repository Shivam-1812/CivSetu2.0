import { rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import { q, sql } from './support';

/** Removes everything this run created: users, issues (with their files), department and category. */
export default async function globalTeardown() {
  const run = process.env.E2E_RUN;
  if (!run) return;
  const users = `(SELECT id FROM users WHERE email LIKE ${q(`e2e-%-${run}@example.test`)})`;
  const issues = `(SELECT id FROM issues WHERE reporter_id IN ${users})`;

  const issueIds = sql(`SELECT id FROM issues WHERE reporter_id IN ${users};`);
  sql(`
    BEGIN;
    DELETE FROM audit_events WHERE actor_id IN ${users} OR entity_id IN ${issues} OR entity_id IN ${users};
    DELETE FROM issues WHERE id IN ${issues};
    DELETE FROM audit_events WHERE entity_id IN (SELECT id FROM categories WHERE code LIKE ${q(`E2E%${run.toUpperCase()}`)})
        OR entity_id IN (SELECT id FROM departments WHERE code LIKE ${q(`E2E%${run.toUpperCase()}`)});
    DELETE FROM notifications WHERE user_id IN ${users};
    DELETE FROM users WHERE id IN ${users};
    DELETE FROM categories WHERE code LIKE ${q(`E2E%${run.toUpperCase()}`)};
    DELETE FROM departments WHERE code LIKE ${q(`E2E%${run.toUpperCase()}`)};
    COMMIT;
  `);
  // Uploaded photos live on disk in development
  issueIds.forEach((id) =>
    rmSync(join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'backend', 'uploads', 'issues', id), { recursive: true, force: true }),
  );
}
