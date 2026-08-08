#!/usr/bin/env python3
"""Office DDOS LAN bridge -> TTFN via Tailscale (no root)."""
from __future__ import annotations

import select
import socket
import threading

MAP = [
    (12048, "100.64.118.44", 2048),  # AnGe-ClashBoard
    (19999, "100.64.118.44", 9999),  # fnOS
]


def pipe(a: socket.socket, b: socket.socket) -> None:
    try:
        while True:
            ready, _, _ = select.select([a, b], [], [], 120)
            if not ready:
                break
            for s in ready:
                data = s.recv(65536)
                if not data:
                    return
                (b if s is a else a).sendall(data)
    except Exception:
        pass
    finally:
        for s in (a, b):
            try:
                s.close()
            except Exception:
                pass


def serve(local_port: int, remote_host: str, remote_port: int) -> None:
    ls = socket.socket()
    ls.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    ls.bind(("0.0.0.0", local_port))
    ls.listen(50)
    print(f"LISTEN {local_port} -> {remote_host}:{remote_port}", flush=True)
    while True:
        client, _ = ls.accept()
        try:
            remote = socket.create_connection((remote_host, remote_port), 8)
        except Exception:
            client.close()
            continue
        threading.Thread(target=pipe, args=(client, remote), daemon=True).start()


def main() -> None:
    for local_port, remote_host, remote_port in MAP:
        threading.Thread(
            target=serve, args=(local_port, remote_host, remote_port), daemon=True
        ).start()
    threading.Event().wait()


if __name__ == "__main__":
    main()
