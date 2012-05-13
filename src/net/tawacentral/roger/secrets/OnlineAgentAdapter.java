package net.tawacentral.roger.secrets;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.content.Context;
import android.widget.ArrayAdapter;

/**
 * Provides a filtered list of OnlineSecretsAgents.
 * The filter criteria are type (backup/restore/sync) and configured (yes/no)
 * 
 * @author Chris Wood
 */
public class OnlineAgentAdapter extends ArrayAdapter<OnlineSecretsAgent> {
	public final static int TYPE_BACKUP = 0;
	public final static int TYPE_RESTORE = 1;
	public final static int TYPE_SYNC = 2;
	
	private Context context;
	private boolean showAllApps; // true if all apps should be shown so that they
																// can be selected for configuring
																// otherwise only configured apps will be shown
	private int type;

	/**
	 * Creates a new adapter for the indicated mode.
	 * 
	 * @param context
	 * @param resource
	 * @param textViewResourceId
	 * @param configMode
	 *          what OSAs should be included: true = all, false = configured only
	 * @param type
	 *          dialog type
	 */
	public OnlineAgentAdapter(Context context, int resource, int textViewResourceId, boolean configMode, int type) {
		super(context, resource, textViewResourceId);
		this.context = context;
		this.showAllApps = configMode;
		this.type = type;
	}
	
	/**
   * Update the adapter's list of filtered OSAs
	 * @param secretsList the list of secrets
   */
  public void updateAppList(SecretsListAdapter secretsList) {
  	clear();
		if (showAllApps) {
			addInstalledApps();
		} else {
			switch (type) {
			case TYPE_RESTORE:
				// begin with local restore options
				addLocalRestoreOptions();
			case TYPE_BACKUP:
				// for backup & restore, show the sd card location
				add(new OnlineSecretsAgent(context.getString(R.string.sd_card), OnlineSecretsAgent.SD_CARD_CLASSID));
			case TYPE_SYNC:
				// add suitable configured OSAs to the list
				for (OnlineSecretsAgent app : filterByType(OnlineAgentManager.getConfiguredAgents(), type)) {
					add(app);
				}
				// only show the configure option if at least 1 OSA is
				// installed, and this is not a restore dialog
				if (type != TYPE_RESTORE && (filterByType(OnlineAgentManager.getInstalledAgents(), type)).size() > 0) {
					add(new OnlineSecretsAgent(context.getString(R.string.list_osa_configure), "configure"));
				}
				break;
			default:
			}
		}
  }
		
  /**
  * Adds all the suitable installed apps to the list
  */
 private void addInstalledApps() {
   for (OnlineSecretsAgent app : filterByType(OnlineAgentManager.getInstalledAgents(), type)) {
 	  add(app);
   }
 }

 /**
  * Adds all the local restore options
  */
 private void addLocalRestoreOptions() {
   for (String localRestorePoint : FileUtils.getRestorePoints(context)) {
     add(new OnlineSecretsAgent(localRestorePoint, "localrp"));
   }
 }
 
 /**
  * Filter the provided list by OSA type
  * 
  * @param list
  * @param type
  * @return new list of OSAs
  */
 private List<OnlineSecretsAgent> filterByType(Collection<OnlineSecretsAgent> list, int type) {
 	List<OnlineSecretsAgent> selectedApps = new ArrayList<OnlineSecretsAgent>(list.size());
 	for (OnlineSecretsAgent app : list) {
 		if ((type == TYPE_SYNC && app.isSync()) || (!(type == TYPE_SYNC) && !app.isSync())) {
 			selectedApps.add(app);
		  }
 	}
 	return selectedApps;
 }
 
}
