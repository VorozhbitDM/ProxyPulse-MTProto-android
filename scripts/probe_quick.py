#!/usr/bin/env python3
import hashlib, os, re, socket, struct, sys, time, urllib.request
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

BLOCKED = {0x44414548, 0x54534F50, 0x20544547, 0x4954504F, 0xDDDDDDDD, 0xEEEEEEEE, 0x02010316}

def aes_ctr(data, key, iv):
    c = Cipher(algorithms.AES(key), modes.CTR(iv), None)
    e = c.encryptor()
    return e.update(data) + e.finalize()

def parse_secret(hexs):
    try:
        b = bytes.fromhex(hexs)
    except ValueError:
        return None
    if len(b) == 16:
        return b, False
    if len(b) == 17 and b[0] in (0xDD, 0xEE):
        return b[1:17], True
    if len(b) >= 18 and b[0] == 0xEE:
        return b[1:17], True
    return None

def obf_check(host, port, key, dd, t=6):
    try:
        s = socket.create_connection((host, port), t)
        s.settimeout(t)
        tag = b"\xdd\xdd\xdd\xdd" if dd else b"\xee\xee\xee\xee"
        while True:
            pkt = bytearray(os.urandom(64))
            if pkt[0] == 0xEF: continue
            if struct.unpack("<I", pkt[0:4])[0] in BLOCKED: continue
            if struct.unpack("<I", pkt[4:8])[0] == 0: continue
            break
        pkt[56:60] = tag
        pkt[60], pkt[61] = 2, 0
        ek = hashlib.sha256(bytes(pkt[8:40]) + key).digest()
        eiv = bytes(pkt[40:56])
        rev = bytes(pkt[8:56])[::-1]
        dk = hashlib.sha256(rev[:32] + key).digest()
        div = rev[32:48]
        pkt[56:64] = aes_ctr(bytes(pkt[56:64]), ek, eiv)
        s.sendall(bytes(pkt))
        resp = b""
        dl = time.time() + t
        while len(resp) < 64 and time.time() < dl:
            resp += s.recv(64 - len(resp))
        s.close()
        if len(resp) < 64:
            return "short"
        r = bytearray(resp)
        r[56:64] = aes_ctr(bytes(r[56:64]), dk, div)
        tagv = struct.unpack("<I", r[56:60])[0]
        if tagv in (0xEEEEEEEE, 0xDDDDDDDD, 0xEFEFEFEF) or r[56] == 0xEF:
            return "OK"
        return f"tag_{tagv:08x}"
    except Exception as e:
        return type(e).__name__

def main():
    html = urllib.request.urlopen(
        "https://web.archive.org/web/2/https://t.me/s/ProxyMTProto", timeout=40
    ).read().decode("utf-8", "replace").replace("&amp;", "&")
    rows = re.findall(r"server=([^&\"'\s<>]+)&port=(\d+)&secret=([0-9a-fA-F]+)", html, re.I)
    tcp = obf_ok = 0
    for host, port, sec in rows[:30]:
        try:
            socket.create_connection((host, int(port)), 4).close()
            tcp += 1
        except OSError:
            print(f"{host}:{port} tcp=FAIL")
            continue
        p = parse_secret(sec)
        if not p:
            print(f"{host}:{port} secret_reject len={len(sec)//2}")
            continue
        key, dd = p
        r = obf_check(host, int(port), key, dd)
        if r == "OK":
            obf_ok += 1
        print(f"{host}:{port} tcp=Y obf={r} hexlen={len(sec)}")
    print(f"SUMMARY n={len(rows)} tcp={tcp} obf_ok={obf_ok}")

if __name__ == "__main__":
    main()
