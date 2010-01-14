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
    this.note = note;
  }

  public String getNote() {
    return note;
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
