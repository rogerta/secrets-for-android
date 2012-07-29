package net.tawacentral.roger.secrets;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

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
  public static final String LOG_TAG = "Secrets.FileUtils";
  
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
   * Replace contents with those from the supplied collection
   * 
   * @param collection
   */
  public void replaceContents(Collection<? extends Secret> collection) {
    clear();
    addAll(collection);
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
         * TODO Roger says the following block is unnecessary. I think this
         * needs to be looked at further - this logic questions the ordering of
         * the collection, and whether this should be a merge operation.
         */
        if (compare < 0) {
          add(i, newSecret);
          done = true;
          break;
        } else if (compare == 0) {
          existingSecret.update(newSecret);
          existingSecret.createLogEntry(LogEntry.SYNCED);
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
   * Returns an encrypted json stream representing the user's secrets.
   * 
   * @param cipher
   *          The encryption cipher to use with the file.
   * @param secrets
   *          The list of secrets.
   * @return byte array of secrets
   */
  public static byte[] putSecretsToEncryptedJSONStream(Cipher cipher,
                                                   SecretsCollection secrets) {
    /**TODO This should not be a static method */
    Log.d(FileUtils.LOG_TAG, "FileUtils.putSecretsToEncryptedJSONStream");

    if (null == cipher)
      return null;

    CipherOutputStream output = null;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    JSONObject jsonValues = new JSONObject();
    boolean success = false;
    try {
      output = new CipherOutputStream(baos, cipher);
      JSONArray jsonSecrets = new JSONArray();
      for (Secret secret : secrets) {
        jsonSecrets.put(secret.toJSONString());
      }
      jsonValues.put("secrets", jsonSecrets);
      jsonValues.put("syncdate", secrets.getLastSyncTimestamp());
//      if (secrets instanceof SecretsCollection) {
//        jsonValues.put("syncdate",
//                ((SecretsCollection) secrets).getLastSyncTimestamp());
//      } else {
//        Log.w(FileUtils.LOG_TAG,
//                "No sync date in stored secrets - not SecretsCollection");
//      }
      output.write(jsonValues.toString().getBytes("UTF-8"));
      success = true;
    } catch (Exception ex) {
      Log.d(LOG_TAG, "SecretsCollection.putSecretsToEncryptedJSONStream "
              + ex);
    } finally {
      try {
        if (null != output)
          output.close();
      } catch (IOException ex) {
      }
    }

    if (!success) {
      throw new RuntimeException("Failed creating stream");
    }
    return baos.toByteArray();
  }

  /**
   * Returns an json stream representing the contained secrets.
   * 
   * @param secrets The list of secrets.
   * @return String of secrets
   */
  public String putSecretsToJSONString() {
    JSONObject jsonValues = new JSONObject();
    JSONArray jsonSecrets = new JSONArray();
    try {
      for (Iterator<Secret> iterator = iterator(); iterator.hasNext();) {
        Secret secret = (Secret) iterator.next();
        jsonSecrets.put(secret.toJSONString());
      }
      jsonValues.put("secrets", jsonSecrets);
      jsonValues.put("syncdate", getLastSyncTimestamp());
    } catch (JSONException e) {
      Log.d(LOG_TAG, "SecretsCollection.putSecretsToJSONString " + e);
    }
    return jsonValues.toString();
  }

  /**
   * Constructs secrets from the supplied JSON String
   * 
   * @param secrets JSON String
   * @return list of secrets or null if error
   */
  public static SecretsCollection getSecretsFromJSONString(String secrets) {
    if (secrets == null || secrets.length() == 0)
      return null;

    try {
      JSONObject jsonValues = new JSONObject(secrets);
      /* get the secrets */
      JSONArray jsonSecrets = jsonValues.getJSONArray("secrets");
      SecretsCollection secretList = new SecretsCollection();
      for (int i = 0; i < jsonSecrets.length(); i++) {
        secretList.add(Secret.fromJSONString(jsonSecrets.getString(i)));
      }
      /* get the last sync date */
      if (jsonValues.has("syncdate")) {
        secretList.setLastSyncTimestamp(jsonValues.getLong("syncdate"));
      } else {
        Log.w(FileUtils.LOG_TAG, "No sync date in JSON stream - set to default");
        secretList.setLastSyncTimestamp(0);
      }
      return secretList;
    } catch (Exception e) {
      Log.e(FileUtils.LOG_TAG, "Error restoring secret stream", e);
    }
    return null;
  }

  /**
   * Constructs secrets from the supplied encrypted byte stream
   * 
   * @param cipher
   *          cipher to use
   * @param secrets
   *          encrypted byte array
   * @return list of secrets
   */
  public static SecretsCollection getSecretsFromEncryptedJSONStream(Cipher cipher,
                                                                    byte[] secrets) {
    if (cipher == null || secrets == null || secrets.length == 0)
      return null;

    try {
      byte[] secretStrBytes = cipher.doFinal(secrets);
      JSONObject jsonValues = new JSONObject(
              new String(secretStrBytes, "UTF-8"));
      /* get the secrets */
      JSONArray jsonSecrets = jsonValues.getJSONArray("secrets");
      SecretsCollection secretList = new SecretsCollection();
      for (int i = 0; i < jsonSecrets.length(); i++) {
        secretList.add(Secret.fromJSONString(jsonSecrets.getString(i)));
      }
      /* get the last sync date */
      if (jsonValues.has("syncdate")) {
        secretList.setLastSyncTimestamp(jsonValues.getLong("syncdate"));
      } else {
        Log.w(FileUtils.LOG_TAG, "No sync date in JSON stream - set to default");
        secretList.setLastSyncTimestamp(0);
      }
      return secretList;
    } catch (Exception e) {
      Log.e(FileUtils.LOG_TAG, "Error restoring secret stream", e);
    }
    return null;
  }

}
