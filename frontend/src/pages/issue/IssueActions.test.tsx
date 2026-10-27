import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import type { IssueDetail } from '../../api/types';
import { jsonResponse, renderWithProviders } from '../../test/render';
import { IssueActions } from './IssueActions';

const authority = { id: 'auth-1', name: 'Officer', role: 'AUTHORITY', departmentId: 'dept-1' };

const issue = (overrides: Partial<IssueDetail>): IssueDetail => ({
  id: 'issue-1',
  status: 'ASSIGNED',
  version: 3,
  departmentId: 'dept-1',
  assignedAuthorityId: 'auth-1',
  reportedByMe: false,
  ...overrides,
});

/** Signs the given user in through the mocked refresh call, then answers other requests with `handler`. */
function mockApi(user: object, handler: (url: string, init?: RequestInit) => Response) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url.endsWith('/auth/refresh')) {
      return jsonResponse(200, { accessToken: 'token', tokenType: 'Bearer', expiresIn: 900, user });
    }
    return handler(url, init);
  });
}

describe('IssueActions', () => {
  it('offers an Authority of the department the actions allowed in the current status', async () => {
    mockApi(authority, () => jsonResponse(200, {}));
    renderWithProviders(<IssueActions issue={issue({ status: 'ASSIGNED' })} />);

    expect(await screen.findByRole('button', { name: 'Start work' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Not our department' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Mark resolved' })).not.toBeInTheDocument();
  });

  it('lets a colleague in the same department act, not only the assigned lead', async () => {
    mockApi({ ...authority, id: 'colleague' }, () => jsonResponse(200, {}));
    renderWithProviders(<IssueActions issue={issue({ status: 'IN_PROGRESS' })} />);

    expect(await screen.findByRole('button', { name: 'Mark resolved' })).toBeInTheDocument();
  });

  it('shows nothing to an Authority of another department', async () => {
    mockApi({ ...authority, id: 'someone-else', departmentId: 'dept-2' }, () => jsonResponse(200, {}));
    const { container } = renderWithProviders(<IssueActions issue={issue({})} />);

    await waitFor(() => expect(container).toBeEmptyDOMElement());
  });

  it('sends the version it showed, and explains a 409 when someone else acted first', async () => {
    const fetchMock = mockApi(authority, () =>
      jsonResponse(409, { code: 'STALE_ISSUE', detail: 'This issue changed since you loaded it.' }),
    );
    renderWithProviders(<IssueActions issue={issue({ status: 'ASSIGNED' })} />);

    await userEvent.click(await screen.findByRole('button', { name: 'Not our department' }));
    await userEvent.type(screen.getByLabelText(/Why it is not your department/), 'Belongs to the water department.');
    await userEvent.click(screen.getByRole('button', { name: 'Confirm' }));

    expect(await screen.findByText(/Someone else updated this issue/)).toBeInTheDocument();
    const call = fetchMock.mock.calls.find(([url]) => String(url).endsWith('/issues/issue-1/return'));
    expect(JSON.parse(String(call?.[1]?.body))).toEqual({ version: 3, reason: 'Belongs to the water department.' });
  });
});
