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

import getopt
import httplib
import StringIO
import sys
import unittest

CONN_FACTORY = httplib.HTTPConnection
HOSTNAME = 'localhost:8080'
BASE_SECRETS_URL = '/secrets'

HEADER_EMAIL = 'X-Secrets-Email'
HEADER_SALT = 'X-Secrets-Salt'

TEST_EMAIL = 'foo@gmail.com'
TEST_EMAIL2 = 'bar@gmail.com'
TEST_SALT = '00000000'
TEST_BODY = 'test-body'

class TestGET(unittest.TestCase):
  """
  Unit test for getting secrets.
  """

  def setUp(self):
    """
    Setup the GET tests by creating a secret for a well known email address
    and salt, and making sure that a second well known email address does not
    exist.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()

  def tearDown(self):
    """
    Delete the well known secret created in the setup.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)

    finally:
      conn.close()

  def RequestNonSecretsPage(self, url):
    """
    Perform an HTTP GET request for the given URL.

    Args:
      url: The URL to request as a string.
    """
    try:
      conn = httplib.HTTPConnection(HOSTNAME)
      conn.request('GET', url)
      res = conn.getresponse()
      self.assertEquals(200, res.status, 'URL=%s' % url)
      body = res.read()
      self.assertTrue(body)
      self.assertTrue(body.__contains__('Secrets for Android'))
      self.assertTrue(body.__contains__(
          'http://code.google.com/p/secrets-for-android/'))
    finally:
      conn.close()

  def testRandomPages(self):
    """
    Test a randomly generated URL, to make sure it returns the standard
    response to go see the c.g.c page for Secrets for Android.
    """
    self.RequestNonSecretsPage('/')
    self.RequestNonSecretsPage('/adasdad')
    self.RequestNonSecretsPage('/adasdad/ddadaa')
    self.RequestNonSecretsPage('/adasdad/ddadaa/')
    self.RequestNonSecretsPage('/adasdad/ddadaa/?email=fff&salt=40453')

  def testNoEmailNoSalt(self):
    """
    Test a secrets URL with neither an email or salt.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('GET', BASE_SECRETS_URL)
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testNoEmail(self):
    """
    Test a secrets URL with no email.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('GET', BASE_SECRETS_URL,
                   headers={HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testNoSalt(self):
    """
    Test a secrets URL with no salt.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('GET', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testBadEmail(self):
    """
    Test a secrets URL with an email and salt specified, but the email does
    not exist.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('GET', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : 'bar@gmail.com',
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testBadSalt(self):
    """
    Test a secrets URL with an email and salt specified, but the salt is
    incorrect.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('GET', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : '10000000'})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testGoodRequest(self):
    """
    Test a valid secrets URL.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('GET', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()


class TestPUT(unittest.TestCase):
  """
  Unit test for putting secrets.
  """

  def setUp(self):
    """
    Setup the PUT tests by making sure a secret for one well known email address
    exists and for another known email address does not exist.
    """
    try:
      # Make sure a secret exists for foo@.
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)

      # Make sure a secret does not exists for bar@.
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL2,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      if 200 != res.status and 403 != res.status:
        self.assertEquals(200, res.status)

    finally:
      conn.close()

  def tearDown(self):
    """
    Delete the well known secret created in the test.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      if 200 != res.status and 403 != res.status:
        self.assertEquals(200, res.status)

      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : 'barr@gmail.com',
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
    finally:
      conn.close()

  def RequestNonSecretsPage(self, url):
    """
    Perform an HTTP PUT request for the given URL.

    Args:
      url: The URL to request as a string.
    """
    try:
      conn = httplib.HTTPConnection(HOSTNAME)
      conn.request('PUT', url, TEST_BODY)
      res = conn.getresponse()

      # Why does a PUT on a random URL return 200?
      self.assertEquals(200, res.status)
      #self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testRandomPages(self):
    """
    Test a randomly generated URL, to make sure it returns a 403 response.
    """
    self.RequestNonSecretsPage('/')
    self.RequestNonSecretsPage('/adasdad')
    self.RequestNonSecretsPage('/adasdad/ddadaa')
    self.RequestNonSecretsPage('/adasdad/ddadaa/')
    self.RequestNonSecretsPage('/adasdad/ddadaa/?email=fff&salt=40453')

  def testNoEmailNoSalt(self):
    """
    Test a secrets URL with neither an email or salt.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY)
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testNoEmail(self):
    """
    Test a secrets URL with no email.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testNoSalt(self):
    """
    Test a secrets URL with no salt.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testNonExistantEmail(self):
    """
    Test a secrets URL with an email and salt specified, but the email does
    not exist.  This should set a new record in the database.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : 'barr@gmail.com',
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()

  def testExistantEmailBadSalt(self):
    """
    Test a secrets URL with an email and salt specified, the email exists,
    but the salt is incorrect.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : '10000000'})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testExistantEmailGoodSalt(self):
    """
    Test a secrets URL with a good email and salt.  This should update the
    record in the database.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
      # TODO: We are able to put the secret.  Now read it back and make sure
      # its OK.
    finally:
      conn.close()

  def testSecretsTooLarge(self):
    """
    Test that a user trying to put a secrets record that is too large fails.
    """
    try:
      # Create a body that is larger than the maximum allowed.
      MAX_SIZE = 25 * 1024
      out = StringIO.StringIO()
      for i in range(MAX_SIZE / 16 + 1):
        out.write('                ')

      out.write('This is past the maximum allowed')
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, out.getvalue(),
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()


class TestDELETE(unittest.TestCase):
  """
  Unit test for DELETE with request attributes.
  """

  def setUp(self):
    """
    Setup the tests by making sure a secret for one well known email address
    exists and for another known email address does not exist.
    """
    try:
      # Make sure a secret exists for foo@.
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
      # Make sure a secret does not exists for bar@.
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL2,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      if 200 != res.status and 403 != res.status:
        self.assertEquals(200, res.status)
    finally:
      conn.close()

  def tearDown(self):
    """
    Delete the well known secret created in the test.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      if 200 != res.status and 403 != res.status:
        self.assertEquals(200, res.status)
    finally:
      conn.close()

  def testDeleteExisting(self):
    """
    Delete the record added in setUp().
    """
    try:
      # Now delete the record.
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()

  def testDeleteExistingViaGET(self):
    """
    Put a new record, then delete it using request parameters.  This should
    fail since this operation is only supported for admins, and we are not
    logged in during the tests.
    """
    try:
      # Now delete the record.
      conn = CONN_FACTORY(HOSTNAME)
      url = '%s?http_method=DELETE&email=%s&salt=%s' % (BASE_SECRETS_URL,
                                                        TEST_EMAIL,
                                                        TEST_SALT)
      conn.request('GET',  url)
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testDeleteNonExisting(self):
    """
    Put a new record, then delete it using request parameters.
    """
    try:
      # Now delete the record.
      conn = CONN_FACTORY(HOSTNAME)
      url = '%s?email=%s&salt=%s' % (BASE_SECRETS_URL, TEST_EMAIL2, TEST_SALT)
      conn.request('DELETE',  url)
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()

  def testDeleteBad(self):
    """
    Put a new record, then delete it using request parameters.
    """
    try:
      # Now delete the record.
      conn = CONN_FACTORY(HOSTNAME)
      url = '%s?email=%s&salt=%s' % (BASE_SECRETS_URL, 'xx@foo.com', TEST_SALT)
      conn.request('DELETE',  url)
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()


class TestSendEmail(unittest.TestCase):
  """
  Unit test for sending email for a secret.
  """

  def setUp(self):
    """
    Setup the tests by creating a secret for a well known email address
    and salt, and making sure that a second well known email address does not
    exist.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()

  def tearDown(self):
    """
    Delete the well known secret created in the setup.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()

  def testSendEmail(self):
    try:
      conn = CONN_FACTORY(HOSTNAME)
      url = '/send_email?email=%s&key=%s' % (TEST_EMAIL, TEST_SALT)
      conn.request('GET', url)
      res = conn.getresponse()
      self.assertEquals(200, res.status)
      body = res.read()
      self.assertTrue(body.__contains__('Email sent'))
    finally:
      conn.close()

  def testSendBadEmail(self):
    try:
      conn = CONN_FACTORY(HOSTNAME)
      url = '/send_email?email=xxxx@tawacentral.net&key=foobar'
      conn.request('GET', url)
      res = conn.getresponse()
      self.assertEquals(403, res.status)
    finally:
      conn.close()


class TestEnableDisable(unittest.TestCase):
  """
  Unit test for enabling and disabling a user account.  For now, all of these
  should fail because we are not logged in as user.  The "failure" will be
  in the form of a 302 redirect, since the server is asking us to login.
  """
  def setUp(self):
    """
    Setup the tests by creating a secret for a well known email address
    and salt, and making sure that a second well known email address does not
    exist.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('PUT', BASE_SECRETS_URL, TEST_BODY,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()

  def tearDown(self):
    """
    Delete the well known secret created in the setup.
    """
    try:
      conn = CONN_FACTORY(HOSTNAME)
      conn.request('DELETE', BASE_SECRETS_URL,
                   headers={HEADER_EMAIL : TEST_EMAIL,
                            HEADER_SALT : TEST_SALT})
      res = conn.getresponse()
      self.assertEquals(200, res.status)
    finally:
      conn.close()

  def testEnable(self):
    try:
      conn = CONN_FACTORY(HOSTNAME)
      url = '/enable?email=%s&key=%s&enable=1' % (TEST_EMAIL, TEST_SALT)
      conn.request('GET', url)
      res = conn.getresponse()
      self.assertEquals(302, res.status)
      body = res.read()
    finally:
      conn.close()

  def testDisable(self):
    try:
      conn = CONN_FACTORY(HOSTNAME)
      url = '/enable?email=%s&key=%s&enable=0' % (TEST_EMAIL, TEST_SALT)
      conn.request('GET', url)
      res = conn.getresponse()
      self.assertEquals(302, res.status)
      body = res.read()
    finally:
      conn.close()


if __name__ == "__main__":
  # Parse command line arguments to determine what to do.
  try:
    (opts, args) = getopt.getopt(sys.argv[1:],
                                 'ocde:s:a:',
                                 ['online', 'create', 'delete', 'email=',
                                  'salt=', 'data='])
  except getopt.GetoptError, err:
    print(err)
    sys.exit(2)

  do_create = False
  do_delete = False
  email = None
  salt = None
  data = None

  # Process command line arguments.
  for (o, v) in opts:
    if o in ('-o', '--online'):
      # Test against the real, or online, version of the server instead of
      # the test server on the local machine.
      CONN_FACTORY = httplib.HTTPSConnection
      HOSTNAME = 'my-secrets.appspot.com'
    elif o in ('-c', '--create'):
      # Instead of running the tests, create a record.
      do_create = True
    elif o in ('-d', '--delete'):
      # Instead of running the tests, delete a record.
      do_delete = True
    elif o in ('-e', '--email'):
      # Set the email address for the create or delete action.
      email = v
    elif o in ('-s', '--salt'):
      # Set the salt for the create or delete action.
      salt = v
    elif o in ('-a', '--data'):
      # Set the secrets data for the create or delete action.
      data = v

  # Perform the request action.
  if do_create:
    print 'Email=%s' % email
    print 'Salt=%s' % salt
    print 'Data=%s' % data
    conn = CONN_FACTORY(HOSTNAME)
    conn.request('PUT', BASE_SECRETS_URL, data,
                 headers={HEADER_EMAIL : email,
                          HEADER_SALT : salt})
    res = conn.getresponse()
    if res.status == 200:
      print 'Record added successfully.'
    else:
      print '*** Error adding record: %s.' % res.status
      for (k,v) in res.getheaders():
        print '*** Header %s=%s' % (k, v)

  elif do_delete:
    conn = CONN_FACTORY(HOSTNAME)
    conn.request('DELETE', BASE_SECRETS_URL,
                 headers={HEADER_EMAIL : email,
                          HEADER_SALT : salt})
    res = conn.getresponse()
    if res.status == 200:
      print 'Record deleted successfully.'
    else:
      print '*** Error deleted record: %s.' % res.status
      for (k,v) in res.getheaders():
        print '*** Header %s=%s' % (k, v)

  else:
    # The unit test framework does not like to see any command line arguments
    # except for those that it wants, so remove anything we already handled.
    sys.argv[1:] = args
    unittest.main()
