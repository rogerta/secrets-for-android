package net.tawacentral.roger.secrets;

import java.security.SecureRandom;

import net.tawacentral.roger.secrets.OnlineAgentManager.SecretsReceivedListener;


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
	 * The response key is an randomly generated string that is provided to the
	 * OSA as part of the sync request and must be returned in the response in
	 * order for it to be accepted. The key is changed when the response is
	 * received to ensure that any subsequent or unsolicited responses are
	 * rejected.
	 */
	private static final int RESPONSEKEY_LENGTH = 8;
	private String responseKey;

  /*
   * available indicates if the agent is available. Agents are required to
   * respond to a roll call broadcast every time the app is resumed. Previously
   * known agents that do not respond are marked as not available but remain
   * configured.
   */
  private boolean available = true;
  
  private SecretsReceivedListener listener;

  /**
	 * Constructor
	 * @param displayName
	 * @param classId
	 */
  public OnlineSyncAgent(String displayName, String classId) {
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

  /**
   * Get the response key
   * @return the responseKey
   */
  public String getResponseKey() {
    return responseKey;
  }

  /**
   * Generate a new response key
   */
  public void generateResponseKey() {
    SecureRandom random = new SecureRandom();
    byte[] keyBytes = new byte[RESPONSEKEY_LENGTH];
    random.nextBytes(keyBytes);
    this.responseKey = new String(keyBytes);
  }

  /**
   * Get the secrets received listener
   * @return the listener
   */
  public SecretsReceivedListener getListener() {
    return listener;
  }

  /**
   * Set the secrets received listener
   * @param listener the listener to set
   */
  public void setSecretsReceivedlistener(SecretsReceivedListener listener) {
    this.listener = listener;
  }

	@Override
	public String toString() {
		return displayName;
	}
}
