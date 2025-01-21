FROM node:23-slim AS frontend-build

COPY frontend frontend
WORKDIR frontend
RUN npm install
RUN npm run package

FROM python:3.12-slim-bookworm

RUN groupadd -g 1234 spybot && useradd -m -u 1234 -g spybot spybot

WORKDIR /home/spybot

ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

EXPOSE 8000

RUN pip install uv
COPY pyproject.toml pyproject.toml
RUN uv sync
COPY --chown=spybot:spybot . .

COPY --chown=spybot:spybot --from=frontend-build frontend/output frontend/output

USER spybot
RUN mkdir spybot_static
CMD ["sh", "run.sh"]