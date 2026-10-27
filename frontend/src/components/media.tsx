import { useEffect, useState } from 'react';

import { getAccessToken } from '@/api/client';
import type { Media } from '@/api/types';

const API_ORIGIN = import.meta.env.VITE_API_BASE_URL ?? '';

export type MediaType = 'image' | 'voice' | 'pdf' | 'doc';

/** What kind of file this is, from its stored content type. */
export function mediaType(media: Pick<Media, 'contentType'>): MediaType {
  const type = media.contentType ?? '';
  if (type.startsWith('image/')) return 'image';
  if (type.startsWith('audio/')) return 'voice';
  if (type === 'application/pdf') return 'pdf';
  return 'doc';
}

/**
 * Loads a stored file through the API with the access token (private files such as voice notes
 * need it; a plain <img src> cannot send headers) and returns a temporary blob URL for it.
 */
export function useMediaUrl(path?: string | null): { url?: string; failed: boolean } {
  const [state, setState] = useState<{ path?: string | null; url?: string; failed: boolean }>({ failed: false });

  useEffect(() => {
    if (!path) return;
    let objectUrl: string | undefined;
    const controller = new AbortController();
    const token = getAccessToken();
    fetch(`${API_ORIGIN}${path}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) throw new Error(String(response.status));
        return response.blob();
      })
      .then((blob) => {
        objectUrl = URL.createObjectURL(blob);
        setState({ path, url: objectUrl, failed: false });
      })
      .catch((error: unknown) => {
        if (!(error instanceof DOMException && error.name === 'AbortError')) setState({ path, failed: true });
      });
    return () => {
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [path]);

  // Ignore a result that belongs to a previous path
  return state.path === path ? { url: state.url, failed: state.failed } : { failed: false };
}

/** A cover photo for cards; shows nothing until loaded. */
export function MediaImage({ path, alt, className }: { path: string; alt: string; className?: string }) {
  const { url } = useMediaUrl(path);
  if (!url) return <div className={`animate-pulse bg-muted ${className ?? ''}`} />;
  return <img src={url} alt={alt} className={className} />;
}
