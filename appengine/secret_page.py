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

from google.appengine.api import users
from google.appengine.ext import webapp

from user_record import UserRecord
from helper import *

# Don't allow more than 25KB of secrets to be stored.  That should be *plenty*.
# If this value is changed, don't forget to update the unit test.
MAX_SIZE = 25 * 1024

class SecretsPage(webapp.RequestHandler):
  """
  Handler for the '/secret' URL.
  """

  def get(self):
    """
    HTTP GET handler.  Gets the secret for the given user from the database.
    Both the email and salt must match, otherwise the request is rejected. 
    """

    # If the request has a 'http_method' request parameter, handle it as if
    # that were the actual method of the request.  This is to get around
    # situations where we cannot directly do that method.  For now, this only
    # supports the DELETE method for use by the admin page.
    method = self.request.get('http_method', '')
    if method == 'DELETE':
      # We'll only support this if the user is logged in as an admin.
      if users.is_current_user_admin():
        self.delete()
      else:
        res = self.response
        res.set_status(403)
        res.out.write('Invalid credentials')

    else:
      res = self.response
      res.headers['Content-Type'] = 'text/plain'

      (email, salt) = GetHeaderFields(self.request)

      status = 403
      (record, exact) = FindEnabledUserRecord(email, salt)
      if record and exact:
        status = 200
        res.out.write(record.secrets)
      else:
        res.set_status(status)
        res.out.write('Invalid credentials')

      logging.info('GET email=%s salt=%s status=%d' % (email, salt, status))

  def put(self):
    """
    HTTP PUT  handler.  Saves the secrets for the given user into the database.
    If no record exists with the given email address, then a new record is
    created.  If a record does exist, then the salt in the record must match
    the salt of the request, otherwise we reject the put.
    """
    req = self.request
    res = self.response
    res.headers['Content-Type'] = 'text/plain'

    (email, salt) = GetHeaderFields(self.request)
    length = len(req.body)

    # If the length is greater than some (small) maximum, then reject the
    # put.  This is to prevent abuse of the server's storage.
    if length > MAX_SIZE:
      logging.info('PUT too large email=%s salt=%s length=%s' %
                   (email, salt, length))
      res.set_status(403)
      res.out.write('Invalid request')
      return

    validEmail = email and 0 < len(email)
    validSalt = salt and 0 < len(salt)
    status = 403

    # for the PUT to succeeded, we need to either find an exact match for a
    # user record, or find non at all.  An inexact match means the email was
    # found, but the salt does not match, so we don't trust this request.
    if validEmail and validSalt:
      (record, exact) = FindEnabledUserRecord(email, salt)
      if record and exact:
        record.secrets = req.body
        record.put()
        status = 200
      elif not record:
        record = UserRecord(email=email,salt=salt,secrets=req.body)
        record.enabled = True
        record.put()
        status = 200

    if 403 == status:
      res.set_status(status)
      res.out.write('Invalid credentials')

    logging.info('PUT email=%s salt=%s status=%d' % (email, salt, status))

  def delete(self):
    """
    HTTP DELETE handler.  Deletes the secrets for the given user from the
    database.  If no record exists with the given email address, then the delete
    is ignored.  If a record does exist, then the salt in the record must match
    the salt of the request, otherwise the delete is ignored.

    The email and salt are first extracted from the HTTP headers.  If not
    present, then they will be extracted from the request parameters.  This
    latter case is used for handling delete from the admin page.
    """
    res = self.response
    res.headers['Content-Type'] = 'text/plain'

    (email, salt) = GetHeaderFields(self.request)
    if not email:
      email = self.request.get('email', '')
      salt = self.request.get('salt', '')

    status = 200
    (record, exact) = FindEnabledUserRecord(email, salt)
    if record and exact:
      record.delete()
      res.out.write('Secrets deleted')
    else:
      status = 403
      res.set_status(status)
      res.out.write('Invalid credentials')

    logging.info('DELETE email=%s salt=%s status=%d' % (email, salt, status))
