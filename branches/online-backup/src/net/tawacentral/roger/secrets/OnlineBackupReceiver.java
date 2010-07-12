package net.tawacentral.roger.secrets;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.util.Log;

import java.util.*;

/**
 * Receives broadcasts from online backup providers that wish to be presented to the user
 * for backup.
 *
 * @author Ryan Dearing
 */
public class OnlineBackupReceiver extends BroadcastReceiver {
  private static final String LOG_TAG = "Secrets";
  private static Set<OnlineBackupApplication> INSTALLED_BACKUP_APPS =
      Collections.synchronizedSet(new HashSet<OnlineBackupApplication>());
  private static Handler handler;
  private static SecretsReceivedListener secretsListener;

  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "OBA received msg");
    // handle roll call responses
    if (intent.getAction().equals("net.tawacentral.roger.secrets.OBARollCallResponse") && intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get("classId");
      String displayName = (String) intent.getExtras().get("displayName");

      Log.d(LOG_TAG, "OBA received rollcall resp: " + classId + " " + displayName);
      try {
        // try creating an OBA (this throws an exception if displayName or classId is null or empty string)
        OnlineBackupApplication app = new OnlineBackupApplication(displayName, classId);
        if (!INSTALLED_BACKUP_APPS.contains(app))
          INSTALLED_BACKUP_APPS.add(app);
      } catch (IllegalArgumentException e) {
        // they sent invalid info, so they do not get added
      }
      // handle restore responses
    } else if(intent.getAction().equals("net.tawacentral.roger.secrets.OBARestoreResponse") && intent.getExtras() != null) {
      byte[] encSecrets = intent.getExtras().getByteArray("secrets");
      final ArrayList<Secret> secrets = FileUtils.restoreSecretsStream(context, encSecrets);
      if(handler != null && secretsListener != null) {
        secretsListener.setSecrets(secrets);
        handler.post(secretsListener);
        // this ensures that if more than 1 copy of the response comes in we will not fire the listener again
        // this will also prevent an app from randomly sending a response when no requested
        handler = null;
        secretsListener = null;
      }
    }
  }

  public static Set<OnlineBackupApplication> getInstalledBackupApps() {
    return Collections.unmodifiableSet(INSTALLED_BACKUP_APPS);
  }

  /**
   * Sends out the rollcall broadcast and will keep track of all OBAs that respond
   * @param context
   */
  public static void sendRollCallBroadcast(Context context) {
    INSTALLED_BACKUP_APPS.clear();
    /* TODO (rdearing) at some point we may want to use an orderedBroadcast with a last receiver so we
     know when all the OBAs have had a chance to respond and we can maybe show a toast saying
      we found some new installed OBAs, on the other hand OBAs can abort the broadcast if ordered*/
    Intent broadcastIntent = new Intent("net.tawacentral.roger.secrets.OBARollCall");
    context.sendBroadcast(broadcastIntent, "net.tawacentral.roger.secrets.permission.ENCRYPTED_SECRETS");
    Log.d(LOG_TAG, "sent broadcast");
  }

  /**
   * Sends secrets to the specified OBA, this will also send the username, password, and email configured for the OBA.
   * This returns true if secrets are successfully sent, but makes no guarantees that the secrets were received. A well
   * performing OBA would probably display a notification upon receipt.
   *
   * @param app
   * @param secrets
   * @param context
   * @return true if secrets were sent
   */
  public static boolean sendSecrets(OnlineBackupApplication app, List<Secret> secrets, Context context) {
    Secret secret = OnlineBackupUtil.findOBASecret(app, secrets);
    // secret == null should not happen, because it would not be considered configured
    if (secret != null) {
      try {
        Intent secretsIntent = new Intent(app.getClassId());
        secretsIntent.putExtra("salt", SecurityUtils.getSalt());
        byte[] secretStream = FileUtils.secretsStream(context, SecurityUtils.getEncryptionCipher(), secrets);
        secretsIntent.putExtra("secrets", secretStream);
        secretsIntent.putExtra("username", secret.getUsername());
        secretsIntent.putExtra("email", secret.getEmail());
        secretsIntent.putExtra("password", secret.getPassword(false));
        context.sendBroadcast(secretsIntent, "net.tawacentral.roger.secrets.permission.ENCRYPTED_SECRETS");
        return true;
      } catch (RuntimeException e) {
        Log.e(LOG_TAG, "Error sending secrets to OBA", e);
        // ignore the exception, false will be returned below
      }
    }
    return false;
  }

  /**
   * Requests secrets for restoring from the specified OBA. The listener will be posted via the specified handler.
   * @param app
   * @param secrets secrets that are used to find the username, password, and email for the OBA
   * @param context
   * @param handler
   * @param listener
   * @return
   */
  public static boolean sendRestoreRequest(OnlineBackupApplication app, List<Secret> secrets,
                                           Context context, Handler handler, SecretsReceivedListener listener) {
    Secret secret = OnlineBackupUtil.findOBASecret(app, secrets);
    // secret == null should not happen, because it wouldnt be considered configured
    if (secret != null) {
      try {
        Intent secretsIntent = new Intent(app.getClassId());
        secretsIntent.putExtra("username", secret.getUsername());
        secretsIntent.putExtra("email", secret.getEmail());
        secretsIntent.putExtra("password", secret.getPassword(false));
        secretsIntent.putExtra("restore", true);
        OnlineBackupReceiver.handler = handler;
        OnlineBackupReceiver.secretsListener = listener;
        context.sendBroadcast(secretsIntent, "net.tawacentral.roger.secrets.permission.ENCRYPTED_SECRETS");
        return true;
      } catch (RuntimeException e) {
        Log.e(LOG_TAG, "Error sending secrets to OBA", e);
        // ignore the exception, false will be returned below
      }
    }
    return false;
  }

  /**
   * Listener that is called when an OBA replies to a restore request. The "secrets" member variable will contain
   * the secrets sent by the OBA
   * @see #getSecrets()
   */
  public static abstract class SecretsReceivedListener implements Runnable {
    protected ArrayList<Secret> secrets;

    protected SecretsReceivedListener() {
    }

    public ArrayList<Secret> getSecrets() {
      return secrets;
    }

    public void setSecrets(ArrayList<Secret> secrets) {
      this.secrets = secrets;
    }
  }
}
