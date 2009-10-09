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

from google.appengine.ext import db

class UserRecord(db.Model):
  """
  This class holds information about one Secrets for Android user.  The email
  member uniquely identifies a user, while the salt is used to authenticate
  that user, much like a password.

  For now only a blob (base64 encoded) with the user's secrets is stored.  Later
  on other information may be added.  Note that the secrets blob is encoded
  with the user's master password, which we don't know here.  This is treated
  as an opaque blob from this app engine site.
  """

  # The email and salt are used to identify the user.  The email acts like a
  # unique id, and the salt is like a password.
  email = db.StringProperty(required=True)
  salt = db.StringProperty(required=True)

  # Track when the record was first created and last modified.
  created = db.DateTimeProperty(auto_now_add=True)
  last_modified = db.DateTimeProperty(auto_now=True)

  # Properties of the record,  For now, the actual secrets (encrypted form)
  # and whether the record is enabled or not.
  secrets = db.TextProperty()
  enabled = db.BooleanProperty()
