#!/usr/bin/env python3
"""Probe MTProxy auth (fake TLS + obfuscated) on feed proxies."""
import hashlib
import os
import re
import socket
import struct
import sys
import time
import urllib.request

try:
    from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
except ImportError:
    print("pip install cryptography")
    sys.exit(1)

BLOCKED = {0x44414548, 0x54534F50, 0x20544547, 0x4954504F, 0xDDDDDDDD, 0xEEEEEEEE, 0x02010316}


def aes_ctr(data: bytes, key: bytes, iv: bytes) -> bytes:
    cipher = Cipher(algorithms.AES(key), modes.CTR(iv), None)
    enc = cipher.encryptor()
    return enc.update(data) + enc.finalize()


def parse_secret(hexs: str):
    b = bytes.fromhex(hexs)
    if len(b) == 16:
        return b, False, None
    if len(b) == 17 and b[0] in (0xDD, 0xEE):
        return b[1:17], True, None
    if len(b) >= 18 and b[0] == 0xEE:
        return b[1:17], True, b[17:].decode("utf-8", "replace")
    return None, False, None


def build_init(key: bytes, dd: bool) -> bytes:
    tag = b"\xdd\xdd\xdd\xdd" if dd else b"\xee\xee\xee\xee"
    while True:
        pkt = bytearray(os.urandom(64))
        if pkt[0] == 0xEF:
            continue
        first = struct.unpack("<I", pkt[0:4])[0]
        if first in BLOCKED or struct.unpack("<I", pkt[4:8])[0] == 0:
            continue
        break
    pkt[56:60] = tag
    pkt[60], pkt[61] = 2, 0
    ek = hashlib.sha256(bytes(pkt[8:40]) + key).digest()
    eiv = bytes(pkt[40:56])
    rev = bytes(pkt[8:56])[::-1]
    dk = hashlib.sha256(rev[:32] + key).digest()
    div = rev[32:48]
    tail = aes_ctr(bytes(pkt[56:64]), ek, eiv)
    pkt[56:64] = tail
    return bytes(pkt), dk, div


def check_obf(host, port, key, dd, timeout=8):
    try:
        s = socket.create_connection((host, port), timeout)
        s.settimeout(timeout)
        init, dk, div = build_init(key, dd)
        s.sendall(init)
        resp = b""
        while len(resp) < 64:
            resp += s.recv(64 - len(resp))
        s.close()
        if len(resp) < 64:
            return "short"
        r = bytearray(resp)
        plain = aes_ctr(bytes(r[56:64]), dk, div)
        r[56:64] = plain
        tag = struct.unpack("<I", r[56:60])[0]
        if tag in (0xEEEEEEEE, 0xDDDDDDDD, 0xEFEFEFEF) or r[56] == 0xEF:
            return "OK"
        return f"tag_{tag:08x}"
    except Exception as e:
        return str(e)[:40]


def main():
    html = urllib.request.urlopen(
        "https://web.archive.org/web/2/https://t.me/s/ProxyMTProto", timeout=45
    ).read().decode("utf-8", "replace").replace("&amp;", "&")
    rows = re.findall(r"server=([^&\"'\s<>]+)&port=(\d+)&secret=([0-9a-fA-F]+)", html, re.I)
    ok = 0
    tcp = 0
    for host, port, sec in rows[:40]:
        try:
            socket.create_connection((host, int(port)), 3).close()
            tcp += 1
        except OSError:
            continue
        key, dd, dom = parse_secret(sec)
        if not key:
            continue
        r = check_obf(host, int(port), key, dd)
        if r == "OK":
            ok += 1
        print(f"{host}:{port} dom={dom is not None} obf={r}")
    print(f"SUMMARY tcp={tcp} auth_ok={ok}/{len(rows)}")


if __name__ == "__main__":
    main()
