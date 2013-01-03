package net.tawacentral.roger.secrets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

/**
 * Receives broadcasts from Online Sync Agents.
 * 
 * @author Chris Wood
 */
public class OnlineAgentManager extends BroadcastReceiver {
  private static final String LOG_TAG = "Secrets.OnlineAgentManager";
  
  private static final String SECRETS_PERMISSION = "net.tawacentral.roger.secrets.permission.SECRETS";

  // TODO: by convention, action names should be "<package-name>.ACTION_NAME". 
  private static final String ROLLCALL = "net.tawacentral.roger.secrets.OSARollCall";
  private static final String ROLLCALL_RESPONSE = "net.tawacentral.roger.secrets.OSARollCallResponse";
  private static final String SYNC = "net.tawacentral.roger.secrets.SYNC";
  private static final String SYNC_RESPONSE = "net.tawacentral.roger.secrets.OSASyncResponse";
  
  private static final String CLASS_ID = "classId";
  private static final String DISPLAY_NAME = "displayName";
  private static final String RESPONSE_KEY = "responseKey";
  private static final String SECRETS_ID = "secrets";

  private static Map<String,OnlineSyncAgent> INSTALLED_AGENTS
                                    = new HashMap<String,OnlineSyncAgent>();

  /* handler and listener for the current operation */
  private static Handler handler;

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "OSA received msg: " + intent.getAction());
    
    // handle roll call responses
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
        Log.d(LOG_TAG, "Received OSA rollcall resp: " + classId + " "
                + displayName);
        if (!INSTALLED_AGENTS.containsKey(classId)) {
          INSTALLED_AGENTS.put(classId, new OnlineSyncAgent(displayName,
              classId));
        } else {
          // app already known - reclaim it
          INSTALLED_AGENTS.get(classId).setAvailable(true);
        }
      }
      
    // handle sync response
    } else if (intent.getAction().equals(SYNC_RESPONSE)
            && validateResponse(intent)) {
      String classId = (String) intent.getExtras().get(CLASS_ID);
      String secretsString = intent.getStringExtra(SECRETS_ID);
      SecretsCollection secrets = null;
      if (secretsString != null) {
        try {
          secrets = SecretsCollection
                  .fromJSON(new JSONObject(secretsString));
        } catch (JSONException e) {
          Log.e(LOG_TAG, "Received invalid JSON secrets data", e);
        }
      }
      OnlineSyncAgent agent = INSTALLED_AGENTS.get(classId);
      agent.getListener().setSecrets(secrets);
      
      // run the listener code to process the received secrets
      handler.post(agent.getListener());

      // change the response key to prevent a second response being accepted
      agent.generateResponseKey();
    }
  }
  
  /*
   * Validate the response from the agent
   * @param intent
   * @return true if OK, false otherwise
   */
  private boolean validateResponse(Intent intent) {
    boolean validity = false;
    if (intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get(CLASS_ID);
      String responseKey = (String) intent.getExtras().get(RESPONSE_KEY);
      OnlineSyncAgent agent = INSTALLED_AGENTS.get(classId);
      if (agent != null) {
        if (responseKey != null && responseKey.length() > 0 &&
                agent.getResponseKey().equals(responseKey)) {
          if (handler != null && agent.getListener() != null) {
            return true;
          } else {
            Log.e(LOG_TAG,
                    "OSADataResponse received OK from agent "
                            + classId
                            + " but handler or listener not set - program error");
          }
        } else {
          Log.w(LOG_TAG,
                  "OSADataResponse received from agent " + classId
                          + " with invalid response key: current key '"
                          + agent.getResponseKey() + "', received '"
                          + responseKey + "'");
        }
      } else {
        Log.w(LOG_TAG, "OSADataResponse received from unknown app: "
                + classId);
      }
    } else {
      Log.w(LOG_TAG, "OSADataResponse received with no extras");
    }
    return validity;
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
   * Get installed agents
   * 
   * @return collection of installed agents
   */
  public static Collection<OnlineSyncAgent> getInstalledAgents() {
    return Collections.unmodifiableCollection(INSTALLED_AGENTS.values());
  }

  /**
   * Get available agents
   * 
   * @return collection of available agents
   */
  public static Collection<OnlineSyncAgent> getAvailableAgents() {
    /* first count the available agents */
    int available = 0;
    for (OnlineSyncAgent agent : INSTALLED_AGENTS.values()) {
      if (agent.isAvailable()) {
        available++;
      }
    }
    List<OnlineSyncAgent> availableAgents = new ArrayList<OnlineSyncAgent>(
            available);
    for (OnlineSyncAgent agent : INSTALLED_AGENTS.values()) {
      if ( agent.isAvailable()) {
        availableAgents.add(agent);
      }
    }
    return availableAgents;
  }

  /**
   * Sends out the rollcall broadcast and will keep track of all OSAs that
   * respond
   * 
   * We need to remember previously configured agents, otherwise
   * they will need to be reconfigured every time the app is resumed.
   * 
   * @param context
   */
  public static void sendRollCallBroadcast(Context context) {
    for (OnlineSyncAgent agent : INSTALLED_AGENTS.values()) {
      agent.setAvailable(false);
    }    
    Intent broadcastIntent = new Intent(ROLLCALL);
    context.sendBroadcast(broadcastIntent, SECRETS_PERMISSION);    
    Log.d(LOG_TAG, "sent broadcast");
  }

  /**
   * Sends secrets to the specified OSA. 
   * 
   * Returns true if secrets are successfully sent, but makes no guarantees
   * that the secrets were received.
   * 
   * A one-time key is sent to the OSA and must be returned in the reply for
   * it to be considered valid.
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
    agent.generateResponseKey();
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
    protected SecretsCollection secrets;
    private SecretsListActivity activity;
    private String agentName;

    protected SecretsReceivedListener(SecretsListActivity activity,
            String agentName) {
      this.activity = activity;
      this.agentName = agentName;
    }

    /**
     * Accessor for secrets
     * @return secrets
     */
    public SecretsCollection getSecrets() {
      return secrets;
    }

    /**
     * Accessor for secrets
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
