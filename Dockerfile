FROM python:3.12.5-bookworm

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

RUN pip install uv
COPY pyproject.toml pyproject.toml
RUN uv sync
COPY . .
