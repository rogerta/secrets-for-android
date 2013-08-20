// Copyright (c) 2009, Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

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
 * Multiple concurrent sync requests are not supported. It is the caller's
 * responsibility to ensure there is no active request when calling
 * sendSecrets().
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
  private static final String SYNC_CANCEL =
      "net.tawacentral.roger.secrets.SYNC_CANCEL";

  private static final String CLASS_ID = "classId";
  private static final String DISPLAY_NAME = "displayName";
  private static final String RESPONSE_KEY = "responseKey";
  private static final String SECRETS_ID = "secrets";

  // for the current request
  private static OnlineSyncAgent requestAgent;
  private static SecretsListActivity responseActivity;
  private static boolean active;
  
  /*
   * The response key is a randomly generated string that is provided to the
   * OSA as part of the sync request and must be returned in the response in
   * order for it to be accepted. The key is changed when the response is
   * received to ensure that any subsequent or unsolicited responses are
   * rejected.
   */
  private static String responseKey;
  private static final int RESPONSEKEY_LENGTH = 8;

  private static Map<String, OnlineSyncAgent> AVAILABLE_AGENTS =
      new HashMap<String, OnlineSyncAgent>();

  /*
   * Handle the received broadcast
   */
  @Override
  public void onReceive(Context context, Intent intent) {
    Log.d(LOG_TAG, "Agent Manager received msg: " + intent.getAction());

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
      String secretsString = intent.getStringExtra(SECRETS_ID);
      SecretsCollection secrets = null;
      if (secretsString != null) {
        try {
          secrets = SecretsCollection.fromJSON(new JSONObject(secretsString));
        } catch (JSONException e) {
          Log.e(LOG_TAG, "Received invalid JSON secrets data", e);
        }
      }
      active = false;
      responseActivity.syncSecrets(secrets, requestAgent.getDisplayName());
      requestAgent = null;
      responseKey = generateResponseKey(); // change response key
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
    if (intent.getExtras() != null) {
      String classId = (String) intent.getExtras().get(CLASS_ID);
      String responseKey = (String) intent.getExtras().get(RESPONSE_KEY);
      OnlineSyncAgent agent = AVAILABLE_AGENTS.get(classId);
      if (agent != null) {
        if (responseKey != null && responseKey.length() > 0
            && OnlineAgentManager.responseKey.equals(responseKey)) {
          if (requestAgent == null) {
            Log.w(LOG_TAG, "Unexpected SYNC response received OK from agent "
                + classId + " - no request outstanding");
          } else if (agent == requestAgent){
            if (active) return true;
            Log.w(LOG_TAG, "SYNC response received OK from agent " + classId
                + " after request was cancelled - discarded");
          }
        } else {
          Log.w(LOG_TAG, "SYNC response received from agent " + classId
              + " with invalid response key: current key x'"
              + convertStringToHex(OnlineAgentManager.responseKey) + "', received x'"
              + convertStringToHex(responseKey) + "'");
        }
      } else {
        Log.w(LOG_TAG, "SYNC response received from unknown app: " + classId);
      }
    } else {
      Log.w(LOG_TAG, "SYNC response received with no extras");
    }
    return false;
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
    requestAgent = agent;
    responseActivity = activity;
    active = true;
    responseKey = generateResponseKey();
    try {
      Intent secretsIntent = new Intent(SYNC);
      secretsIntent.setPackage(agent.getClassId());
      secretsIntent.putExtra(RESPONSE_KEY, OnlineAgentManager.responseKey);
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
   * Test for active request
   * @return true if active
   */
  public static boolean isActive() {
    return active;
  }

  /**
   * Cancel the active request
   */
  public static void cancel() {
    OnlineAgentManager.active = false;
    Intent secretsIntent = new Intent(SYNC_CANCEL);
    secretsIntent.setPackage(requestAgent.getClassId());
    responseActivity.sendBroadcast(secretsIntent, SECRETS_PERMISSION);
  }
  
  /*
   * Produce a hex string representation of a string
   */
  private String convertStringToHex(String str) {
    char[] chars = str.toCharArray();
 
    StringBuffer hex = new StringBuffer();
    for(int i = 0; i < chars.length; i++) {
      hex.append(Integer.toHexString((int)chars[i]).toUpperCase());
    }
    return hex.toString();
  }  
  
}
