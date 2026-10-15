FROM python:3.12-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt && useradd --create-home analytics
COPY analytics ./analytics
COPY migrations ./migrations
COPY application.properties .
USER analytics
EXPOSE 8082
CMD ["uvicorn", "analytics.api:create_app", "--factory", "--host", "0.0.0.0", "--port", "8082"]
