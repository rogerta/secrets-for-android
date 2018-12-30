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

import java.util.ArrayList;
import java.util.TreeSet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

/**
 * Maintains a user's list of secrets.  Implements all required interfaces to
 * allow the list to be shown in List and Spinner views.  Also provides support
 * for the auto complete adapters for the username and email edit views.
 *
 * @author rogerta
 */
@SuppressWarnings("javadoc")
public class SecretsListAdapter extends BaseAdapter implements Filterable {
  public static final char DOT = '.';

  // There are two secrets arrays.  secrets represents the array
  // use to implement the Adapter interface of this class (inherited from
  // BaseAdapter).  allSecrets is the real array that holds the secrets.
  //
  // Most of the time, these two members will point to the same array.
  // However, when filtering is being used, secrets will point to an
  // array that contains only the secrets that match the filter criteria.
  // allSecrets will always points to all the secrets.
  //
  // allSecrets is marked as final because its used as a lock for certain
  // member functions, and we don't ever want the instance to change.
  private ArrayList<Secret> secrets;
  private final ArrayList<Secret> allSecrets;
  private final ArrayList<Secret> deletedSecrets;

  // These members are used to maintain the auto complete lists for the
  // username and email fields.  I need to use the tree set because I don't
  // want to search the ArrayAdapters for existing names before inserting into
  // them.
  private TreeSet<String> usernames;
  private TreeSet<String> emails;
  private ArrayAdapter<String> usernameAdapter;
  private ArrayAdapter<String> emailAdapter;

  // Cache of objects to use in the various methods.
  private SecretsListActivity activity;
  private LayoutInflater inflater;
  private SecretsFilter filter;

  /**
   * Create a new secret list adapter for the UI from the given list.
   *
   * @param activity Context of the application, used for getting resources.
   * @param secrets The list of user secrets.  This list cannot be null.
   * @param deletedSecrets The list of deleted user secrets, cannot be null.
   */
  SecretsListAdapter(SecretsListActivity activity, ArrayList<Secret> secrets,
      ArrayList<Secret> deletedSecrets) {
    this.activity = activity;
    inflater = LayoutInflater.from(this.activity);
    allSecrets = secrets;
    this.secrets = allSecrets;
    this.deletedSecrets = deletedSecrets;

    // Fill in the auto complete adapters with the initial data from the
    // secrets.
    // TODO(rogerta): would probably be more efficient to use a custom
    // implementation of ListAdapter+Filterable instead of ArrayAdapter and two
    // maps, but will use this for now, since I don't expect there to be
    // hundreds of usernames or emails.
    usernameAdapter = new ArrayAdapter<String>(activity,
        android.R.layout.simple_dropdown_item_1line);
    emailAdapter = new ArrayAdapter<String>(activity,
        android.R.layout.simple_dropdown_item_1line);
    usernames = new TreeSet<String>();
    emails = new TreeSet<String>();

    usernameAdapter.setNotifyOnChange(false);
    emailAdapter.setNotifyOnChange(false);

    for (int i = 0; i < allSecrets.size(); ++i) {
      Secret secret = allSecrets.get(i);
      usernames.add(secret.getUsername());
      emails.add(secret.getEmail());
    }

    for (String username : usernames)
      usernameAdapter.add(username);

    for (String email : emails)
      emailAdapter.add(email);

    usernameAdapter.setNotifyOnChange(true);
    emailAdapter.setNotifyOnChange(true);
  }

  @Override
  public boolean areAllItemsEnabled() {
    return true;
  }

  @Override
  public boolean isEnabled(int position) {
    // This will probably never get called since areAllItemsEnabled() returns
    // true, but I'll leave it here anyway.
    return true;
  }

  @Override
  public int getCount() {
    return secrets.size();
  }

  @Override
  public Object getItem(int position) {
    return getSecret(position);
  }

  @Override
  public long getItemId(int position) {
    // TODO(rogerta): should probably try to come up with a stable Id, but for
    // now not important.  I suspect this might be an issue for very large data
    // sets.  Hopefully a user does not have that many secrets...
    return position;
  }

  @Override
  public int getItemViewType(int position) {
    // Must be a value from 0 to getViewTypeCount()-1.
    return 0;
  }

  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    // if convertView is non-null, then I will assume its the right type.
    if (null == convertView) {
      // Using inflate(int,View) just crashed.  Not sure why.  It seems that
      // the inflated item view cannot be a child of the given parent.  This
      // overload of inflate() will use the parent argument to get certain
      // information to inflate the two line view, but will not make it a
      // child of parent because of the 'false'.
      convertView = inflater.inflate(
          R.layout.list_item, parent, false);
    }

    // Now setup the view based on the current secret.
    Secret secret = secrets.get(position);

    TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
    TextView text2 = (TextView) convertView.findViewById(android.R.id.text2);

    text1.setText(secret.getDescription());
    text2.setText(getFriendlyId(secret));

    return convertView;
  }

  @Override
  public int getViewTypeCount() {
    return 1;
  }

  @Override
  public boolean hasStableIds() {
    return false;
  }

  @Override
  public boolean isEmpty() {
    return 0 == secrets.size();
  }

  @Override
  public Filter getFilter() {
    if (null == filter)
      filter  = new SecretsFilter();

    return filter;
  }

  /**
   * Concrete subclass of Filter to allow filtering the list of secrets by
   * typing the first letters of the secret's description.
   *
   * @author rogerta
   *
   */
  private class SecretsFilter extends Filter {
    @Override
    // TODO(rogerta): the clone() method does not support generics.
    protected FilterResults performFiltering(CharSequence prefix) {
      // NOTE: this function is *always* called from a background thread, and
      // not the UI thread.

      boolean isFullTextSearch = false;
      FilterResults results = new FilterResults();
      String prefixString = null == prefix ? null
                                           : prefix.toString().toLowerCase();
      ArrayList<Secret> secrets;

      // if the prefix starts with a dot, then this is interpreted as a full
      // text search.  This means that a given secret matches the "prefix",
      // not including the dot, if the prefix appears in any part of any field
      // of the secret.  If the prefix does not start with a dot, this is a
      // normal prefix search of the description.  If the prefix starts with
      // two dots, this is considered a prefix search with a single dot.
      //
      // Examples:
      //
      //  prefix="abc"   -> prefix search with "abc"
      //  prefix=".abc"  -> full text search with "abc"
      //  prefix="..abc" -> prefix search with ".abc"
      if (null != prefixString) {
        if (prefixString.length() > 0 && prefixString.charAt(0) == DOT) {
          isFullTextSearch = prefixString.length() > 1 &&
              prefixString.charAt(1) != DOT;
          prefixString = prefixString.substring(1);
        }
      }

      if (null != prefixString && prefixString.length() > 0) {
        // Do a shallow copy of the secrets list.  This works because all the
        // members that we will access here are immutable.  If this ever changes
        // then the locking strategy will need to get smarter.
        synchronized (allSecrets) {
          secrets = new ArrayList<Secret>(allSecrets);
        }

        // We loop backwards because we may be removing elements from the array
        // in the loop, and we don't want to disturb the index when this
        // happens.
        for (int i = secrets.size() - 1; i >= 0; --i) {
          Secret secret = secrets.get(i);
          if (isFullTextSearch) {
            if (!secret.getDescription().toLowerCase().contains(prefixString) &&
                !secret.getEmail().toLowerCase().contains(prefixString) &&
                !secret.getUsername().toLowerCase().contains(prefixString) &&
                !secret.getNote().toLowerCase().contains(prefixString))
              secrets.remove(i);
          } else {
            String description = secret.getDescription().toLowerCase();
            if (!description.startsWith(prefixString)) {
              secrets.remove(i);
            }
          }
        }

        results.values = secrets;
        results.count = secrets.size();
      } else {
        // No prefix specified, so show entire list.
        synchronized (allSecrets) {
          results.values = allSecrets;
          results.count = allSecrets.size();
        }
      }

      return results;
    }

    @SuppressWarnings("unchecked")
    @Override
    protected void publishResults(CharSequence prefix,
                                  FilterResults results) {
      // NOTE: this function is *always* called from the UI thread.
      secrets = (ArrayList<Secret>) results.values;
      notifyDataSetChanged();
      activity.setTitle();
    }
  }

  /**
   * Totally for debugging.  Should be taken out before releasing.
   *
   * @param view View to walk.
   * @return Id of view.
   */
  /*private int walkViewTree(View view) {
    int id = view.getId();

    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      int count = group.getChildCount();
      for (int i = 0; i < count; i++) {
        View child = group.getChildAt(i);
        walkViewTree(child);
      }
    }

    return id;
  }*/

  /** Get the secret at the given position in the list. */
  public Secret getSecret(int position) {
    return secrets.get(position);
  }

  /**
   * Get the secrets backing this adapter.  It's expected that callers
   * of this method will not modify the returned list.
   *
   * Any filter applied to this adapter will not affect the secrets returned.
   * This method is meant to get all the user's secrets, and is used mainly
   * for saving the list of secrets.
   */
  public ArrayList<Secret> getAllSecrets() {
    return allSecrets;
  }
  
  /**
   * Return a collection of all secrets including deleted ones.
   * This is a merge of the two collections (all secrets and deleted secrets)
   * unless either is empty, then just the non-empty collection is returned.
   * The caller should not modify the returned collection.
   * @return secrets collection
   */
   public ArrayList<Secret> getAllAndDeletedSecrets() {
      if (deletedSecrets.size() == 0) {
         return allSecrets;
      }
      if (allSecrets.size() == 0) {
         return deletedSecrets;
      }
      ArrayList<Secret> allAndDeletedSecrets = new ArrayList<Secret>();
      synchronized (allSecrets) {
         // merge the two collections. Both collections are assumed sorted and
         // secrets exist uniquely in only one collection.
         int aIndex = 0, dIndex = 0;
         while (aIndex < allSecrets.size() || dIndex < deletedSecrets.size()) {
            if (aIndex == allSecrets.size()) {
               allAndDeletedSecrets.add(deletedSecrets.get(dIndex++));
            } else if (dIndex == deletedSecrets.size()) {
               allAndDeletedSecrets.add(allSecrets.get(aIndex++));
            } else {
               if (allSecrets.get(aIndex).compareTo(deletedSecrets.get(dIndex)) < 0) {
                  allAndDeletedSecrets.add(allSecrets.get(aIndex++));
               } else {
                  allAndDeletedSecrets.add(deletedSecrets.get(dIndex++));
               }
            }
         }
      }
      return allAndDeletedSecrets;
   }

  /** Remove the secret at the given position. It is not deleted. */
  public Secret remove(int position) {
    // NOTE: i will not remove usernames and emails from the auto complete
    // adapters.  For one, it would be expensive, and two, I actually think
    // this behaviour is good.  The adapters will be reset the next time the
    // list activity is restarted.

    // The position argument is relevant to the secrets array, but also need
    // to remove the corresponding element from the allSecrets array.  I
    // will remove from secrets first, then use the object found to remove
    // it from allSecrets.  Note that this only need to be done if filtering
    // is currently enabled, i.e. when secrets != allSecrets.
    Secret secret;
    synchronized (allSecrets) {
      secret = secrets.remove(position);
      if (secrets != allSecrets) {
        position = allSecrets.indexOf(secret);
        allSecrets.remove(position);
      }
    }

    return secret;
  }
  
  /** Remove the secret at the given position and then delete the secret
   *  from the secrets collection */
  public Secret delete(int position) {
    int i;
    Secret secret;
    synchronized (allSecrets) {
      secret = remove(position);
      // add the deleted secret to the deleted secrets list, removing it
      // first if it has been deleted previously.
      for (i = 0; i < deletedSecrets.size(); ++i) {
        Secret s = deletedSecrets.get(i);
        int compare = secret.compareTo(s);
        if (compare < 0) break;
        else if (compare == 0) {
           deletedSecrets.remove(i);
           break;
        }
      }
      deletedSecrets.add(i, secret);
      secret.setDeleted();
    }

    return secret;
  }

  /**
   * Insert the secret into the list.  The secret is inserted in alphabetical
   * order as determined by the description.
   *
   * @param secret Secret to insert.
   * @return Position where the secret was inserted.
   */
  public int insert(Secret secret) {
    int i;

    // We need to synch our access to allSecrets since it also accessed from
    // a background thread when doing filtering.  This is a shallow lock, in
    // the sense that access to the array elements is not synch'ed.  That's
    // OK for this purpose though.
    synchronized (allSecrets) {
      for (i = 0; i < allSecrets.size(); ++i) {
        Secret s = allSecrets.get(i);
        if (secret.compareTo(s) < 0)
          break;
      }
      allSecrets.add(i, secret);

      if (secrets != allSecrets) {
        for (i = 0; i < secrets.size(); ++i) {
          Secret s = secrets.get(i);
          if (secret.compareTo(s) < 0)
            break;
        }
        secrets.add(i, secret);
      }
      
      // in case a secret of the same name has been previously removed
      deletedSecrets.remove(secret);
    }

    // Add the username and email to the auto complete adapters.
    if (!usernames.contains(secret.getUsername())) {
      usernames.add(secret.getUsername());
      usernameAdapter.add(secret.getUsername());
    }

    if (!emails.contains(secret.getEmail())) {
      emails.add(secret.getEmail());
      emailAdapter.add(secret.getEmail());
    }

    return i;
  }
  
  /**
   * Used by sync agent for updating secrets list(s).
   * 
   * Adds, updates or deletes secrets 
   * 
   * @param changedSecrets secrets that are new or updated
   */
  public void syncSecrets(ArrayList<Secret> changedSecrets) {
    if (changedSecrets != null) {
      synchronized (allSecrets) {
        OnlineAgentManager.syncSecrets(allSecrets, changedSecrets);
        deletedSecrets.clear();
      }
      notifyDataSetChanged();
    }
  }

  /** Gets the auto complete adapter used for completing usernames. */
  public ArrayAdapter<String> getUsernameAutoCompleteAdapter() {
    return usernameAdapter;
  }

  /** Gets the auto complete adapter used for completing emails. */
  public ArrayAdapter<String> getEmailAutoCompleteAdapter() {
    return emailAdapter;
  }

  /**
   * Gets the friendly id of this secret.  This is a combination of the
   * username and email address.
   *
   * The friendly id will be cached in the secret object for future use.
   * Any change to the username or email address will automatically invalidate
   * the friend id and cause it to be recalculated here.
   *
   * @param secret Secret whose friendly name we want to generate.
   * @return Friendly name for the secret.
   */
  public String getFriendlyId(Secret secret) {
    // Create a friendly name based on the information we have.  Note that
    // if the username or email of the secret is changed, the cache about
    // will be cleared, so we'll come through here again.
    String friendlyId = "";
    String username = secret.getUsername();
    Secret.LogEntry entry = secret.getMostRecentAccess();
    String lastAccessed = AccessLogActivity.getElapsedString(activity,
                                                              entry, 0);
    boolean hasUsername = null != username && username.length() > 0;
    if (hasUsername) {
      friendlyId = username;
    } else {
      String email = secret.getEmail();
      if (null != email && email.length() > 0)
        friendlyId = email;
    }

    if (friendlyId.length() > 0)
      friendlyId += ", ";

    friendlyId += lastAccessed;
    return friendlyId;
  }
}
