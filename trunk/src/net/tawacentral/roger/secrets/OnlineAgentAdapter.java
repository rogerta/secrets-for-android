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
