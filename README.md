# Spybot 2

This repository now contains two application generations:

- The original Django/Python implementation in the existing `spybot` and `Spybot2` packages.
- An in-progress Spring Boot + Kotlin rewrite in `spybot-core`, `spybot-web`, and `spybot-recorder`.

The Spring rewrite uses Spring MVC, Spring Security, jOOQ, Flyway, and JTE templates, while preserving the current PostgreSQL schema for the first release.

## Spring rewrite modules

- `spybot-core`: shared query layer, domain models, security principal, Flyway baseline, and jOOQ generation config.
- `spybot-web`: MVC app, JTE views, REST endpoints, security config, and scheduled jobs.
- `spybot-recorder`: dedicated recorder process scaffold for the TeamSpeak listener.

## Dependencies
This project uses `uv` as a Python package manager. 

### Install project dependencies
To install all dependencies, first make sure that you are using Python 3.11. Then install uv using `pip install uv`.
Then install all project dependencies using `uv sync`.

### Add a new dependency
Execute `uv add mydependency`, then commit the files `pyproject.toml` and `uv.lock`.

### Code style
We use `ruff` for code formatting. The code style is enforced in pull requests.
To install the pre-commit hook, use `pre-commit install`.
