
#!/usr/bin/env python3
import os
import socket

import pymysql


REDIS_HOST = os.getenv("REDIS_HOST", "127.0.0.1")
REDIS_PORT = int(os.getenv("REDIS_PORT", "6379"))
DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_NAME = os.getenv("DB_NAME", "festival_test")
DB_USER = os.getenv("DB_USER", "admin")
DB_PASSWORD = os.getenv("DB_PASSWORD", "REDACTED_LOCAL_ONLY")

EVENT_ID = os.getenv("EVENT_ID", "1")
STOCK = os.getenv("STOCK", "3000")
STUDENT_ID_PREFIX = os.getenv("STUDENT_ID_PREFIX", "TEST")
LIMIT = int(os.getenv("LIMIT", "8000"))


def encode_command(*parts: str) -> bytes:
    encoded = [f"*{len(parts)}\r\n".encode()]
    for part in parts:
        raw = str(part).encode()
        encoded.append(f"${len(raw)}\r\n".encode())
        encoded.append(raw + b"\r\n")
    return b"".join(encoded)


def read_reply(sock: socket.socket):
    line = b""
    while not line.endswith(b"\r\n"):
        chunk = sock.recv(1)
        if not chunk:
            raise RuntimeError("redis connection closed")
        line += chunk
    prefix = line[:1]
    body = line[1:-2]
    if prefix == b"+":
        return body.decode()
    if prefix == b":":
        return int(body)
    if prefix == b"$":
        length = int(body)
        if length == -1:
            return None
        data = b""
        while len(data) < length + 2:
            data += sock.recv(length + 2 - len(data))
        return data[:-2].decode()
    if prefix == b"-":
        raise RuntimeError(body.decode())
    raise RuntimeError(f"unsupported redis reply: {line!r}")


def fetch_user_ids():
    conn = pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASSWORD,
        database=DB_NAME,
        charset="utf8mb4",
    )
    try:
        with conn.cursor() as cursor:
            cursor.execute(
                "select id from users where student_id like %s order by student_id asc limit %s",
                (f"{STUDENT_ID_PREFIX}%", LIMIT),
            )
            user_ids = [str(row[0]) for row in cursor.fetchall()]
            if user_ids:
                placeholders = ",".join(["%s"] * len(user_ids))
                cursor.execute(
                    f"delete from user_tickets where event_id = %s and user_id in ({placeholders})",
                    [EVENT_ID, *user_ids],
                )
            safe_execute(
                cursor,
                "delete from ticket_queue_entries where event_id = %s",
                (EVENT_ID,),
            )
            try:
                cleanup_async_issue_artifacts(cursor)
            except pymysql.err.ProgrammingError:
                # 테이블이 아직 생성되지 않은 초기 부트 상태에서는 스킵
                pass
            conn.commit()
            return user_ids
    finally:
        conn.close()


def cleanup_async_issue_artifacts(cursor):
    cursor.execute(
        """
        select request_id
        from ticket_issue_requests
        where event_id = %s
        """,
        (EVENT_ID,),
    )
    request_ids = [row[0] for row in cursor.fetchall()]
    if request_ids:
        req_placeholders = ",".join(["%s"] * len(request_ids))
        cursor.execute(
            f"""
            delete from outbox_events
            where aggregate_type = 'TICKET_ISSUE'
              and aggregate_id in ({req_placeholders})
            """,
            request_ids,
        )
        cursor.execute(
            f"delete from ticket_issue_compensation_logs where request_id in ({req_placeholders})",
            request_ids,
        )
    cursor.execute(
        """
        delete from ticket_issue_requests
        where event_id = %s
        """,
        (EVENT_ID,),
    )


def safe_execute(cursor, query, args):
    try:
        cursor.execute(query, args)
    except pymysql.err.ProgrammingError:
        # 테이블이 없는 경우(초기 스키마/구버전)에는 무시
        pass


def main():
    user_ids = fetch_user_ids()
    keys = [
        f"ticket:{EVENT_ID}:queue",
        f"ticket:{EVENT_ID}:ready",
        f"ticket:{EVENT_ID}:active",
        f"ticket:{EVENT_ID}:seq",
        f"ticket:{EVENT_ID}:closed-cleanup",
    ]
    for user_id in user_ids:
        keys.extend([
            f"ticket:{EVENT_ID}:quser:{user_id}",
            f"ticket:{EVENT_ID}:dedup:{user_id}",
            f"ticket:{EVENT_ID}:user:{user_id}",
            f"ticket:{EVENT_ID}:status:{user_id}",
        ])

    with socket.create_connection((REDIS_HOST, REDIS_PORT), timeout=5) as sock:
        read_reply_after_send(sock, "DEL", *keys)
        read_reply_after_send(sock, "SET", f"ticket:{EVENT_ID}:stock", STOCK)

    print(f"event={EVENT_ID} cleared_keys={len(keys)} stock={STOCK} users={len(user_ids)}")


def read_reply_after_send(sock: socket.socket, *parts: str):
    sock.sendall(encode_command(*parts))
    return read_reply(sock)


if __name__ == "__main__":
    main()
