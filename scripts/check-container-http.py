"""Read-only checks against the local container stack; never submit analyses."""
import argparse
import json
from urllib.request import urlopen


def read(base, path):
    with urlopen(base + path, timeout=15) as response:
        if response.status != 200:
            raise RuntimeError(f"{path}: HTTP {response.status}")
        return response.headers.get_content_type(), response.read().decode("utf-8")


def check(base):
    content_type, body = read(base, "/api/health")
    if content_type != "application/json" or json.loads(body).get("status") != "UP":
        raise RuntimeError("API/PostgreSQL readiness failed through Nginx")

    content_type, body = read(base, "/api/analyses?page=0&size=20")
    history = json.loads(body)
    if content_type != "application/json" or not isinstance(history.get("items"), list):
        raise RuntimeError("History API response failed through Nginx")
    if history.get("page") != 0 or history.get("size") != 20:
        raise RuntimeError("History pagination contract failed")

    for path in ("/", "/historico", "/metodologia"):
        content_type, body = read(base, path)
        if content_type != "text/html" or '<div id="root"></div>' not in body:
            raise RuntimeError(f"SPA document missing at {path}")
        if '/assets/' not in body:
            raise RuntimeError(f"Compiled frontend assets missing at {path}")
    print("HTTP checks passed: API/PostgreSQL, history and SPA documents through Nginx.")
    print("Browser rendering, scans and persistence after restart remain separate checks.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:3000")
    args = parser.parse_args()
    check(args.base_url.rstrip("/"))
