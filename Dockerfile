FROM python:3.12-slim-bookworm

ENV PYTHONUNBUFFERED=1 \
    AURA_BIND=0.0.0.0 \
    AURA_PORT=10000 \
    AURA_WEB_ROOT=/app/website

# yt-dlp needs a JavaScript runtime for current YouTube formats.
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates nodejs \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir --upgrade pip \
    && pip install --no-cache-dir yt-dlp

WORKDIR /app
COPY backend/ ./backend/
COPY website/ ./website/

EXPOSE 10000
CMD ["python", "backend/server.py"]
