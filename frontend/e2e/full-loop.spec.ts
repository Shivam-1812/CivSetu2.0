import { expect, test, type Browser, type Page } from '@playwright/test';

/**
 * The whole CivSetu loop in a real browser, with all three roles:
 * Admin sets up a department and an official → a citizen reports an issue → the system routes it
 * → a neighbour agrees with it on the public feed → the department official plans and resolves it
 * with proof → the citizen sees the result and is notified.
 */

const run = () => process.env.E2E_RUN!;
const password = () => process.env.E2E_PASSWORD!;

// A valid 1x1 PNG; the backend checks file content, not the name
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC',
  'base64',
);

async function signIn(browser: Browser, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/login');
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password());
  await page.locator('form').getByRole('button', { name: 'Sign In' }).click();
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  return page;
}

async function signUp(browser: Browser, name: string, email: string): Promise<Page> {
  const page = await (await browser.newContext()).newPage();
  await page.goto('/signup');
  await page.getByLabel('Full name').fill(name);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(password());
  await page.getByLabel('Confirm password', { exact: true }).fill(password());
  await page.getByRole('button', { name: 'Create Account' }).click();
  await expect(page).toHaveURL(/\/dashboard$/);
  return page;
}

test('citizen → system → community → department official → citizen', async ({ browser }) => {
  const RUN = run().toUpperCase();
  const departmentName = `E2E Parks ${run()}`;
  const categoryLabel = `E2E broken bench ${run()}`;
  const officerEmail = `e2e-officer-${run()}@example.test`;
  const title = `Broken bench in the park ${run()}`;

  // 1. Admin: a department, a category routed to it, and an official working there
  const admin = await signIn(browser, process.env.E2E_ADMIN_EMAIL!);
  await admin.goto('/admin/routing');
  const departments = admin.locator('section', { has: admin.getByRole('heading', { name: 'Departments' }) });
  await departments.getByLabel('Code').fill(`E2E_${RUN}`);
  await departments.getByLabel('Name').fill(departmentName);
  await departments.getByRole('button', { name: 'Add' }).click();
  await expect(departments.getByText(departmentName)).toBeVisible();

  const categories = admin.locator('section', { has: admin.getByRole('heading', { name: 'Categories' }) });
  await categories.getByLabel('Code').fill(`E2E_BENCH_${RUN}`);
  await categories.getByLabel('Label').fill(categoryLabel);
  await categories.getByLabel('Usually handled by').selectOption({ label: departmentName });
  await categories.getByRole('button', { name: 'Add category' }).click();
  await expect(categories.getByText(categoryLabel)).toBeVisible();

  await admin.goto('/admin/users');
  await admin.getByLabel('Name').fill('E2E Officer');
  await admin.getByLabel('Email').fill(officerEmail);
  await admin.getByLabel('Temporary password').fill(password());
  await admin.getByLabel('Department').selectOption({ label: departmentName });
  await admin.getByRole('button', { name: 'Create account' }).click();
  await expect(admin.getByText('E2E Officer can now sign in as authority.')).toBeVisible();

  // 2. Citizen: sign up and report, with a photo and a location picked on the map
  const citizen = await signUp(browser, 'E2E Citizen', `e2e-citizen-${run()}@example.test`);
  await citizen.goto('/report');
  await citizen.getByLabel(/Issue Title/).fill(title);
  await citizen.getByRole('combobox', { name: 'Category' }).click();
  await citizen.getByRole('option', { name: categoryLabel }).click();
  await citizen.getByLabel(/^Description/).fill('The wooden seat is split and has sharp edges.');
  await citizen.getByLabel(/Address/).fill('Near the east gate, E2E Park');
  await citizen.locator('.leaflet-container').first().click();
  await expect(citizen.getByText(/^Selected:/)).toBeVisible();
  await citizen.getByLabel('Photos (optional)').setInputFiles({ name: 'bench.png', mimeType: 'image/png', buffer: PNG });
  await citizen.getByRole('button', { name: 'Submit Issue Report' }).click();

  // 3. The system routes it within seconds
  await expect(citizen.getByText('Issue Reported!')).toBeVisible();
  await expect(citizen.getByText(departmentName, { exact: true })).toBeVisible();
  await citizen.getByRole('button', { name: 'View Your Issue' }).click();
  await expect(citizen).toHaveURL(/\/issues\/[0-9a-f-]{36}$/);
  const issueUrl = new URL(citizen.url()).pathname;
  await expect(citizen.getByText('Assigned', { exact: true })).toBeVisible();
  await expect(citizen.getByRole('img', { name: 'Photo of the issue' })).toBeVisible();

  // 4. A neighbour agrees with it from the public feed, like a social post
  const neighbour = await signUp(browser, 'E2E Neighbour', `e2e-neighbour-${run()}@example.test`);
  await neighbour.goto('/map');
  await neighbour.getByLabel('Search issues').fill(title);
  const card = neighbour.getByRole('article', { name: title });
  await expect(card).toBeVisible();
  await card.getByRole('button', { name: /^Agree, 0$/ }).click();
  await expect(card.getByRole('button', { name: /^Agree, 1$/ })).toHaveAttribute('aria-pressed', 'true');

  // 5. Department official: finds it in Manage Issues, plans the work, resolves it with proof
  const officer = await signIn(browser, officerEmail);
  await officer.goto('/authority/issues');
  await officer.getByRole('button', { name: `Actions for ${title}` }).click();
  await officer.getByRole('menuitem', { name: 'Plan Resolution' }).click();
  await officer.getByLabel('What will be done').fill('Carpenter will replace the seat planks.');
  await officer.getByRole('button', { name: 'Allocate & Start' }).click();
  await expect(officer.getByText('Issue status updated to In Progress')).toBeVisible();

  await officer.getByRole('tab', { name: 'In Progress' }).click();
  await officer.getByRole('button', { name: `Actions for ${title}` }).click();
  await officer.getByRole('menuitem', { name: 'Finalize & Resolve' }).click();
  await officer.getByLabel('What was done').fill('Seat planks replaced and sanded.');
  await officer.getByLabel(/Proof photos/).setInputFiles({ name: 'fixed.png', mimeType: 'image/png', buffer: PNG });
  await officer.getByRole('button', { name: 'Mark Resolved' }).click();
  await expect(officer.getByText('Issue has been marked as resolved')).toBeVisible();

  // 6. Citizen: sees the fix, the public timeline, the proof, and a notification
  await citizen.goto(issueUrl);
  await expect(citizen.getByText('Resolved', { exact: true }).first()).toBeVisible();
  await expect(citizen.getByText('Work planned')).toBeVisible();
  await expect(citizen.getByText('Seat planks replaced and sanded.')).toBeVisible();
  await expect(citizen.getByRole('button', { name: /Notifications, [1-9]\d* unread/ })).toBeVisible();
  await citizen.getByRole('button', { name: /Resolution Verified/ }).click();
  await expect(citizen.getByRole('img', { name: 'Photo showing the fix' })).toBeVisible();

  // 7. Admin: the actions are in the audit log
  await admin.goto('/admin/audit');
  await expect(admin.getByText('issue resolved').first()).toBeVisible();
});

test('anyone can browse the home page, the map and the issue feed', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: /Your Voice for a/ })).toBeVisible();
  await page.goto('/map');
  await expect(page.getByRole('heading', { name: 'Issue Map' })).toBeVisible();
  await expect(page.locator('.leaflet-container')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Reported Issues' })).toBeVisible();
  await expect(page.getByText(/^\d+ issues?$/)).toBeVisible();
});
