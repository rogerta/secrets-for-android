package net.tawacentral.roger.secrets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

/**
 * Receives broadcasts from Online Sync Agents.
 * 
 * @author Ryan Dearing
 * @author Chris Wood
 */
public class OnlineAgentManager extends BroadcastReceiver {
  private static final String LOG_TAG = "Secrets";
  
  private static Map<String,OnlineSyncAgent> INSTALLED_AGENTS
                                    = new HashMap<String,OnlineSyncAgent>();

  /* handler and listener for the current operation */
  private static Handler handler;
//  private static SecretsReceivedListener secretsListener;

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "OSA received msg: " + intent.getAction());
    
    // handle roll call responses
    if (intent.getAction().equals(
            "net.tawacentral.roger.secrets.OSARollCallResponse")
            && intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get("classId");
      String displayName = (String) intent.getExtras().get("displayName");
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
    } else if (intent.getAction().equals(
            "net.tawacentral.roger.secrets.OSASyncResponse")
            && validateResponse(intent)) {
      String classId = (String) intent.getExtras().get("classId");
      String secretsString = intent.getStringExtra("secrets");
      final SecretsCollection secrets = SecretsCollection
              .getSecretsFromJSONString(secretsString);
      OnlineSyncAgent agent = INSTALLED_AGENTS.get(classId);
      agent.getListener().setSecrets(secrets);
      
      // run the listener code to process the received secrets
      handler.post(agent.getListener());

      // change the response key to prevent a second response being accepted/
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
      String classId = (String) intent.getExtras().get("classId");
      String responseKey = (String) intent.getExtras().get("responseKey");
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
   * Determines which agents are configured.
   * 
   * @return collection of configured OSAs
   */
  public static Collection<OnlineSyncAgent> getConfiguredAgents() {
    /* first count the configured agents */
    int configured = 0;
    for (OnlineSyncAgent agent : INSTALLED_AGENTS.values()) {
      if (agent.getConfigSecret() != null && agent.isAvailable()) {
        configured++;
      }
    }
    List<OnlineSyncAgent> configuredAgents = new ArrayList<OnlineSyncAgent>(
            configured);
    for (OnlineSyncAgent agent : INSTALLED_AGENTS.values()) {
      if (agent.getConfigSecret() != null && agent.isAvailable()) {
        configuredAgents.add(agent);
      }
    }
    return configuredAgents;
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
    Intent broadcastIntent = new Intent(
            "net.tawacentral.roger.secrets.OSARollCall");
    context.sendBroadcast(broadcastIntent,
            "net.tawacentral.roger.secrets.permission.SECRETS");
    Log.d(LOG_TAG, "sent broadcast");
  }

  /**
   * Sends secrets to the specified OSA, this will also send the data from the
   * configured secret. This returns true if secrets are successfully sent, 
   * but makes no guarantees that the secrets were received.
   * 
   * A one-time key is sent to the OSA and must be returned in the reply for
   * it to be consided valid.
   * 
   * @param agent
   * @param secrets
   * @param activity 
   * @return true if secrets were sent
   */
  public static boolean sendSecrets(OnlineSyncAgent agent,
                                    SecretsCollection secrets, 
                                    SecretsListActivity activity) {
    agent.setSecretsReceivedlistener(new SecretsReceivedListener(activity));
    agent.generateResponseKey();
    Secret secret = agent.getConfigSecret();
    // secret == null should not happen, because it would not be considered
    // configured
    if (secret != null) {
      try {
        Intent secretsIntent = new Intent(agent.getClassId());
        secretsIntent.putExtra("responseKey", agent.getResponseKey());
        String secretString = secrets.putSecretsToJSONString();

        /* pass values from the configured secret */
        secretsIntent.putExtra("secrets", secretString);
        secretsIntent.putExtra("username", secret.getUsername());
        secretsIntent.putExtra("email", secret.getEmail());
        secretsIntent.putExtra("password", secret.getPassword(false));
        secretsIntent.putExtra("note", secret.getNote());

        activity.sendBroadcast(secretsIntent,
                "net.tawacentral.roger.secrets.permission.SECRETS");
        Log.d(LOG_TAG, "Secrets sent to OSA " + agent.getClassId());
        return true;
      } catch (RuntimeException e) {
        Log.e(LOG_TAG, "Error sending secrets to OSA", e);
        // ignore the exception, false will be returned below
      }
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

    protected SecretsReceivedListener(SecretsListActivity activity) {
      this.activity = activity;
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
      activity.syncSecrets(secrets);
    }
  }
}
