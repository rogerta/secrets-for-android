package net.tawacentral.roger.secrets;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Class that represents a secrets collection. Introduced to hold meta-info
 * for the collection e.g. sync timestamp.
 * 
 * @author Chris Wood
 */
@SuppressWarnings("serial")
public class SecretsCollection extends ArrayList<Secret> {
	private long lastSyncTimestamp;

	/**
	 * Default constructor
	 */
	public SecretsCollection() {
		super();
	}

	/**
	 * Construct from another collection
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
	 * @param lastSyncTimestamp the lastSyncTimestamp to set
	 */
	public void setLastSyncTimestamp(long lastSyncTimestamp) {
		this.lastSyncTimestamp = lastSyncTimestamp;
	}
  
  /** Add or update the current secrets from the given collection. 
   * @param newSecrets - added or changed secrets
   */
  public void addOrUpdateSecrets(SecretsCollection newSecrets) {
    for (Secret newSecret : newSecrets) {
      boolean done = false;
      for (int i = 0; i < size(); i++) {
        Secret existingSecret = get(i);
        int compare = newSecret.getDescription().compareTo(
                existingSecret.getDescription());
        if (compare < 0) {
          add(i, newSecret);
          done = true;
          break;
        } else if (compare == 0) {
          existingSecret.update(newSecret);
          done = true;
          break;
        }
      }
      if (!done) {
        add(newSecret);
      }
    }
  }
	
}
