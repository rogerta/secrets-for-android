package net.tawacentral.roger.secrets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

/**
 * Receives broadcasts from online agent apps that wish to be presented to the
 * user for secrets service (backup/restore and sync).
 * 
 * Note: during a restore or sync operation this class is modal (because of the
 * handler and listener).
 * 
 * @author Ryan Dearing
 * @author Chris Wood
 */
public class OnlineAgentManager extends BroadcastReceiver {
  private static final String LOG_TAG = "Secrets";

  private static Set<OnlineSyncAgent> INSTALLED_AGENTS = Collections
          .synchronizedSet(new HashSet<OnlineSyncAgent>());

  /* handler and listener for the current operation */
  private static Handler handler;

  private static SecretsReceivedListener secretsListener;

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "OSA received msg: " + intent.getAction());
    // handle roll call responses
    if (intent.getAction().equals(
            "net.tawacentral.roger.secrets.OSARollCallResponse")
            && intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get("classId");
      String displayName = (String) intent.getExtras().get("displayName");

      Log.d(LOG_TAG, "Received OSA rollcall resp: " + classId + " "
              + displayName);
      try {
        // try creating an OSA (this throws an exception if displayName or
        // classId is null or empty string)
        OnlineSyncAgent app = new OnlineSyncAgent(displayName, classId);
        if (!INSTALLED_AGENTS.contains(app)) {
          INSTALLED_AGENTS.add(app);
        } else {
          // app already known - reclaim it
          findAgent(app).setAvailable(true);
        }
      } catch (IllegalArgumentException e) {
        // they sent invalid info, so they do not get added
        Log.e(LOG_TAG, "Received invalid OSA rollcall resp: classId=" + classId
                + ",displayName=" + displayName);
      }
      // handle sync response
    } else if (intent.getAction().equals(
            "net.tawacentral.roger.secrets.OSASyncResponse")
            && intent.getExtras() != null) {
      String secretsString = intent.getStringExtra("secrets");
      final SecretsCollection secrets = SecretsCollection
              .getSecretsFromJSONString(secretsString);
      if (handler != null && secretsListener != null) {
        secretsListener.setSecrets(secrets);
        Log.d(LOG_TAG, "setSecrets() called with secrets: "
                + (secrets == null ? "null" : secrets.size()));
        handler.post(secretsListener);
        // this ensures that if more than 1 copy of the response comes in we
        // will not fire the listener again
        // this will also prevent an app from randomly sending a response when
        // none requested
        handler = null;
        secretsListener = null;
      } else {
        Log.w(LOG_TAG, "OSADataResponse received but handler not set");
      }
    }
  }

  /**
   * Get installed agents
   * 
   * @return set of installed agents
   */
  public static Set<OnlineSyncAgent> getInstalledAgents() {
    return Collections.unmodifiableSet(INSTALLED_AGENTS);
  }

  /**
   * Determines which agents are configured.
   * 
   * @return (ordered) list of configured OSAs
   */
  public static List<OnlineSyncAgent> getConfiguredAgents() {
    /* count the configured agents */
    int configured = 0;
    for (OnlineSyncAgent agent : INSTALLED_AGENTS) {
      if (agent.getConfigSecret() != null && agent.isAvailable()) {
        configured++;
      }
    }
    List<OnlineSyncAgent> configuredAgents = new ArrayList<OnlineSyncAgent>(
            configured);
    for (OnlineSyncAgent agent : INSTALLED_AGENTS) {
      if (agent.getConfigSecret() != null && agent.isAvailable()) {
        configuredAgents.add(agent);
      }
    }
    return configuredAgents;
  }

  /**
   * Find the "test" agent in the agent collection.
   * 
   * @param thisAgent
   * @return contained agent or null
   */
  public static OnlineSyncAgent findAgent(final OnlineSyncAgent thisAgent) {
    for (OnlineSyncAgent agent : INSTALLED_AGENTS) {
      if (agent.equals(thisAgent)) {
        return agent;
      }
    }
    return null;
  }

  /**
   * Sends out the rollcall broadcast and will keep track of all OSAs that
   * respond
   * 
   * CTW However, we need to remember previously configured agents, otherwise
   * they will need to be reconfigured every time the app is resumed.
   * 
   * @param context
   */
  public static void sendRollCallBroadcast(Context context) {
    for (OnlineSyncAgent agent : INSTALLED_AGENTS) {
      agent.setAvailable(false);
    }
    Intent broadcastIntent = new Intent(
            "net.tawacentral.roger.secrets.OSARollCall");
    context.sendBroadcast(broadcastIntent,
            "net.tawacentral.roger.secrets.permission.SECRETS");
    Log.d(LOG_TAG, "sent broadcast");
  }

  /**
   * Sends secrets to the specified OSA, and process the (sync) response
   * 
   * @param app
   * @param secrets
   * @param context
   * @param handler
   *          handler for the main activity
   * @param listener
   *          runnable class to be called to process response
   * @return true if secrets were sent
   */
  public static boolean sendSecrets(OnlineSyncAgent app,
                                    SecretsCollection secrets, Context context,
                                    Handler handler,
                                    SecretsReceivedListener listener) {
    OnlineAgentManager.handler = handler;
    OnlineAgentManager.secretsListener = listener;
    return sendSecrets(app, secrets, context);
  }

  /**
   * Sends secrets to the specified OSA, this will also send the username,
   * password, and email configured for the OSA. This returns true if secrets
   * are successfully sent, but makes no guarantees that the secrets were
   * received. An OSA could produce some sort of notification upon receipt if
   * appropriate.
   * 
   * @param app
   * @param secrets
   * @param context
   * @return true if secrets were sent
   */
  public static boolean sendSecrets(OnlineSyncAgent app,
                                    SecretsCollection secrets, Context context) {
    Secret secret = app.getConfigSecret();
    // secret == null should not happen, because it would not be considered
    // configured
    if (secret != null) {
      try {
        Intent secretsIntent = new Intent(app.getClassId());
        secretsIntent.putExtra("salt", SecurityUtils.getSalt());
        secretsIntent.putExtra("rounds", SecurityUtils.getRounds());
        String secretString = secrets.putSecretsToJSONString();

        /* pass values from the configured secret */
        secretsIntent.putExtra("secrets", secretString);
        secretsIntent.putExtra("username", secret.getUsername());
        secretsIntent.putExtra("email", secret.getEmail());
        secretsIntent.putExtra("password", secret.getPassword(false));
        secretsIntent.putExtra("note", secret.getNote());

        context.sendBroadcast(secretsIntent,
                "net.tawacentral.roger.secrets.permission.SECRETS");
        Log.d(LOG_TAG, "Secrets sent to OSA " + app.getClassId());
        return true;
      } catch (RuntimeException e) {
        Log.e(LOG_TAG, "Error sending secrets to OSA", e);
        // ignore the exception, false will be returned below
      }
    }
    return false;
  }

  /**
   * Listener that is called when an OSA replies to a request. The "secrets"
   * member variable will contain the secrets sent by the OSA
   */
  public static abstract class SecretsReceivedListener implements Runnable {
    protected SecretsCollection secrets;

    protected SecretsReceivedListener() {
    }

    public SecretsCollection getSecrets() {
      return secrets;
    }

    public void setSecrets(SecretsCollection secrets) {
      this.secrets = secrets;
    }
  }
}
