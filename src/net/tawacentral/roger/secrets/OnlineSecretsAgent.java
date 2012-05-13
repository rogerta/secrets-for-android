package net.tawacentral.roger.secrets;

/**
 * Represents an Online Backup or Sync Agent application.
 * 
 * @author Ryan Dearing
 * @author Chris Wood
 */
public class OnlineSecretsAgent {
	private String displayName;
	private String classId;
	private int agentType;
	private Secret configSecret;
	private boolean lost; // true = not recontacted following resume

	/* package-wide constants */
	static final int TYPE_BACKUP = 0;
	static final int TYPE_SYNC = 1;
	
	static final String SD_CARD_CLASSID = "secrets-sd-card";

	/**
	 * Constructor
	 * @param displayName
	 * @param classId
	 */
	public OnlineSecretsAgent(String displayName, String classId) {
		if (displayName == null || classId == null || displayName.equals("") || classId.equals("")) {
			throw new IllegalArgumentException("displayName and classId are required!");
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
	 * Get agent type
	 * @return the agent type
	 */
	public int getAgentType() {
		return agentType;
	}

	/**
	 * Set agent type
	 * @param agentType the agentType to set
	 */
	public void setAgentType(int agentType) {
		this.agentType = agentType;
	}

	/**
	 * Test for BACKUP/RESTORE type agent
	 * @return true if type is BACKUP/RESTORE, false otherwise
	 */
	public boolean isBackup() {
		return agentType == TYPE_BACKUP;
	}

	/**
	 * Test for SYNC type agent
	 * @return true if type is SYNC, false otherwise
	 */
	public boolean isSync() {
		return agentType == TYPE_SYNC;
	}

	/**
	 * Get the configured secret
	 * @return the configSecret or null if none
	 */
	public Secret getConfigSecret() {
		return configSecret;
	}

	/**
	 * Set the secret used to configure this OSA
	 * @param configSecret the configSecret to set
	 */
	public void setConfigSecret(Secret configSecret) {
		this.configSecret = configSecret;
	}

	/**
	 * Test the lost indicator
	 * @return lost
	 */
	public boolean isLost() {
		return lost;
	}

	/**
	 * Set the lost indicator
	 * @param lost the lost to set
	 */
	public void setLost(boolean lost) {
		this.lost = lost;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;

		OnlineSecretsAgent that = (OnlineSecretsAgent) o;
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
