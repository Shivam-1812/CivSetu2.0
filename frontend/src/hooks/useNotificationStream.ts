import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';

import { notificationApi } from '../api/endpoints';
import type { Notification } from '../api/types';

const API_ORIGIN = import.meta.env.VITE_API_BASE_URL ?? '';
const MAX_BACKOFF_MS = 60_000;

/**
 * Keeps a live notification stream open while someone is signed in. EventSource cannot send the
 * access token, so each connection uses a fresh one-time ticket; after an error the hook asks for
 * a new ticket and reconnects with backoff, then refetches the lists so nothing missed is lost.
 */
export function useNotificationStream(enabled: boolean, onNotification?: (notification: Notification) => void) {
  const queryClient = useQueryClient();

  useEffect(() => {
    if (!enabled || typeof EventSource === 'undefined') return;
    let source: EventSource | null = null;
    let retryTimer: ReturnType<typeof setTimeout> | undefined;
    let attempt = 0;
    let stopped = false;

    const refreshLists = () => {
      queryClient.invalidateQueries({ queryKey: ['notifications'] });
    };

    const connect = async () => {
      try {
        const { streamUrl } = await notificationApi.streamTicket();
        if (stopped) return;
        source = new EventSource(`${API_ORIGIN}${streamUrl}`);
        source.onopen = () => {
          attempt = 0;
          refreshLists();
        };
        source.addEventListener('notification', (event) => {
          const notification = JSON.parse((event as MessageEvent<string>).data) as Notification;
          refreshLists();
          if (notification.issueId) queryClient.invalidateQueries({ queryKey: ['issue', notification.issueId] });
          onNotification?.(notification);
        });
        source.onerror = () => {
          // The ticket was single-use, so the browser's own reconnect would fail: start over
          source?.close();
          scheduleReconnect();
        };
      } catch {
        scheduleReconnect();
      }
    };

    const scheduleReconnect = () => {
      if (stopped) return;
      attempt += 1;
      retryTimer = setTimeout(connect, Math.min(1000 * 2 ** attempt, MAX_BACKOFF_MS));
    };

    connect();
    return () => {
      stopped = true;
      clearTimeout(retryTimer);
      source?.close();
    };
  }, [enabled, onNotification, queryClient]);
}
