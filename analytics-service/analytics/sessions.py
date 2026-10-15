"""Revocable server-side sessions. Only cookie digests are stored in PostgreSQL."""
import hashlib
import secrets

COOKIE = "JSESSIONID"


class Sessions:
    def __init__(self, pool):
        self.pool = pool

    @staticmethod
    def digest(raw):
        return hashlib.sha256(raw.encode()).hexdigest()

    def find(self, raw):
        if not raw or len(raw) > 128:
            return None
        with self.pool.connection() as conn:
            return conn.execute("UPDATE analytics.internal_sessions SET expires_at=now()+interval '30 minutes' WHERE token_hash=%s AND expires_at>now() RETURNING username,csrf_token", (self.digest(raw),)).fetchone()

    def create(self, username=None):
        raw, csrf = secrets.token_urlsafe(32), secrets.token_urlsafe(32)
        with self.pool.connection() as conn:
            conn.execute("DELETE FROM analytics.internal_sessions WHERE expires_at<now()")
            conn.execute("INSERT INTO analytics.internal_sessions(token_hash,csrf_token,username,expires_at) VALUES (%s,%s,%s,now()+interval '30 minutes')", (self.digest(raw), csrf, username))
        return raw, {"username": username, "csrf_token": csrf}

    def revoke(self, raw):
        if raw:
            with self.pool.connection() as conn:
                conn.execute("DELETE FROM analytics.internal_sessions WHERE token_hash=%s", (self.digest(raw),))
