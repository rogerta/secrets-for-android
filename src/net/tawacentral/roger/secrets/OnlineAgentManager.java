package net.tawacentral.roger.secrets;

import java.security.SecureRandom;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

/**
 * Provides support for Online Sync Agents.
 * 
 * Sync process overview
 * 
 * On each resume a roll call is broadcast. Agents that respond are recorded in
 * a list of available agents.
 * 
 * Sync operations are always initiated from secrets. A sync request is
 * broadcast at the selected (or only) available agent, together with the
 * unencrypted secrets and a one-time validation key. The sync response is
 * validated against the key,and the returned secrets (updated or deleted) are
 * merged with the existing ones.
 * 
 * @author Chris Wood
 */
public class OnlineAgentManager extends BroadcastReceiver {
  private static final String LOG_TAG = "OnlineAgentManager";

  private static final String SECRETS_PERMISSION =
      "net.tawacentral.roger.secrets.permission.SECRETS";

  // broadcast ACTIONS
  private static final String ROLLCALL =
      "net.tawacentral.roger.secrets.OSA_ROLLCALL";
  private static final String ROLLCALL_RESPONSE =
      "net.tawacentral.roger.secrets.OSA_ROLLCALL_RESPONSE";
  private static final String SYNC = "net.tawacentral.roger.secrets.SYNC";
  private static final String SYNC_RESPONSE =
      "net.tawacentral.roger.secrets.SYNC_RESPONSE";

  private static final String CLASS_ID = "classId";
  private static final String DISPLAY_NAME = "displayName";
  private static final String RESPONSE_KEY = "responseKey";
  private static final String SECRETS_ID = "secrets";

  private static final int RESPONSEKEY_LENGTH = 8;

  private static Map<String, OnlineSyncAgent> AVAILABLE_AGENTS =
      new HashMap<String, OnlineSyncAgent>();

  // handler and listener for the current operation
  private static Handler handler;

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "OSA received msg: " + intent.getAction());

    // handle roll call response
    if (intent.getAction().equals(ROLLCALL_RESPONSE)
        && intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get(CLASS_ID);
      String displayName = (String) intent.getExtras().get(DISPLAY_NAME);
      if (classId == null || classId.length() == 0 || displayName == null
          || displayName.length() == 0) {
        // invalid info, so do not add it
        Log.e(LOG_TAG, "Received invalid OSA rollcall resp: classId=" + classId
            + ",displayName=" + displayName);
      } else {
        AVAILABLE_AGENTS
            .put(classId, new OnlineSyncAgent(displayName, classId));
        Log.d(LOG_TAG, "Received OSA rollcall resp: " + classId + " "
            + displayName);
      }

      // handle sync response
    } else if (intent.getAction().equals(SYNC_RESPONSE)
        && validateResponse(intent)) {
      String classId = (String) intent.getExtras().get(CLASS_ID);
      String secretsString = intent.getStringExtra(SECRETS_ID);
      SecretsCollection secrets = null;
      if (secretsString != null) {
        try {
          secrets = SecretsCollection.fromJSON(new JSONObject(secretsString));
        } catch (JSONException e) {
          Log.e(LOG_TAG, "Received invalid JSON secrets data", e);
        }
      }
      OnlineSyncAgent agent = AVAILABLE_AGENTS.get(classId);
      agent.getListener().setSecrets(secrets);

      // run the listener code to process the received secrets
      handler.post(agent.getListener());

      // change the response key to prevent a second response being accepted
      agent.setResponseKey(generateResponseKey());
    }
  }

  /*
   * Validate the sync response from the agent. The key in the response must
   * match the one sent in the original sync request.
   * 
   * @param intent
   * 
   * @return true if response is OK, false otherwise
   */
  private boolean validateResponse(Intent intent) {
    boolean validity = false;
    if (intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get(CLASS_ID);
      String responseKey = (String) intent.getExtras().get(RESPONSE_KEY);
      OnlineSyncAgent agent = AVAILABLE_AGENTS.get(classId);
      if (agent != null) {
        if (responseKey != null && responseKey.length() > 0
            && agent.getResponseKey().equals(responseKey)) {
          if (handler != null && agent.getListener() != null) {
            return true;
          } else {
            Log.e(LOG_TAG, "OSADataResponse received OK from agent " + classId
                + " but handler or listener not set - program error");
          }
        } else {
          Log.w(
              LOG_TAG,
              "OSADataResponse received from agent " + classId
                  + " with invalid response key: current key '"
                  + agent.getResponseKey() + "', received '" + responseKey
                  + "'");
        }
      } else {
        Log.w(LOG_TAG, "OSADataResponse received from unknown app: " + classId);
      }
    } else {
      Log.w(LOG_TAG, "OSADataResponse received with no extras");
    }
    return validity;
  }

  /**
   * Generate a new response key
   * 
   * @return String response key
   */
  public static String generateResponseKey() {
    SecureRandom random = new SecureRandom();
    byte[] keyBytes = new byte[RESPONSEKEY_LENGTH];
    random.nextBytes(keyBytes);
    return new String(keyBytes);
  }

  /**
   * Set the thread handler
   * 
   * @param handler
   */
  public static void setHandler(Handler handler) {
    OnlineAgentManager.handler = handler;
  }

  /**
   * Get available agents
   * 
   * @return collection of installed agents
   */
  public static Collection<OnlineSyncAgent> getAvailableAgents() {
    return Collections.unmodifiableCollection(AVAILABLE_AGENTS.values());
  }

  /**
   * Sends out the rollcall broadcast and will keep track of all OSAs that
   * respond.
   * 
   * Forget previous agents - only ones that respond are considered available.
   * 
   * @param context
   */
  public static void sendRollCallBroadcast(Context context) {
    AVAILABLE_AGENTS.clear();
    Intent broadcastIntent = new Intent(ROLLCALL);
    context.sendBroadcast(broadcastIntent, SECRETS_PERMISSION);
    Log.d(LOG_TAG, "sent broadcast");
  }

  /**
   * Sends secrets to the specified OSA.
   * 
   * Returns true if secrets are successfully sent, but makes no guarantees that
   * the secrets were received.
   * 
   * A one-time key is sent to the OSA and must be returned in the reply for it
   * to be considered valid.
   * 
   * @param agent
   * @param secrets
   * @param activity
   * @return true if secrets were sent
   */
  public static boolean sendSecrets(OnlineSyncAgent agent,
                                    SecretsCollection secrets,
                                    SecretsListActivity activity) {
    agent.setSecretsReceivedlistener(new SecretsReceivedListener(activity,
        agent.getDisplayName()));
    agent.setResponseKey(generateResponseKey());
    try {
      Intent secretsIntent = new Intent(SYNC);
      secretsIntent.setPackage(agent.getClassId());
      secretsIntent.putExtra(RESPONSE_KEY, agent.getResponseKey());
      String secretString = secrets.toJSON().toString();
      secretsIntent.putExtra(SECRETS_ID, secretString);

      activity.sendBroadcast(secretsIntent, SECRETS_PERMISSION);
      Log.d(LOG_TAG, "Secrets sent to OSA " + agent.getClassId());
      return true;
    } catch (Exception e) {
      Log.e(LOG_TAG, "Error sending secrets to OSA", e);
      // ignore the exception, false will be returned below
    }
    return false;
  }

  /**
   * Listener that is called when an OSA replies to a request. The secrets
   * member variable will contain the secrets sent by the OSA
   */
  public static class SecretsReceivedListener implements Runnable {
    private SecretsCollection secrets;
    private SecretsListActivity activity;
    private String agentName;

    protected SecretsReceivedListener(SecretsListActivity activity,
        String agentName) {
      this.activity = activity;
      this.agentName = agentName;
    }

    /**
     * Accessor for secrets
     * 
     * @return secrets
     */
    public SecretsCollection getSecrets() {
      return secrets;
    }

    /**
     * Accessor for secrets
     * 
     * @param secrets
     */
    public void setSecrets(SecretsCollection secrets) {
      this.secrets = secrets;
    }

    /**
     * Call back so the secrets are processed in the context of the activity.
     */
    public void run() {
      activity.syncSecrets(secrets, agentName);
    }
  }
}
