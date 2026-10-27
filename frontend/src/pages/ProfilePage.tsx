import { useState, type FormEvent } from 'react';

import { authApi } from '../api/endpoints';
import { useAuth } from '../auth/AuthProvider';
import { Alert, Button, Card, ErrorMessage, PageHeader, TextField } from '../components/shared';

export function ProfilePage() {
  const { user, updateUser } = useAuth();
  const [name, setName] = useState(user?.name ?? '');
  const [phone, setPhone] = useState(user?.phone ?? '');
  const [busy, setBusy] = useState(false);
  const [saved, setSaved] = useState(false);
  const [error, setError] = useState<unknown>();

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setSaved(false);
    setError(undefined);
    try {
      updateUser(await authApi.updateProfile({ name, phone: phone.trim() || undefined }));
      setSaved(true);
    } catch (caught) {
      setError(caught);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-lg">
      <PageHeader title="Your profile" description={`${user?.email} · ${user?.role?.toLowerCase()} account`} />
      <Card>
        <form onSubmit={submit} className="space-y-4">
          {saved && <Alert tone="success">Profile saved.</Alert>}
          <ErrorMessage error={error} />
          <TextField label="Full name" value={name} onChange={(e) => setName(e.target.value)} />
          <TextField label="Phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} />
          <Button type="submit" busy={busy}>
            Save
          </Button>
        </form>
      </Card>
    </div>
  );
}
