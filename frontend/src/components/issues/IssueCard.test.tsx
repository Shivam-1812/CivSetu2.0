import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { IssueSummary } from '@/api/types';
import { jsonResponse, renderWithProviders } from '@/test/render';
import { IssueCard } from './IssueCard';

const issue: IssueSummary = {
  id: 'issue-1',
  title: 'Broken streetlight',
  description: 'Dark at night near the school.',
  categoryCode: 'STREETLIGHT',
  categoryLabel: 'Streetlight not working',
  departmentName: 'Street Lighting',
  status: 'ASSIGNED',
  priority: 'MEDIUM',
  address: 'Kothrud',
  agreeCount: 2,
  disagreeCount: 0,
  commentCount: 3,
};

function mockApi(user: object | null, handler: (url: string, init?: RequestInit) => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url.endsWith('/auth/refresh')) {
      return user
        ? jsonResponse(200, { accessToken: 'token', tokenType: 'Bearer', expiresIn: 900, user })
        : jsonResponse(401, { code: 'UNAUTHORIZED' });
    }
    return handler(url, init);
  });
}

describe('IssueCard', () => {
  it('lets a citizen like an issue from the feed and shows the server count', async () => {
    const fetchMock = mockApi({ id: 'c-1', name: 'Asha', role: 'CITIZEN' }, (url) =>
      url.endsWith('/issues/issue-1/vote') ? jsonResponse(200, { agree: 3, disagree: 0, myVote: 'AGREE' }) : jsonResponse(200, {}),
    );
    renderWithProviders(<IssueCard issue={issue} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Agree, 2' }));

    const liked = await screen.findByRole('button', { name: 'Agree, 3' });
    expect(liked).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'Comments, 3' })).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/issues/issue-1/vote'));
    expect(call?.[1]?.method).toBe('PUT');
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ value: 'AGREE' });
  });

  it('does not send a vote for visitors who are not signed in', async () => {
    const fetchMock = mockApi(null, () => jsonResponse(200, {}));
    renderWithProviders(<IssueCard issue={issue} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Agree, 2' }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Agree, 2' })).toHaveAttribute('aria-pressed', 'false'));
    expect(fetchMock.mock.calls.some(([url]) => String(url).includes('/vote'))).toBe(false);
  });
});
