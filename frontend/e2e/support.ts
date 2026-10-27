import { execFileSync } from 'node:child_process';
import { existsSync } from 'node:fs';
import { homedir } from 'node:os';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));

/**
 * Helpers for preparing and cleaning end-to-end test data in the local development database
 * (the Docker Compose PostGIS container started by the backend).
 */
export const DB_CONTAINER = process.env.E2E_DB_CONTAINER ?? 'backend-postgres-1';

/** Runs SQL in the development database and returns the output lines. */
export function sql(statement: string): string[] {
  const output = execFileSync(
    'docker',
    // -i forwards stdin, which carries the SQL
    ['exec', '-i', DB_CONTAINER, 'sh', '-c', 'psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tA'],
    { input: statement, encoding: 'utf8' },
  );
  return output.split('\n').map((line) => line.trim()).filter(Boolean);
}

/** A {bcrypt} hash made by Spring Security itself, so the backend accepts it. */
export function springPasswordHash(password: string): string {
  const m2 = join(homedir(), '.m2', 'repository');
  const jar = (group: string, artifact: string, version: string) =>
    join(m2, ...group.split('.'), artifact, version, `${artifact}-${version}.jar`);
  const classpath = [
    jar('org.springframework.security', 'spring-security-crypto', '7.1.2'),
    jar('commons-logging', 'commons-logging', '1.3.5'),
  ];
  classpath.forEach((path) => {
    if (!existsSync(path)) throw new Error(`Missing ${path}: build the backend once with mvnw first.`);
  });
  return execFileSync('java', ['-cp', classpath.join(process.platform === 'win32' ? ';' : ':'), join(HERE, 'Hash.java'), password], {
    encoding: 'utf8',
  }).trim();
}

/** Quotes a value for SQL. Only used with values this test generated itself. */
export const q = (value: string) => `'${value.replaceAll("'", "''")}'`;
