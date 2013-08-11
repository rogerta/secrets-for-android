package net.tawacentral.roger.secrets;

import java.util.Collection;

import android.content.Context;
import android.widget.ArrayAdapter;

/**
 * Provides a list of available OnlineSecretsAgents.
 *
 * @author Chris Wood
 */
public class OnlineAgentAdapter extends ArrayAdapter<OnlineSyncAgent> {

  /**
   * Creates a new adapter.
   *
   * @param context
   * @param resource
   * @param textViewResourceId
   */
  public OnlineAgentAdapter(Context context, int resource,
      int textViewResourceId) {
    super(context, resource, textViewResourceId);
  }

  /**
   * Update the adapter's list of available OSAs
   */
  public void updateAppList() {
    clear();
    addAllAgents(OnlineAgentManager.getAvailableAgents());
  }

  /*
   * This is a substitute for ArrayAdapter.addAll() which is not available below
   * API level 11.
   *
   * @param agents
   */
  private void addAllAgents(Collection<OnlineSyncAgent> agents) {
    for (OnlineSyncAgent agent : agents) {
      add(agent);
    }
  }

}
