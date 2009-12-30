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

from google.appengine.ext import webapp

from user_record import UserRecord
from helper import *

class EnablePage(webapp.RequestHandler):
  """Handler for the '/enable' URL."""

  def get(self):
    # Determine whether this record should be eanbled or disabled.
    enabled = int(self.request.get('enable', 0)) != 0
    res = self.response
    res.headers['Content-Type'] = 'text/plain'

    # Determine which record is affected.  First use the HTTP header fields,
    # then the request parameters.  The latter is used by the admin page.
    (email, salt) = GetHeaderFields(self.request)
    if not email:
      email = self.request.get('email', '')
      salt = self.request.get('salt', '')

    status = 403
    (record, exact) = FindUserRecord(email, salt)
    if record and exact:
      record.enabled = enabled
      record.put()
      status = 200
      if enabled:
        res.out.write('User enabled')
      else:
        res.out.write('User disabled')
    else:
      res.set_status(status)
      res.out.write('Invalid credentials')

    logging.info('enable email=%s salt=%s enable=%d' % (email, salt, enabled))
