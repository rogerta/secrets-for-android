# Copyright (c) 2009, Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

from google.appengine.ext import webapp
from google.appengine.ext.webapp.util import run_wsgi_app

from secret_page import SecretsPage
from admin_page import AdminPage
from send_email_page import SendEmailPage
from enable_page import EnablePage

URLS = [
  ('/secrets', SecretsPage),
  ('/admin', AdminPage),
  ('/send_email', SendEmailPage),
  ('/enable', EnablePage),
]

application = webapp.WSGIApplication(URLS, debug=True)


def main():
  """
  Only handler files with a main function are cached by appengine.  Therefore
  using a main function for this purpose.
  """
  run_wsgi_app(application)


if __name__ == "__main__":
  main()
