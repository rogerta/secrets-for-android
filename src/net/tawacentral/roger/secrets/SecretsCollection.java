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
 * Class that represents a secrets collection. Introduced to hold meta-info for
 * the collection e.g. sync timestamp.
 * 
 * @author Chris Wood
 */
@SuppressWarnings("serial")
public class SecretsCollection extends ArrayList<Secret> {
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets.SecretsCollection";

  private static final String SECRETS_ID = "secrets";
  private static final String SYNCDATE_ID = "syncdate";

  private long lastSyncTimestamp;

  /**
   * Default constructor
   */
  public SecretsCollection() {
    super();
  }

  /**
   * Construct from another collection
   * 
   * @param collection
   */
  public SecretsCollection(Collection<? extends Secret> collection) {
    super(collection);
  }

  /**
   * @return the lastSyncTimestamp
   */
  public long getLastSyncTimestamp() {
    return lastSyncTimestamp;
  }

  /**
   * @param lastSyncTimestamp
   *          the lastSyncTimestamp to set
   */
  public void setLastSyncTimestamp(long lastSyncTimestamp) {
    this.lastSyncTimestamp = lastSyncTimestamp;
  }

  /**
   * Add or update the current secrets from the given collection.
   * 
   * Assumes that the collection sort sequences are the same. 
   * 
   * @param newSecrets
   *          - added or changed secrets
   */
  public void syncSecrets(SecretsCollection newSecrets) {
    for (Secret newSecret : newSecrets) {
      boolean done = false;
      for (int i = 0; i < size(); i++) {
        Secret existingSecret = get(i);
        int compare = newSecret.getDescription().compareTo(
                existingSecret.getDescription());
        /*
         * TODO(ctw) Roger says the following block is unnecessary. I think this
         * needs to be looked at further - it questions the ordering of
         * the collection.
         */
        if (compare < 0) {
          add(i, newSecret);
          done = true;
          break;
        } else if (compare == 0) {
          existingSecret.update(newSecret, LogEntry.SYNCED);
          done = true;
          break;
        }
      }
      if (!done) {
        add(newSecret);
      }
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
    jsonValues.put(SYNCDATE_ID, getLastSyncTimestamp());

    return jsonValues;
  }
//  }

  /**
   * Constructs a secrets collection from the supplied JSON object
   * @param jsonValues
   *          JSON object
   * @return list of secrets
   * @throws JSONException if error with JSON data
   */
  public static SecretsCollection fromJSON(JSONObject jsonValues)
          throws JSONException {
    JSONArray jsonSecrets = jsonValues.getJSONArray(SECRETS_ID);
    SecretsCollection secretList = new SecretsCollection();
    for (int i = 0; i < jsonSecrets.length(); i++) {
      secretList.add(Secret.fromJSON((JSONObject) jsonSecrets.get(i)));
    }
    /* get the last sync date */
    if (jsonValues.has(SYNCDATE_ID)) {
      secretList.setLastSyncTimestamp(jsonValues
              .getLong(SYNCDATE_ID));
    } else {
      Log.w(LOG_TAG, "No sync date in JSON stream - set to default");
      secretList.setLastSyncTimestamp(0);
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
      try {
        if (null != output)
          output.close();
      } catch (IOException ex) {
      }
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
                                                          byte[] secrets)
          throws IOException {
    try {
      byte[] secretStrBytes = cipher.doFinal(secrets);
      JSONObject jsonValues = new JSONObject(
              new String(secretStrBytes, "UTF-8"));
      return fromJSON(jsonValues);
    } catch (Exception e) {
      Log.e(LOG_TAG, "fromEncryptedJSONStream", e);
      throw new IOException("fromEncryptedJSONStream failed: " + e.getMessage());
    }
  }

}
