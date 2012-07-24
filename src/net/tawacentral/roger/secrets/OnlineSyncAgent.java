package net.tawacentral.roger.secrets;

/**
 * Represents an Online Sync Agent application.
 * 
 * @author Chris Wood
 */
public class OnlineSyncAgent {
	private String displayName;
	private String classId;
	private Secret configSecret;
	
  /*
   * "available" indicates if the agent is available. Agents are required to
   * respond to a roll call broadcast every time the app is resumed. Previously
   * known agents that do not respond are marked as not available but remain
   * configured.
   */
  private boolean available = true;

	/**
	 * Constructor
	 * @param displayName
	 * @param classId
	 */
  public OnlineSyncAgent(String displayName, String classId) {
    if (displayName == null || classId == null || displayName.equals("")
            || classId.equals("")) {
      throw new IllegalArgumentException(
              "displayName and classId are required!");
    }
    this.displayName = displayName;
    this.classId = classId;
  }

	/**
	 * Get displayName
	 * @return the displayName
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Get classId
	 * @return the classId
	 */
	public String getClassId() {
		return classId;
	}

	/**
	 * Get the secret used to configure this agent
	 * @return the configSecret or null if none
	 */
	public Secret getConfigSecret() {
		return configSecret;
	}

	/**
	 * Set the secret used to configure this agent
	 * @param configSecret the configSecret to set
	 */
	public void setConfigSecret(Secret configSecret) {
		this.configSecret = configSecret;
	}

	/**
	 * Test the available indicator
	 * @return available
	 */
	public boolean isAvailable() {
		return available;
	}

	/**
	 * Set the available indicator
	 * @param available the indicator to set
	 */
	public void setAvailable(boolean available) {
		this.available = available;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		OnlineSyncAgent that = (OnlineSyncAgent) o;
		if (!classId.equals(that.classId))
			return false;
		return displayName.equals(that.displayName);
	}

	@Override
	public int hashCode() {
		int result = displayName.hashCode();
		result = 31 * result + classId.hashCode();
		return result;
	}

	@Override
	public String toString() {
		return displayName;
	}
}
