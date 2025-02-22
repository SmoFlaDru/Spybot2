from testcontainers.postgres import PostgresContainer

from Spybot2 import settings

from xmlrunner.extra.djangotestrunner import XMLTestRunner


class TestContainerRunner(XMLTestRunner):
    postgres_container: PostgresContainer = None

    def setup_databases(self, **kwargs):
        self.postgres_container = PostgresContainer(image="postgres:17.2")
        self.postgres_container.start()

        db_connection_settings = {
            "USER": self.postgres_container.username,
            "PASSWORD": self.postgres_container.password,
            "HOST": self.postgres_container.get_container_host_ip().replace(
                "localhost", "127.0.0.1"
            ),
            "PORT": self.postgres_container.get_exposed_port(
                self.postgres_container.port
            ),
        }
        settings.DATABASES["default"].update(db_connection_settings)

        return super().setup_databases(**kwargs)

    def teardown_databases(self, old_config, **kwargs):
        super().teardown_databases(old_config, **kwargs)

        self.postgres_container.stop()
