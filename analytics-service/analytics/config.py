"""One explicit configuration path: properties defaults, then environment overrides."""
from dataclasses import dataclass
from pathlib import Path
import os


@dataclass(frozen=True)
class Settings:
    db_url: str
    db_user: str
    db_password: str
    username: str
    password_hash: str
    origins: tuple[str, ...] = ()
    secure_cookie: bool = False
    operations_username: str = ""
    operations_password_hash: str = ""

    def accounts(self):
        users = {self.username: (self.password_hash, "ANALYST")}
        if self.operations_username:
            users[self.operations_username] = (self.operations_password_hash, "OPERATIONS")
        return users

    @classmethod
    def load(cls):
        values = {}
        path = Path(os.getenv("ANALYTICS_PROPERTIES_FILE", Path(__file__).resolve().parents[1] / "application.properties"))
        if path.exists():
            for line in path.read_text().splitlines():
                if line.strip() and not line.lstrip().startswith("#"):
                    key, value = line.split("=", 1)
                    values[key.strip()] = value.strip()
        values.update(os.environ)
        def required(key):
            if not values.get(key):
                raise ValueError(f"Configure {key} in the environment or properties file")
            return values[key]
        origins = tuple(s.strip() for s in values.get("ANALYTICS_ALLOWED_ORIGINS", "").split(",") if s.strip())
        if any("*" in s or not s.startswith(("http://", "https://")) for s in origins):
            raise ValueError("Use explicit http(s) frontend origins, not wildcards")
        password_hash = required("ANALYTICS_ADMIN_PASSWORD_HASH")
        if not password_hash.startswith(("$2a$", "$2b$", "$2y$")) or len(password_hash) != 60:
            raise ValueError("ANALYTICS_ADMIN_PASSWORD_HASH must be a BCrypt hash")
        operations_user = values.get("ANALYTICS_OPERATIONS_USER", "")
        operations_hash = values.get("ANALYTICS_OPERATIONS_PASSWORD_HASH", "")
        if bool(operations_user) != bool(operations_hash):
            raise ValueError("Configure both operations username and password hash")
        if operations_user and (operations_user == values.get("ANALYTICS_ADMIN_USER", "admin") or len(operations_hash) != 60 or not operations_hash.startswith(("$2a$", "$2b$", "$2y$"))):
            raise ValueError("Operations needs a distinct username and BCrypt hash")
        return cls(required("ANALYTICS_DB_URL").removeprefix("jdbc:"), values.get("ANALYTICS_DB_USER", "jarbis_olap_user"),
                   required("ANALYTICS_DB_PASSWORD"), values.get("ANALYTICS_ADMIN_USER", "admin"), password_hash,
                   origins, values.get("ANALYTICS_SECURE_COOKIE", "false").lower() == "true",
                   operations_user, operations_hash)
