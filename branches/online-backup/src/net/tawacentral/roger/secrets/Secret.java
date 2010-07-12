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

import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Represents one secret.  The assumption is that each secret is describable,
 * uses a combination of username/password, and may require a separate email
 * address for management purposes.  Finally, an arbitrary note can be attached.
 *
 * @author rogerta
 */
public class Secret implements Serializable {
  private static final long serialVersionUID = -116450416616138469L;
  private static final int TIMEOUT_MS = 60 * 1000;
  private static final int MAX_LOG_SIZE = 100;
  private static final String LOG_TAG = "Secret";

  private String description;
  private String username;
  private String password;
  private String email;
  private String note;
  private ArrayList<LogEntry> access_log;

  /**
   * An immutable class that represents one entry in the access log.  Each
   * time the password is viewed or modified, the access log is updated with
   * a new log entry.
   *
   * Log entries are pruned so that they don't grow indefinitely.  The
   * maximum size of an access log is given by the MAX_LOG_SIZE constant.
   * The CREATED log entry is never pruned though.
   *
   * @author rogerta
   */
  public static final class LogEntry implements Serializable {
    private static final long serialVersionUID = -9024951856209882415L;

    // Log entry types.
    public static final int CREATED = 1;
    public static final int VIEWED = 2;
    public static final int CHANGED = 3;
    public static final int EXPORTED = 4;

    private int type_;
    private long time_;

    /** Used internally to create the CREATED long entry. */
    LogEntry() {
      type_ = CREATED;
      time_ = System.currentTimeMillis();
    }

    /**
     * Creates a new log entry object of the given type for the given time.
     *
     * @param type One of CREATED, VIEWED, or CHANGED.
     * @param time Time of the entry, in milliseconds.
     */
    public LogEntry(int type, long time) {
      this.type_ = type;
      this.time_ = time;
    }

    /**
     * Returns the type of this log entry.
     *
     * @return One of CREATED, VIEWED, or CHANGED.
     */
    public int getType() {
      return type_;
    }

    /** Returns the time stamp associated with this log entry. */
    public long getTime() {
      return time_;
    }
  }

  /**
   * Creates a new secret where all fields are empty.  The access log contains
   * only on CREATED entry with the current time.
   */
  public Secret() {
    access_log = new ArrayList<LogEntry>();
    access_log.add(new LogEntry());
    note = new JSONObject().toString();
  }

  /**
   * This method exists only to recover from a corrupted save file.  As each
   * secret is successfully read, it is added to a global array.  If the save
   * file cannot read the entire array because the end is corrupted, the global
   * array will contain those that were read successfully.
   * 
   *  Its the responsibility of the load code to clear the global array before
   *  and after reading from the file.  This code assumes that only one thread
   *  tries to load Secrets from an input stream at a time.
   * 
   * @param stream
   * @throws IOException
   * @throws ClassNotFoundException
   */
  private void readObject(ObjectInputStream stream)
      throws IOException, ClassNotFoundException {
    stream.defaultReadObject();
  }
  
  public void setDescription(String description) {
    this.description = description;
  }
  public String getDescription() {
    return description;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getUsername() {
    return username;
  }

  /**
   * Sets the password for the secret, updating the access log with a
   * CHANGED entry.
   *
   * <p>If the most recent entry is a VIEWED, and that view was pretty recent,
   * then replace the VIEWED with a CHANGED.  This is to handle the case
   * when the user edits a secret, we don't want to accumulate too many
   * log entries.
   */
  public void setPassword(String password) {
    long now = System.currentTimeMillis();
    LogEntry entry = access_log.get(0);
    if (now - entry.getTime() < TIMEOUT_MS) {
      if (entry.getType() == LogEntry.VIEWED) {
        access_log.remove(0);
        access_log.add(0, new LogEntry(LogEntry.CHANGED, now));
      }
    } else {
      access_log.add(0, new LogEntry(LogEntry.CHANGED, entry.getTime()));
      pruneAccessLog();
    }

    this.password = password;
  }

  /**
   * Gets the password for the secret, updating the access log with a
   * VIEWED (if the most recent access is not too recent) or EXPORTED entry.
   */
  public String getPassword(boolean forExport) {
    // Don't add an entry to the access log if the last entry is within
    // 60 seconds of now.
    long now = System.currentTimeMillis();
    LogEntry entry = access_log.get(0);
    if (forExport || (now - entry.getTime() > TIMEOUT_MS)) {
      int type = forExport ? LogEntry.EXPORTED : LogEntry.VIEWED;
      access_log.add(0, new LogEntry(type, now));
      pruneAccessLog();
    }

    return password;
  }

  /**
   * Prune the size of the access log to the maximum size by getting rid of
   * the oldest entries.  The "created" log entry is never pruned away.
   */
  private void pruneAccessLog() {
    // TODO(rogerta): may want to give lower priority to VIEWED entries.  Could
    // maybe implement this by doing a first pass that removes VIEWED entries
    // first to see if we can reach the limit.  If not, then do a paas to delete
    // either VIEWED or CHANGED entries.
    //
    // Need to be careful about an etry that is modified often, in which case
    // a naive implementation of the above could end up never storing any and
    // VIEWED entries.

    while(access_log.size() > MAX_LOG_SIZE) {
      // The "created" entry is always the first one in the list, and there
      // is only ever one.  So try to delete the second last item.
      int index = access_log.size() - 2;
      access_log.remove(index);
    }
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getEmail() {
    return email;
  }

  public void setNote(String note) {
    try {
      if(this.note == null || this.note.equals(""))
        this.note = new JSONObject().put("note", note).toString();
      else
        this.note = new JSONObject(this.note).put("note", note).toString();
    } catch (JSONException e) {
      Log.e(LOG_TAG,"JSON error", e);
    }
  }

  public String getNote() {
    String note = null;
    try {
      note = (String)new JSONObject(this.note).get("note");
    } catch (JSONException e) {
      Log.e(LOG_TAG,"JSON error",e);
    }
    return note;
  }

  public String getNoteJSON() {
    return note;
  }

  public void addOBA(OnlineBackupApplication oba) {
    try {
      JSONObject jsonOBA = new JSONObject();
      jsonOBA.put("classid", oba.getClassId());
      this.note = new JSONObject(this.note).accumulate("oba", jsonOBA).toString();
    } catch (JSONException e) {
      Log.e(LOG_TAG, "JSON error", e);
      //TODO (rdearing) if we get here, it probably makes sense to just clear the oba field and start fresh
    }
  }

  /**
   * Helper method that will always return an array of OBAs even if this secret only has 1
   * @return JSONArray of OBAs assigned to this secret
   */
  private JSONArray getOBAArray() {
    JSONArray curObas = new JSONArray();
    try {
      JSONObject noteObject = new JSONObject(this.note);
      // test to see if the oba is an array (there is more than 1 oba)
      curObas = noteObject.optJSONArray("oba");
      // if it was not an array, then there is either 0 or 1 obas
      if(curObas == null) {
        JSONObject oba = noteObject.optJSONObject("oba");
        curObas = new JSONArray();
        // if oba is null we do not have an oba for this secret
        if(oba != null)
          curObas.put(oba);
      }
    } catch (JSONException e) {
      Log.e(LOG_TAG, "JSON error", e);
    }
    return curObas;
  }

  /**
   * Removes the OBA from this secret
   * @param app
   */
  public void removeOBA(OnlineBackupApplication app) {
    try {
      JSONObject noteObject = new JSONObject(this.note);
      // the removal happens by building a new JSONArray with the app missing
      JSONArray newObas = new JSONArray();
      JSONArray curObas = getOBAArray();
      for(int i = 0; i < curObas.length(); i++) {
        JSONObject jsonOBA = curObas.getJSONObject(i);
        // this will check if the classid matches, or if the oba JSON oba
        // has no classid (which should not happen, but this provides a cleanup
        // mechanism
        String classid = jsonOBA.optString("classid", "");
        if(!classid.equals(app.getClassId()) && !classid.equals("")) {
          newObas.put(jsonOBA);
        }
      }
      this.note = new JSONObject(this.note).put("oba", newObas).toString();
    } catch (JSONException e) {
      Log.e(LOG_TAG, "JSON error", e);
      //TODO (rdearing) if we get here, it probably makes sense to just clear the oba field and start fresh
    }
  }

  /**
   * Returns a Set of OBAs that this secret is used for
   * @return Set of OBA classids
   */
  public Set<String> getOBAs() {
    // avoid NPE
    Set<String> obaSet = Collections.emptySet();
    try {
      JSONArray curObas = getOBAArray();
      obaSet = new HashSet<String>(curObas.length());
      for(int i = 0; i < curObas.length(); i++) {
        JSONObject jsonOBA = curObas.getJSONObject(i);
        obaSet.add(jsonOBA.optString("classid", ""));
      }
    } catch (JSONException e) {
      Log.e(LOG_TAG, "JSON error", e);
    }
    return obaSet;
  }

  public String toJSONString() throws JSONException {
    JSONObject jsonSecret = new JSONObject();
    jsonSecret.put("username", username);
    jsonSecret.put("password", password);
    jsonSecret.put("email", email);
    jsonSecret.put("note", new JSONObject(this.note));
    jsonSecret.put("description", description);
    Log.d(LOG_TAG, "JSON: " + jsonSecret.toString());
    return jsonSecret.toString();
  }

  public static Secret fromJSONString(String json) throws JSONException {
    Secret secret = new Secret();
    JSONObject jsonSecret = new JSONObject(json);
    secret.username = jsonSecret.getString("username");
    secret.password = jsonSecret.getString("password");
    secret.email = jsonSecret.getString("email");
    secret.description = jsonSecret.getString("description");
    secret.note = jsonSecret.getJSONObject("note").toString();
    return secret;
  }

  /**
   * Get an unmodifiable list of access logs, in reverse chronological order,
   * for this secret.
   */
  public List<LogEntry> getAccessLog() {
    return Collections.unmodifiableList(access_log);
  }

  /**
   * A helper function to return the most recent access log entry of this
   * secret.
   */
  public LogEntry getMostRecentAccess() {
    return access_log.get(0);
  }
}
