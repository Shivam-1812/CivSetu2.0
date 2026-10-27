import { describe, expect, it } from 'vitest';

import { formatRelative } from './format';

describe('formatRelative', () => {
  const now = new Date('2026-10-27T10:00:00Z');

  it('describes past and future times in plain words', () => {
    expect(formatRelative('2026-10-27T08:00:00Z', now)).toBe('2 hours ago');
    expect(formatRelative('2026-10-30T10:00:00Z', now)).toBe('in 3 days');
    expect(formatRelative('2026-10-27T09:59:30Z', now)).toBe('30 seconds ago');
    expect(formatRelative(null, now)).toBe('—');
  });
});
