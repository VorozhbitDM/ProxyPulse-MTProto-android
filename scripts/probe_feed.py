#!/usr/bin/env python3
"""Quick probe of proxies from archive feed: TCP + secret shape."""
import re
import socket
import urllib.request

URL = "https://web.archive.org/web/2/https://t.me/s/ProxyMTProto"


def parse_secret(hexs: str):
    try:
        b = bytes.fromhex(hexs)
    except ValueError:
        return "invalid_hex", None
    if len(b) == 16:
        return "plain16", b
    if len(b) == 17 and b[0] == 0xDD:
        return "dd17", b[1:17]
    if len(b) >= 18 and b[0] == 0xEE:
        return "ee_tls", b
    if len(b) == 17 and b[0] == 0xEE:
        return "ee17_obfuscated_only", b
    return f"reject_len{len(b)}", b


def main():
    html = urllib.request.urlopen(URL, timeout=45).read().decode("utf-8", "replace")
    html = html.replace("&amp;", "&")
    # Match href query: server=...&port=...&secret=hex
    pat = re.compile(
        r"server=([^&\"'\s<>]+)&port=(\d+)&secret=([0-9a-fA-F]+)",
        re.I,
    )
    proxies = pat.findall(html)
    if not proxies:
        # Fallback: extract from tg:// links then parse fields
        for link in re.findall(r"(?:tg://proxy\?|/proxy\?)([^\"'\s<>]+)", html, re.I):
            fields = dict(
                p.split("=", 1) for p in link.split("&") if "=" in p
            )
            if all(k in fields for k in ("server", "port", "secret")):
                proxies.append(
                    (fields["server"], fields["port"], fields["secret"])
                )
    print(f"Found {len(proxies)} proxies on first page")

    kinds = {}
    tcp_ok = 0
    kotlin_parse_ok = 0
    for host, port, sec in proxies:
        kind, _ = parse_secret(sec)
        kinds[kind] = kinds.get(kind, 0) + 1
        # Kotlin parser simulation
        b = bytes.fromhex(sec)
        k_ok = (
            len(b) == 16
            or (len(b) == 17 and b[0] == 0xDD)
            or (len(b) >= 18 and b[0] == 0xEE)
        )
        if k_ok:
            kotlin_parse_ok += 1
        try:
            socket.create_connection((host, int(port)), 3)
            tcp_ok += 1
            tcp_s = "Y"
        except OSError:
            tcp_s = "N"
        if tcp_s == "Y":
            print(f"  {host}:{port} kind={kind} secret={sec[:20]}... tcp=Y")

    print(f"SUMMARY tcp_ok={tcp_ok}/{len(proxies)} kotlin_parse_ok={kotlin_parse_ok}/{len(proxies)}")
    print("kinds:", kinds)


if __name__ == "__main__":
    main()
