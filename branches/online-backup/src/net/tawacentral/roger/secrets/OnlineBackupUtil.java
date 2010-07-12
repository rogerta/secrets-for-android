package net.tawacentral.roger.secrets;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper class for managing OBAS
 */
public class OnlineBackupUtil {
  private static final String LOG_TAG = "Secrets";
  /**
   * Returns the Secret that is assigned to the OBA
   * @param app
   * @param secrets list of secrets to check
   * @return Secret for the OBA or null if not found
   */
  public static Secret findOBASecret(OnlineBackupApplication app, List<Secret> secrets) {
    for (Secret s : secrets) {
      if(s.getOBAs().contains(app.getClassId()))
        return s;
    }
    return null;
  }

  /**
   * Efficiently determines which OBAs are configured.
   *
   * @param secrets list of secrets to check for OBA note
   * @return list of configured OBAs
   */
  public static List<OnlineBackupApplication> getConfiguredApps(List<Secret> secrets) {
    // set that will hold all the classIds currently assigned to a secret
    Set<String> configuredClassIds = new HashSet<String>();
    // now find out which OBA's are configured
    for (Secret secret : secrets) {
      configuredClassIds.addAll(secret.getOBAs());
    }

    // final list of configured apps
    List<OnlineBackupApplication> configuredApps = new ArrayList<OnlineBackupApplication>(secrets.size());


    for (OnlineBackupApplication app : OnlineBackupReceiver.getInstalledBackupApps()) {
      if (configuredClassIds.contains(app.getClassId())) {
        Log.d(LOG_TAG, "Adding app to configed list: " + app);
        configuredApps.add(app);
      }
    }
    return configuredApps;
  }

  /**
   * Configures an OBA to use the supplied Secret, removes the OBA from any other secrets
   * found in the supplied list of secrets
   * @param app app to configure
   * @param secret secret to use
   * @param secrets list of secrets to check if app was already configured
   */
  public static void configureOBA(OnlineBackupApplication app, Secret secret, List<Secret> secrets) {
    Secret curSecret = findOBASecret(app, secrets);
    if(curSecret != null)
      curSecret.removeOBA(app);
    secret.addOBA(app);
  }
}
