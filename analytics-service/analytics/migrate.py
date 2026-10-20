"""Run existing OLAP migrations as a Compose startup job without API credentials."""
import os
from types import SimpleNamespace
from .database import make_pool, migrate


def main():
    settings = SimpleNamespace(
        db_url=os.environ["ANALYTICS_DB_URL"].removeprefix("jdbc:"),
        db_user=os.environ.get("ANALYTICS_DB_USER", "jarbis_olap_user"),
        db_password=os.environ["ANALYTICS_DB_PASSWORD"],
    )
    with make_pool(settings) as pool:
        migrate(pool)
    print("OLAP migrations are up to date", flush=True)


if __name__ == "__main__":
    main()
