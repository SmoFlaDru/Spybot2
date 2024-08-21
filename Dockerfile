FROM python:3.11-slim

RUN apt-get update

RUN apt-get update && apt-get install -y \
    curl \
    pkg-config \
    gcc \
    mariadb-client \
    libmariadb-dev-compat \
    libmariadb-dev && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

RUN pip install uv

WORKDIR /app

COPY pyproject.toml pyproject.toml
RUN uv sync
