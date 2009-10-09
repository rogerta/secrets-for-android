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

from user_record import UserRecord

HEADER_EMAIL = 'X-Secrets-Email'
HEADER_SALT = 'X-Secrets-Salt'


def GetHeaderFields(req):
  """
  Gets the fields required to identify the user from the HTTP request.

  Args:
    req: The HTTP request received.

  Returns:
    A tuple of (email,salt) retrieved from the request, where email and
    salt are both strings.
  """
  email = HEADER_EMAIL in req.headers and req.headers[HEADER_EMAIL]
  salt = HEADER_SALT in req.headers and req.headers[HEADER_SALT]

  return (email, salt)


def FindUserRecord(email, salt):
  """
  Find the user record in the data store, given the email and salt.  A
  record will be returned if it matches both the email and salt (exact
  match) or if it matches only the email (inexact match).

  Args:
    email: a string containing the email address of the user.
    salt: the corresponding salt of the user.
  Returns:
    A tuple of (record,exact), where record is of type UserRecord and
    exact is a boolean.  If not record is found, (None,False) is returned.
  """
  record = None
  exact = False

  # Look for a user record with the given email address.  If we found one,
  # then verify that the salt is correct.  There should be at most one record
  # that matches this email, so don't waste any time/resources looking for
  # more.
  if email and 0 < len(email):
    query = UserRecord.all()
    query.filter('email =', email)
    result = query.fetch(1)
    if result:
      record = result[0]
      exact = record.salt == salt

  return (record, exact)


def FindEnabledUserRecord(email, salt):
  """
  Find the *enabled* user record in the data store, given the email and salt.
  A record will be returned if it matches both the email and salt (exact
  match) or if it matches only the email (inexact match), and if its enabled.

  Args:
    email: a string containing the email address of the user.
    salt: the corresponding salt of the user.
  Returns:
    A tuple of (record,exact), where record is of type UserRecord and
    exact is a boolean.  If not record is found, (None,False) is returned.
  """
  (record, exact) = FindUserRecord(email, salt)
  if record and record.enabled:
    return (record, exact)
  return (None, False)
