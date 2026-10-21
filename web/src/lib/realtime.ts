import { Client } from '@stomp/stompjs';
import { useEffect, useRef } from 'react';

import { getSession } from './api';

/**
 * Subscribes to a STOMP destination for the lifetime of the component. The access token travels in the CONNECT
 * frame (browsers cannot set headers on the WebSocket upgrade); reconnects pick up refreshed tokens.
 */
// eslint-disable-next-line @typescript-eslint/no-unnecessary-type-parameters -- T names the server's payload shape for callers
export function useStomp<T>(destination: string | null, onMessage: (payload: T) => void): void {
  const handler = useRef(onMessage);
  useEffect(() => {
    handler.current = onMessage;
  });

  useEffect(() => {
    if (!destination) return;
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const client = new Client({
      brokerURL: `${scheme}://${window.location.host}/ws`,
      reconnectDelay: 3000,
      beforeConnect: (c) => {
        c.connectHeaders = { Authorization: `Bearer ${getSession()?.accessToken ?? ''}` };
      },
      onConnect: () => {
        client.subscribe(destination, (frame) => {
          handler.current(JSON.parse(frame.body) as T);
        });
      },
    });
    client.activate();
    return () => {
      void client.deactivate();
    };
  }, [destination]);
}
