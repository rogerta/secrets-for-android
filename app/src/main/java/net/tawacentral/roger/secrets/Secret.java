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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

/**
 * Represents one secret.  The assumption is that each secret is describable,
 * uses a combination of username/password, and may require a separate email
 * address for management purposes.  Finally, an arbitrary note can be attached.
 *
 * @author rogerta
 */

@SuppressWarnings("javadoc")
public class Secret implements Comparable<Secret>, Serializable {
  private static final long serialVersionUID = -116450416616138469L;
  private static final int THRESHOLD_MS = 60 * 1000;
  private static final int MAX_LOG_SIZE = 100;

  // Tag for logging purposes
  public static final String LOG_TAG = "Secret";

  // Secret field names
  private static final String SECRET_DESCRIPTION = "description";
  private static final String SECRET_USERNAME = "username";
  private static final String SECRET_PASSWORD = "password";
  private static final String SECRET_EMAIL = "email";
  private static final String SECRET_NOTE = "note";
  private static final String SECRET_ACCESS_LOG = "log";
  private static final String SECRET_TIMESTAMP = "timestamp";
  private static final String SECRET_DELETED = "deleted";

  // Secret fields
  private String description;
  private String username;
  private String password;
  private String email;
  private String note;
  private ArrayList<LogEntry> access_log;

  /* soft deletion indicator */
  private boolean deleted;

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
    public static final int SYNCED = 5;
    public static final int DELETED = 6;

    // Log field names
    private static final String LOG_TYPE = "type";
    private static final String LOG_TIME = "time";

    private int type_;
    private long time_;

    /** Used internally to create the CREATED log entry. */
    LogEntry() {
      type_ = CREATED;
      time_ = System.currentTimeMillis();
    }

    /**
     * Creates a new log entry object of the given type for the given time.
     *
     * @param type One of the supported types.
     * @param time Time of the entry, in milliseconds.
     */
    public LogEntry(int type, long time) {
      this.type_ = type;
      this.time_ = time;
    }

    /**
     * Returns the type of this log entry.
     *
     * @return One of the supported types.
     */
    public int getType() {
      return type_;
    }

    /** Returns the time stamp associated with this log entry. */
    public long getTime() {
      return time_;
    }

    private JSONObject toJSON() throws JSONException {
      JSONObject jsonValues = new JSONObject();
      jsonValues.put(LOG_TYPE, getType());
      jsonValues.put(LOG_TIME, getTime());
      return jsonValues;
    }

    /**
     * Generate LogEntry from json object
     * @param jsonValues
     * @return LogEntry
     * @throws JSONException
     */
    public static LogEntry fromJSON(JSONObject jsonValues)
            throws JSONException {
      return new LogEntry(jsonValues.getInt(LOG_TYPE),
                           jsonValues.getLong(LOG_TIME));
    }
  }

  /**
   * Creates a new secret where all fields are empty.  The access log contains
   * only one CREATED entry, with the current time.
   */
  public Secret() {
    access_log = new ArrayList<LogEntry>();
    access_log.add(new LogEntry());
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
   * CHANGED entry if requested.
   *
   * @param password The new password
   * @param createDefaultLogEntry If true, create a CHANGED entry, otherwise
   *                              do nothing
   */
  public void setPassword(String password, boolean createDefaultLogEntry) {
    if (createDefaultLogEntry) {
      createLogEntry(LogEntry.CHANGED);
    }

    this.password = password;
  }

  /**
   * Create a new log entry for the specified type, under the following
   * conditions.
   * If the specified type is:
   *   VIEWED -  if the previous entry is recent, do nothing
   *   CHANGED - if the previous entry is VIEWED and is recent, remove it
   *             before creating the new entry
   *   EXPORTED - always create a new entry
   *   SYNCED (input) - always create a new entry
   *   DELETED  - always create a new entry
   *
   * Any other type is ignored.
   *
   * The first two conditons are to prevent too many log entries.
   *
   * @param type VIEWED, CHANGED, EXPORTED, SYNCED
   */
  private void createLogEntry(int type) {
    if (!(type == LogEntry.VIEWED ||
        type == LogEntry.CHANGED ||
        type == LogEntry.EXPORTED ||
        type == LogEntry.SYNCED ||
        type == LogEntry.DELETED)) {
      return;
    }

    long now = System.currentTimeMillis();
    if (type == LogEntry.VIEWED || type == LogEntry.CHANGED) {
      LogEntry lastEntry = access_log.get(0);
      if (now - lastEntry.getTime() < THRESHOLD_MS) {
        if (type == LogEntry.VIEWED) return;
        if (lastEntry.getType() == LogEntry.VIEWED) {
          access_log.remove(0);
        }
      }
    }

    access_log.add(0, new LogEntry(type, now));
    pruneAccessLog();
  }

  /**
   * Gets the password for the secret, updating the access log with a
   * VIEWED (if the most recent access is not too recent) or EXPORTED entry.
   * @param forExport if true create EXPORTED log entry, else VIEWED
   * @return password
   */
  public String getPassword(boolean forExport) {
     createLogEntry(forExport ? LogEntry.EXPORTED : LogEntry.VIEWED);

    return password;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public String getEmail() {
    return email;
  }

  public void setNote(String note) {
    this.note = note;
  }

  public String getNote() {
    return note;
  }

	/**
   * @return the deleted
   */
  public boolean isDeleted() {
    return deleted;
  }

  /**
   * Set the secret as deleted
   */
  public void setDeleted() {
    deleted = true;
    createLogEntry(LogEntry.DELETED);
  }

  /**
	 * Update this secret from another
	 * @param from source secret
	 * @param reason Log entry value
	 */
   public void update(Secret from, int reason) {
	  if (!(reason == LogEntry.CHANGED || reason == LogEntry.SYNCED || equals(from)))
	    return;

		setPassword(from.password, false);
		username = from.getUsername();
		email = from.getEmail();
		note = from.getNote();
		createLogEntry(reason);
	}

  /**
   * Convert secret to a JSON OBJECT
   * @return JSON representation of a secret
   * @throws JSONException
   */
  public JSONObject toJSON() throws JSONException {
    JSONObject jsonSecret = new JSONObject();
    jsonSecret.put(SECRET_DESCRIPTION, description);
    jsonSecret.put(SECRET_USERNAME, username);
    jsonSecret.put(SECRET_PASSWORD, password);
    jsonSecret.put(SECRET_EMAIL, email);
    jsonSecret.put(SECRET_NOTE, note);
    jsonSecret.put(SECRET_TIMESTAMP, getLastChangedTime());
    jsonSecret.put(SECRET_DELETED, deleted);

    JSONArray jsonLog = new JSONArray();
    for (LogEntry logEntry : access_log) {
      jsonLog.put(logEntry.toJSON());
    }
    jsonSecret.put(SECRET_ACCESS_LOG, jsonLog);

    return jsonSecret;
  }

  /**
   * Convert JSON object to a Secret
   * @param jsonSecret JSON object
   * @return instance of a Secret
   * @throws JSONException
   */
  public static Secret fromJSON(JSONObject jsonSecret) throws JSONException {
    Secret secret = new Secret();
    secret.description = jsonSecret.getString(SECRET_DESCRIPTION);
    secret.username = jsonSecret.getString(SECRET_USERNAME);
    secret.password = jsonSecret.getString(SECRET_PASSWORD);
    secret.email = jsonSecret.getString(SECRET_EMAIL);
    secret.note = jsonSecret.getString(SECRET_NOTE);

    if (jsonSecret.has(SECRET_DELETED))
      secret.deleted = jsonSecret.getBoolean(SECRET_DELETED);

    if (jsonSecret.has(SECRET_ACCESS_LOG)) {
      JSONArray jsonLog = jsonSecret.getJSONArray(SECRET_ACCESS_LOG);
      ArrayList<LogEntry> log = new ArrayList<LogEntry>(jsonLog.length());
      for (int i = 0; i < jsonLog.length(); i++) {
        log.add(LogEntry.fromJSON((JSONObject)jsonLog.get(i)));
      }
      secret.access_log = log;
      if (!(log.size() > 0)) {
         Log.w(LOG_TAG, "Empty access log for secret '" + secret.description
                     + "'");
      }
    }
    
    // at this point we still may have no log, or an empty log.
    // we must have a log with at least a CREATED entry
    if (secret.access_log == null) {
      secret.access_log = new ArrayList<LogEntry>();
    }
    if (secret.access_log.size() == 0) {
      secret.access_log.add(0, new LogEntry());
    }

    return secret;
  }

  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("d=").append(description);
    sb.append(",u=").append(username);
    sb.append(",p=").append(password);
    sb.append(",e=").append(email);
    return sb.toString();
  }

  /* (non-Javadoc)
   * @see java.lang.Object#equals(java.lang.Object)
   */
  @Override
  public boolean equals(Object o) {
    if (o instanceof Secret)
      return ((Secret)o).description.equalsIgnoreCase(description);
    return false;
  }

  /* (non-Javadoc)
   * @see java.lang.Comparable#compareTo(java.lang.Object)
   */
  @Override
  public int compareTo(Secret anotherSecret) {
    return description.compareToIgnoreCase(anotherSecret.description);
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
 
  /**
   * Get last changed time
   * @return long time
   */
  public long getLastChangedTime() {
    for (int i = 0; i < access_log.size(); i++) {
      LogEntry entry = access_log.get(i);
      if (entry.getType() == LogEntry.CHANGED ||
          entry.getType() == LogEntry.SYNCED ||
          entry.getType() == LogEntry.CREATED ||
          entry.getType() == LogEntry.DELETED) {
        return entry.getTime();
      }
    }
    return 0;
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
    // Need to be careful about an entry that is modified often, in which case
    // a naive implementation of the above could end up never storing any and
    // VIEWED entries.

    while(access_log.size() > MAX_LOG_SIZE) {
      // The "created" entry is always the last one in the list, and there
      // is only ever one.  So try to delete the second last item.
      int index = access_log.size() - 2;
      access_log.remove(index);
    }
  }
}
