#
# DESCRIPTION:
#
#  Locust wont start without a file to bootstrap it, so we can provide this "dummy" file which has no impact
#  on the Kotlin tasks in this project.
#
# USAGE:
#
#  locust -f locust-master.py --master --master-bind-host=127.0.0.1 --master-bind-port=5557
#
from locust import User, task

class Locust4k(User):
    @task(1)
    def workerTask(self):
        pass
