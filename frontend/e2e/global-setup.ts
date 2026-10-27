import { randomBytes } from 'node:crypto';

import { q, springPasswordHash, sql } from './support';

/**
 * Creates a throwaway Admin for this run (staff accounts cannot sign up) and shares the run's
 * names with the tests through environment variables. Everything is removed by global-teardown.
 */
export default async function globalSetup() {
  const health = await fetch('http://localhost:8080/actuator/health').catch(() => null);
  if (!health?.ok) {
    throw new Error('The backend is not running on http://localhost:8080. Start it first (IntelliJ: "CivSetu API (dev)").');
  }

  const run = Date.now().toString(36);
  const password = randomBytes(12).toString('base64url');
  process.env.E2E_RUN = run;
  process.env.E2E_PASSWORD = password;
  process.env.E2E_ADMIN_EMAIL = `e2e-admin-${run}@example.test`;

  sql(
    `INSERT INTO users (name, email, password_hash, role) VALUES ('E2E Admin', ${q(process.env.E2E_ADMIN_EMAIL)}, ${q(
      springPasswordHash(password),
    )}, 'ADMIN');`,
  );
}
