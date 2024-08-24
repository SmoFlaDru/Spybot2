# Spybot 2

Spybot 2 is a rewrite of the original teamspeak server monitoring and statistics system in Python 3.11 using Django. The web frontend uses [Tabler.io](http://tabler.io) as the UI library.

## Dependencies
This project uses `uv` as a Python package manager. 
### Install project dependencies
To install all dependencies, first make sure that you are using Python 3.11. Then install uv using `pip install uv`.
Then install all project dependencies using `uv sync`.

### Add a new dependency
Execute `uv add mydependency`, then commit the files `pyproject.toml` and `uv.lock`.

