package net.tawacentral.roger.secrets;

import android.content.Context;
import android.widget.ArrayAdapter;

/**
 * Provides a filtered list of OnlineSecretsAgents.
 * The filter criterion is configured (yes/no)
 * 
 * @author Chris Wood
 */
public class OnlineAgentAdapter extends ArrayAdapter<OnlineSyncAgent> {
	
	private Context context;
	private boolean showAllApps; // true if all apps should be shown so that
																// they can be selected for configuring
																// otherwise only configured apps will be shown

	/**
	 * Creates a new adapter for the indicated mode.
	 * 
	 * @param context
	 * @param resource
	 * @param textViewResourceId
	 * @param configMode
	 *          what OSAs should be included: true = all, false = configured only
	 */
  public OnlineAgentAdapter(Context context, int resource,
          int textViewResourceId, boolean configMode) {
    super(context, resource, textViewResourceId);
    this.context = context;
    this.showAllApps = configMode;
  }
	
	/**
   * Update the adapter's list of filtered OSAs
	 * @param secretsList the list of secrets
   */
  public void updateAppList(SecretsListAdapter secretsList) {
    clear();
    if (showAllApps) {
      for (OnlineSyncAgent app : OnlineAgentManager.getInstalledAgents()) {
        add(app);
       }
    } else {
      // add suitable configured OSAs to the list
      for (OnlineSyncAgent app : OnlineAgentManager.getConfiguredAgents()) {
        add(app);
      }
      // only show the configure option if at least 1 OSA is installed.
      if (OnlineAgentManager.getInstalledAgents().size() > 0) {
        add(new OnlineSyncAgent(
                context.getString(R.string.list_osa_configure), "configure"));
      }
    }
  }
 
}
