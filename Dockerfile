FROM node:23-slim AS frontend-build

COPY frontend frontend
WORKDIR frontend
RUN npm install
RUN npm run package

FROM python:3.12-slim-bookworm

WORKDIR /app

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

# Install mysqlclient debian package dependencies
RUN apt-get update && apt-get install -y --no-install-recommends python3-dev default-libmysqlclient-dev build-essential pkg-config && rm -rf /var/lib/apt/lists/*
RUN pip install uv
COPY pyproject.toml pyproject.toml
RUN uv sync
COPY . .

COPY --from=frontend-build frontend/output frontend/output

CMD ["sh", "run.sh"]