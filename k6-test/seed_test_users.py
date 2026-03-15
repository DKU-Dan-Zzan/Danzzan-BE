#!/usr/bin/env python3
import os
from datetime import datetime

import bcrypt
import pymysql


DB_HOST = os.getenv("DB_HOST", "127.0.0.1")
DB_PORT = int(os.getenv("DB_PORT", "3306"))
DB_NAME = os.getenv("DB_NAME", "festival_test")
DB_USER = os.getenv("DB_USER", "admin")
DB_PASSWORD = os.getenv("DB_PASSWORD", "dan2026zzan!")

COUNT = int(os.getenv("COUNT", "200"))
PASSWORD = os.getenv("PASSWORD", "test1234")
STUDENT_ID_PREFIX = os.getenv("STUDENT_ID_PREFIX", "TEST")


def main():
    password_hash = bcrypt.hashpw(PASSWORD.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")
    now = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    connection = pymysql.connect(
        host=DB_HOST,
        port=DB_PORT,
        user=DB_USER,
        password=DB_PASSWORD,
        database=DB_NAME,
        charset="utf8mb4",
        autocommit=False,
    )

    try:
        with connection.cursor() as cursor:
            for i in range(1, COUNT + 1):
                student_id = f"{STUDENT_ID_PREFIX}{i:04d}"
                cursor.execute(
                    """
                    INSERT INTO users (
                        student_id,
                        password,
                        name,
                        college,
                        major,
                        academic_status,
                        role,
                        token_version,
                        created_at
                    ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)
                    ON DUPLICATE KEY UPDATE
                        password = VALUES(password),
                        name = VALUES(name),
                        college = VALUES(college),
                        major = VALUES(major),
                        academic_status = VALUES(academic_status),
                        role = VALUES(role)
                    """,
                    (
                        student_id,
                        password_hash,
                        f"Load User {i}",
                        "Engineering",
                        "Computer Science",
                        "ENROLLED",
                        "ROLE_USER",
                        0,
                        now,
                    ),
                )
        connection.commit()
        print(f"seeded_or_updated={COUNT}")
    finally:
        connection.close()


if __name__ == "__main__":
    main()
