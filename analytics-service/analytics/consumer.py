"""Run separately from HTTP. Offsets commit only after a successful OLAP commit."""
import logging
import signal
import threading
from confluent_kafka import Consumer, KafkaException
from .config import Settings
from .database import make_pool, migrate
from .models import SettlementEvent
from .projector import project

log = logging.getLogger(__name__)


def process_message(consumer, message, pool):
    event = SettlementEvent.model_validate_json(message.value())
    project(pool, event)
    consumer.commit(message=message, asynchronous=False)


def main():
    logging.basicConfig(level=logging.INFO)
    settings = Settings.load()
    pool = make_pool(settings)
    pool.open(wait=True)
    stop = threading.Event()
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, lambda *_: stop.set())
    consumer = Consumer({"bootstrap.servers": settings.kafka_servers, "group.id": settings.group,
                         "auto.offset.reset": "earliest", "enable.auto.commit": False, "enable.auto.offset.store": False})
    try:
        migrate(pool)
        consumer.subscribe([settings.topic])
        while not stop.is_set():
            message = consumer.poll(1)
            if message is None:
                continue
            if message.error():
                log.error("Kafka poll error: %s", message.error())
                if stop.wait(5):
                    break
                continue
            try:
                process_message(consumer, message, pool)
            except Exception:
                # Exit without committing this offset. Restart/rebalance replays it; never skip a financial event.
                # Payloads and client data are deliberately excluded from logs.
                log.exception("Analytics projection failed at partition=%s offset=%s; restart will retry", message.partition(), message.offset())
                raise
    finally:
        consumer.close()
        pool.close()


if __name__ == "__main__":
    main()
