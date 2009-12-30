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
from google.appengine.api import mail
from google.appengine.ext import webapp
from google.appengine.ext.webapp import template

from user_record import UserRecord
from helper import *


class SendEmailPage(webapp.RequestHandler):
  """
  Handler for the '/send_email' URL.  What this URL means is: send an email
  containing the salt to the email address given.  Don't send the salt in
  plain text though, make sure to encrypt it with the key given.  Its assumed
  that the recipient of the email will already know the key.

  This URL is used as part of the workflow for recovering the salt, should the
  user forget it, or need it because he switched to a new phone or had
  to do a hard reset of his existing phone.
  """

  def get(self):
    """Sends an email to specified email address.
    The email contains the salt that corresponds to the email. If the email is
    not found in the database, just ignore.
    """
    res = self.response
    res.headers['Content-Type'] = 'text/plain'
    email = self.request.get('email', '')
    key = self.request.get('key', '')
    status = 403
    (record, exact) = FindUserRecord(email, '')
    if record:
      # TODO(rogerta): Use the key to encrypt the salt before putting into the
      # email.  Make sure the key is "secure" enough.  The key is not sent in
      # the email.
      mail.send_mail('rogerta@gmail.com', record.email, 'Salt', record.salt)
      status = 200
      res.out.write('Email sent')
    else:
      res.set_status(status)
      res.out.write('Invalid credentials')
    logging.info('GET email=%s key=%s' % (email, key))
