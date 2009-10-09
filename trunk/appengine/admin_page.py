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

import logging
import os

from google.appengine.api import users
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from user_record import UserRecord

class AdminPage(webapp.RequestHandler):
  """Handler for the '/admin' URL."""

  def get(self):
    """Shows a list of all Emails and salts saved on the server."""
    path = os.path.join(os.path.dirname(__file__), 'static/admin.html')

    # Builds a list of all records.  Need to see how scalable this is.  Put
    # the list into a template for use by the templating system.
    query = UserRecord.all()
    records = []
    records.extend(query)
    template_values = {
      'records': records,
      'logout_url': users.create_logout_url('/admin')
    }
    res = self.response
    # Make sure no one tries to cache this page, neither the browser nor any
    # intermediate proxies.
    res.headers.add_header('Cache-Control', 'no-cache')
    res.headers.add_header('Pragma', 'no-cache')

    # Render the admin page template.
    res.out.write(template.render(path, template_values))
