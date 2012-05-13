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
 * Receives broadcasts from online agent apps that wish to be presented to the user
 * for secrets service (backup/restore and sync).
 * 
 * Note: during a restore or sync operation this class is modal (because of the 
 * handler and listener).
 *
 * @author Ryan Dearing
 * @author Chris Wood
 */
public class OnlineAgentManager extends BroadcastReceiver {
  private static final String LOG_TAG = "Secrets";
  private static Set<OnlineSecretsAgent> INSTALLED_AGENTS =
      Collections.synchronizedSet(new HashSet<OnlineSecretsAgent>());
  
  /* handler and listener for the current operation */
  private static Handler handler;
  private static SecretsReceivedListener secretsListener;

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "OSA received msg: " + intent.getAction());
    // handle roll call responses
    if (intent.getAction().equals("net.tawacentral.roger.secrets.OSARollCallResponse") && intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get("classId");
      String displayName = (String) intent.getExtras().get("displayName");

      Log.d(LOG_TAG, "OSA received rollcall resp: " + classId + " " + displayName);
      try {
        // try creating an OSA (this throws an exception if displayName or classId is null or empty string)
        OnlineSecretsAgent app = new OnlineSecretsAgent(displayName, classId);
        if (((String) intent.getExtras().get("type")).equals("sync")) {
        	app.setAgentType(OnlineSecretsAgent.TYPE_SYNC);
        } else {
        	app.setAgentType(OnlineSecretsAgent.TYPE_BACKUP);
        }
        if (!INSTALLED_AGENTS.contains(app)) {
        	INSTALLED_AGENTS.add(app);
        } else {
        	// app already known - reclaim it
        	findAgent(app).setLost(false);
        }
      } catch (IllegalArgumentException e) {
        // they sent invalid info, so they do not get added
      }
      // handle restore and sync responses
    } else if(intent.getAction().equals("net.tawacentral.roger.secrets.OSADataResponse") && intent.getExtras() != null) {
      byte[] encSecrets = intent.getByteArrayExtra("secrets");
      //TODO only uses current cipher at the moment
      final SecretsCollection secrets = FileUtils.getSecretsFromEncryptedJSONStream(encSecrets);
      if(handler != null && secretsListener != null) {
        secretsListener.setSecrets(secrets);
        Log.d(LOG_TAG, "setSecrets() called with secrets: " + (secrets == null ? "null" : secrets.size()));
        handler.post(secretsListener);
        // this ensures that if more than 1 copy of the response comes in we will not fire the listener again
        // this will also prevent an app from randomly sending a response when none requested
        handler = null;
        secretsListener = null;
      } else {
      	Log.w(LOG_TAG, "OSADataResponse received but handler not set");
      }
    }
  }

  /**
   * Get installed agents
   * @return set of installed agents
   */
  public static Set<OnlineSecretsAgent> getInstalledAgents() {
    return Collections.unmodifiableSet(INSTALLED_AGENTS);
  }
  
  /**
   * Determines which agents are configured.
   *
   * @return (ordered) list of configured OSAs
   */
  public static List<OnlineSecretsAgent> getConfiguredAgents() {
  	/* count the configured agents */
  	int configured = 0;
  	for (OnlineSecretsAgent agent : INSTALLED_AGENTS) {
			if (agent.getConfigSecret() != null && !agent.isLost()) {
				configured++;
			}
		}
  	List<OnlineSecretsAgent> configuredAgents = new ArrayList<OnlineSecretsAgent>(configured);
  	for (OnlineSecretsAgent agent : INSTALLED_AGENTS) {
			if (agent.getConfigSecret() != null && !agent.isLost()) {
				configuredAgents.add(agent);
			}
		}
  	return configuredAgents;
  }
  
  /**
   * Find the "test" agent in the agent collection.
   * @param thisAgent
   * @return contained agent or null
   */
  public static OnlineSecretsAgent findAgent(final OnlineSecretsAgent thisAgent) {
  	for (OnlineSecretsAgent agent : INSTALLED_AGENTS) {
  		if (agent.equals(thisAgent)) {
  			return agent;
  		}
  	}
  	return null;
  }

  /**
   * Sends out the rollcall broadcast and will keep track of all OSAs that respond
   * 
   * CTW However, we need to remember previously configured agents, otherwise
   * they will need to be reconfigured every time the app is resumed.
   * 
   * @param context
   */
  public static void sendRollCallBroadcast(Context context) {
//    INSTALLED_AGENTS.clear();
  	for (OnlineSecretsAgent agent : INSTALLED_AGENTS) {
			agent.setLost(true);
		}
    /* TODO (rdearing) at some point we may want to use an orderedBroadcast with a last receiver so we
     know when all the OSAs have had a chance to respond and we can maybe show a toast saying
      we found some new installed OSAs, on the other hand OSAs can abort the broadcast if ordered*/
    Intent broadcastIntent = new Intent("net.tawacentral.roger.secrets.OSARollCall");
    context.sendBroadcast(broadcastIntent, "net.tawacentral.roger.secrets.permission.ENCRYPTED_SECRETS");
    Log.d(LOG_TAG, "sent broadcast");
  }

  /**
   * Sends secrets to the specified OSA, and process the (sync) response
   *
   * @param app
   * @param secrets
   * @param context
   * @param handler handler for the main activity
   * @param listener runnable class to be called to process response
   * @return true if secrets were sent
   */
//  public static boolean sendSecrets(OnlineSecretsAgent app, List<Secret> secrets,
  public static boolean sendSecrets(OnlineSecretsAgent app, SecretsCollection secrets,
      															Context context, Handler handler, SecretsReceivedListener listener) {
  	OnlineAgentManager.handler = handler;
    OnlineAgentManager.secretsListener = listener;
    return sendSecrets(app, secrets, context);
  }

  /**
   * Sends secrets to the specified OSA, this will also send the username, password, and email configured for the OSA.
   * This returns true if secrets are successfully sent, but makes no guarantees that the secrets were received. An OSA
   * could produce some sort of notification upon receipt if appropriate.
   *
   * @param app
   * @param secrets
   * @param context
   * @return true if secrets were sent
   */
//  public static boolean sendSecrets(OnlineSecretsAgent app, List<Secret> secrets, Context context) {
  public static boolean sendSecrets(OnlineSecretsAgent app, SecretsCollection secrets, Context context) {
    Secret secret = app.getConfigSecret();
    // secret == null should not happen, because it would not be considered configured
    if (secret != null) {
      try {
        Intent secretsIntent = new Intent(app.getClassId());
        secretsIntent.putExtra("salt", SecurityUtils.getSalt());
        secretsIntent.putExtra("rounds", SecurityUtils.getRounds());
        byte[] secretStream = FileUtils.putSecretsToEncryptedJSONStream(SecurityUtils.getEncryptionCipher(), secrets);
        
        /* pass values from the configured secret */
        secretsIntent.putExtra("secrets", secretStream);
        secretsIntent.putExtra("username", secret.getUsername());
        secretsIntent.putExtra("email", secret.getEmail());
        secretsIntent.putExtra("password", secret.getPassword(false));
        secretsIntent.putExtra("note", secret.getNote());
        
        context.sendBroadcast(secretsIntent, "net.tawacentral.roger.secrets.permission.ENCRYPTED_SECRETS");
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
   * Requests secrets for restoring from the specified OSA. The listener will be posted via the specified handler.
   * @param app
   * @param secrets secrets that are used to find the username, password, and email for the OSA
   * @param context
   * @param handler
   * @param listener
   * @return true if secrets sent ok, false otherwise
   */
  public static boolean sendRestoreRequest(OnlineSecretsAgent app, List<Secret> secrets,
                                           Context context, Handler handler, SecretsReceivedListener listener) {
  	Secret secret = app.getConfigSecret();
    // secret == null should not happen, because it wouldnt be considered configured
    if (secret != null) {
      try {
        Intent secretsIntent = new Intent(app.getClassId());
        secretsIntent.putExtra("username", secret.getUsername());
        secretsIntent.putExtra("email", secret.getEmail());
        secretsIntent.putExtra("password", secret.getPassword(false));
        secretsIntent.putExtra("restore", true);
        OnlineAgentManager.handler = handler;
        OnlineAgentManager.secretsListener = listener;
        context.sendBroadcast(secretsIntent, "net.tawacentral.roger.secrets.permission.ENCRYPTED_SECRETS");
        return true;
      } catch (RuntimeException e) {
        Log.e(LOG_TAG, "Error sending restore request to OSA", e);
        // ignore the exception, false will be returned below
      }
    }
    return false;
  }

  /**
   * Listener that is called when an OSA replies to a request. The "secrets" member variable will contain
   * the secrets sent by the OSA
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
