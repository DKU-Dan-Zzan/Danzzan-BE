#!/usr/bin/env python3
import json
import os
import sys
import urllib.error
import urllib.request


BASE_URL = os.getenv("BASE_URL", "http://localhost:8080").rstrip("/")
LOGIN_URL = f"{BASE_URL}/user/login"
OUTPUT_FILE = os.getenv("OUTPUT_FILE", "k6-test/tokens_load_300_fresh.json")
STUDENT_ID_PREFIX = os.getenv("STUDENT_ID_PREFIX", "TEST")
COUNT = int(os.getenv("COUNT", "300"))
START_INDEX = int(os.getenv("START_INDEX", "1"))
PASSWORD = os.getenv("PASSWORD", "REDACTED_LOCAL_ONLY")
TIMEOUT_SECONDS = float(os.getenv("TIMEOUT_SECONDS", "5"))


def build_student_id(index: int) -> str:
    return f"{STUDENT_ID_PREFIX}{index:04d}"


def request_token(student_id: str) -> str:
    payload = json.dumps({"studentId": student_id, "password": PASSWORD}).encode("utf-8")
    req = urllib.request.Request(
        LOGIN_URL,
        data=payload,
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT_SECONDS) as response:
        raw = response.read()
    body = json.loads(raw.decode("utf-8"))
    token = body.get("accessToken")
    if not token:
        raise RuntimeError(f"accessToken missing for studentId={student_id}")
    return token


def main() -> int:
    tokens = []
    failures = []

    for offset in range(COUNT):
        student_id = build_student_id(START_INDEX + offset)
        try:
            token = request_token(student_id)
            tokens.append(token)
        except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, RuntimeError, json.JSONDecodeError) as exc:
            failures.append((student_id, str(exc)))

        if (offset + 1) % 25 == 0 or offset + 1 == COUNT:
            print(f"processed={offset + 1}/{COUNT} success={len(tokens)} failures={len(failures)}")

    if failures:
        print("login failures detected:")
        for student_id, error in failures[:10]:
            print(f"- {student_id}: {error}")
        print(f"total_failures={len(failures)}")
        return 1

    with open(OUTPUT_FILE, "w", encoding="utf-8") as fp:
        json.dump(tokens, fp, ensure_ascii=False, indent=2)

    print(f"tokens_written={len(tokens)} file={OUTPUT_FILE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
