#!/usr/bin/env python3
"""Minimal MTProxy obfuscated init probe (Telethon MTProxyIO)."""
import hashlib
import os
import re
import socket
import struct
import sys
import urllib.request

BLOCKED = {0x44414548, 0x54534F50, 0x20544547, 0x4954504F, 0xDDDDDDDD, 0xEEEEEEEE, 0x02010316}


def aes_ctr(data: bytes, key: bytes, iv: bytes) -> bytes:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

    cipher = Cipher(algorithms.AES(key), modes.CTR(iv), None)
    enc = cipher.encryptor()
    return enc.update(data) + enc.finalize()


def build_init(secret: bytes, dd_tag: bool = False) -> tuple[bytes, bytes, bytes]:
    tag = b"\xdd\xdd\xdd\xdd" if dd_tag else b"\xee\xee\xee\xee"
    while True:
        pkt = bytearray(os.urandom(64))
        if pkt[0] == 0xEF:
            continue
        first = struct.unpack("<I", pkt[0:4])[0]
        if first in BLOCKED:
            continue
        if struct.unpack("<I", pkt[4:8])[0] == 0:
            continue
        break
    pkt[56:60] = tag
    struct.pack_into("<h", pkt, 60, 2)
    ek = hashlib.sha256(bytes(pkt[8:40]) + secret).digest()
    eiv = bytes(pkt[40:56])
    rev = bytes(pkt[8:56])[::-1]
    dk = hashlib.sha256(rev[:32] + secret).digest()
    div = rev[32:48]
    tail = aes_ctr(bytes(pkt[56:64]), ek, eiv)
    pkt[56:64] = tail
    return bytes(pkt), dk, div


def probe(host: str, port: int, secret_hex: str, timeout: float = 8) -> str:
    sec = bytes.fromhex(secret_hex)
    if len(sec) == 17 and sec[0] == 0xDD:
        key, dd = sec[1:17], True
    elif len(sec) >= 18 and sec[0] == 0xEE:
        key, dd = sec[1:17], True
    elif len(sec) == 16:
        key, dd = sec, False
    else:
        return "bad_secret"
    try:
        s = socket.create_connection((host, port), timeout)
        s.settimeout(timeout)
        init, dk, div = build_init(key, dd)
        s.sendall(init)
        resp = b""
        while len(resp) < 64:
            chunk = s.recv(64 - len(resp))
            if not chunk:
                break
            resp += chunk
        s.close()
        if len(resp) < 64:
            return "short_resp"
        r = bytearray(resp)
        plain = aes_ctr(bytes(r[56:64]), dk, div)
        r[56:64] = plain
        tag = struct.unpack("<I", r[56:60])[0]
        if tag in (0xEEEEEEEE, 0xDDDDDDDD, 0xEFEFEFEF) or r[56] == 0xEF:
            return "OK"
        return f"bad_tag_{tag:08x}"
    except Exception as e:
        return f"err_{e}"


def main():
    html = urllib.request.urlopen(
        "https://web.archive.org/web/2/https://t.me/s/ProxyMTProto", timeout=45
    ).read().decode("utf-8", "replace")
    html = html.replace("&amp;", "&")
    pat = re.compile(r"server=([^&\"'\s<>]+)&port=(\d+)&secret=([0-9a-fA-F]+)", re.I)
    rows = pat.findall(html)
    ok = 0
    for host, port, sec in rows:
        try:
            socket.create_connection((host, int(port)), 3).close()
            tcp = "Y"
        except OSError:
            print(f"{host}:{port} tcp=N skip")
            continue
        r = probe(host, int(port), sec)
        if r == "OK":
            ok += 1
        print(f"{host}:{port} tcp=Y obf={r} hexlen={len(sec)}")
    print(f"obfuscated OK: {ok}")


if __name__ == "__main__":
    try:
        main()
    except ImportError:
        print("pip install cryptography", file=sys.stderr)
        sys.exit(1)
