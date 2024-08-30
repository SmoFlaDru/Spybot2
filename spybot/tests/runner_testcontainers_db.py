from testcontainers.mysql import MySqlContainer

from Spybot2 import settings

from xmlrunner.extra.djangotestrunner import XMLTestRunner


class TestContainerRunner(XMLTestRunner):
    mysql_container: MySqlContainer = None

    def setup_databases(self, **kwargs):
        self.mysql_container = MySqlContainer(image="mariadb:11.0")
        self.mysql_container.start()

        db_connection_settings = {
            'USER': 'root',
            'PASSWORD': self.mysql_container.root_password,
            'HOST': self.mysql_container.get_container_host_ip().replace("localhost", "127.0.0.1"),
            'PORT': self.mysql_container.get_exposed_port(3306),
        }
        settings.DATABASES['default'].update(db_connection_settings)

        return super().setup_databases(**kwargs)

    def teardown_databases(self, old_config, **kwargs):
        super().teardown_databases(old_config, **kwargs)

        self.mysql_container.stop()
