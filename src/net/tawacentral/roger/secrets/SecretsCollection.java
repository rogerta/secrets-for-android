// Copyright (c) 2009, Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.tawacentral.roger.secrets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

import javax.crypto.Cipher;
import javax.crypto.CipherOutputStream;

import net.tawacentral.roger.secrets.Secret.LogEntry;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * Class that represents a secrets collection. Originally introduced to hold
 * meta-info for the collection.
 *
 * @author Chris Wood
 */
@SuppressWarnings("serial")
public class SecretsCollection extends ArrayList<Secret> {
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "SecretsCollection";

  private static final String SECRETS_ID = "secrets";

  /**
   * Default constructor
   */
  public SecretsCollection() {
    super();
  }

  /**
   * Construct from another SecretsCollection
   *
   * @param collection
   */
  public SecretsCollection(SecretsCollection collection) {
    super(collection);
  }

  /**
   * Construct from another collection
   *
   * @param collection
   */
  public SecretsCollection(Collection<Secret> collection) {
    super(collection);
  }

  /**
   * Add, update or delete the current secrets in the given collection.
   *
   * Assumes that the collection sort sequences are the same.
   *
   * @param changedSecrets
   *          - added, changed or deleted secrets
   */
  public void syncSecrets(SecretsCollection changedSecrets) {
    for (Secret changedSecret : changedSecrets) {
      boolean done = false;

      for (int i = 0; i < size(); i++) {
        Secret existingSecret = get(i);
        int compare = changedSecret.compareTo(existingSecret);
        if (compare < 0 && !changedSecret.isDeleted()) {
          add(i, changedSecret);
          done = true;
          Log.d(LOG_TAG, "syncSecrets: added '" +
              changedSecret.getDescription() + "'");
          break;
        } else if (compare == 0) {
          if (changedSecret.isDeleted()) {
            remove(existingSecret);
            Log.d(LOG_TAG, "syncSecrets: removed '" +
                changedSecret.getDescription() + "'");
          } else {
            existingSecret.update(changedSecret, LogEntry.SYNCED);
            Log.d(LOG_TAG, "syncSecrets: updated '" +
                changedSecret.getDescription() + "'");
          }

          done = true;
          break;
        }
      }

      if (!done && !changedSecret.isDeleted())
        add(changedSecret);
    }
  }

  /**
   * Returns an json object representing the contained secrets.
   *
   * @param secrets
   *          The list of secrets.
   * @return String of secrets
   * @throws JSONException
   */
  public JSONObject toJSON() throws JSONException {
    JSONObject jsonValues = new JSONObject();
    JSONArray jsonSecrets = new JSONArray();
    for (Secret secret : this) {
      jsonSecrets.put(secret.toJSON());
    }
    jsonValues.put(SECRETS_ID, jsonSecrets);

    return jsonValues;
  }

  // }

  /**
   * Constructs a secrets collection from the supplied JSON object
   *
   * @param jsonValues
   *          JSON object
   * @return list of secrets
   * @throws JSONException
   *           if error with JSON data
   */
  public static SecretsCollection fromJSON(JSONObject jsonValues)
      throws JSONException {
    JSONArray jsonSecrets = jsonValues.getJSONArray(SECRETS_ID);
    SecretsCollection secretList = new SecretsCollection();
    for (int i = 0; i < jsonSecrets.length(); i++) {
      secretList.add(Secret.fromJSON((JSONObject) jsonSecrets.get(i)));
    }

    return secretList;
  }

  /**
   * Returns an encrypted json stream representing the user's secrets.
   *
   * @param cipher
   *          The encryption cipher to use with the file.
   * @param secrets
   *          The list of secrets.
   * @return byte array of secrets
   * @throws IOException
   *           if any error occurs
   */
  public byte[] toEncryptedJSONStream(Cipher cipher) throws IOException {
    CipherOutputStream output = null;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try {
      output = new CipherOutputStream(baos, cipher);
      output.write(toJSON().toString().getBytes("UTF-8"));
    } catch (Exception e) {
      Log.e(LOG_TAG, "toEncryptedJSONStream", e);
      throw new IOException("toEncryptedJSONStream failed: " + e.getMessage());
    } finally {
      try { if (null != output) output.close(); } catch (IOException ex) {}
    }

    return baos.toByteArray();
  }

  /**
   * Constructs secrets from the supplied encrypted byte stream
   *
   * @param cipher
   *          cipher to use
   * @param secrets
   *          encrypted byte array
   * @return list of secrets
   * @throws IOException
   *           if any error occurs
   */
  public static SecretsCollection fromEncryptedJSONStream(Cipher cipher,
      byte[] secrets) throws IOException {
    try {
      byte[] secretStrBytes = cipher.doFinal(secrets);
      JSONObject jsonValues =
          new JSONObject(new String(secretStrBytes, "UTF-8"));
      return fromJSON(jsonValues);
    } catch (Exception e) {
      Log.e(LOG_TAG, "fromEncryptedJSONStream", e);
      throw
          new IOException("fromEncryptedJSONStream failed: " + e.getMessage());
    }
  }
}
