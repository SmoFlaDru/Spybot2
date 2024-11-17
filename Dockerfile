FROM python:3.12.5-bookworm

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE 1
ENV PYTHONUNBUFFERED 1

EXPOSE 8000

RUN pip install --progress-bar off uv
COPY pyproject.toml pyproject.toml
RUN uv sync
COPY . .

CMD uv run manage.py runserver 127.0.0.1:8000