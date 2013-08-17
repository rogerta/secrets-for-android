package net.tawacentral.roger.secrets;

import net.tawacentral.roger.secrets.OnlineAgentManager.SecretsReceivedListener;


/**
 * Represents an Online Sync Agent application.
 *
 * @author Chris Wood
 */
public class OnlineSyncAgent {
	private String displayName;
	private String classId;
	
	/*
	 * The response key is a randomly generated string that is provided to the
	 * OSA as part of the sync request and must be returned in the response in
	 * order for it to be accepted. The key is changed when the response is
	 * received to ensure that any subsequent or unsolicited responses are
	 * rejected.
	 */
	private String responseKey;

  private SecretsReceivedListener listener;

  /**
   * Constructor
   *
   * @param displayName
   * @param classId
   */
  public OnlineSyncAgent(String displayName, String classId) {
    this.displayName = displayName;
    this.classId = classId;
  }

  /**
   * Get displayName
   *
   * @return the displayName
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get classId
   *
   * @return the classId
   */
  public String getClassId() {
    return classId;
  }

  /**
   * Get the response key
   *
   * @return the responseKey
   */
  public String getResponseKey() {
    return responseKey;
  }

  /**
   * set the response key
   * @param key the response key to set
   */
  public void setResponseKey(String key) {
    responseKey = key;
  }

  /**
   * Get the secrets received listener
   *
   * @return the listener
   */
  public SecretsReceivedListener getListener() {
    return listener;
  }

  /**
   * Set the secrets received listener
   *
   * @param listener
   *          the listener to set
   */
  public void setSecretsReceivedlistener(SecretsReceivedListener listener) {
    this.listener = listener;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
