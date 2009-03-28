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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Maintains a user's list of secrets.  Implements all required interfaces to
 * allow the list to be shown in List and Spinner views.  Also provides support
 * for the auto complete adapters for the username and email edit views. 
 *
 * @author rogerta
 */
public class SecretsListAdapter extends BaseAdapter implements Filterable {
  // There are two secrets arrays.  secrets_ represents the array
  // use to implement the Adapter interface of this class (inherited from
  // BaseAdapter).  all_secrets_ is the real array that holds the secrets.
  //
  // Most of the time, these two members will point to the same array.
  // However, when filtering is being used, secrets_ will point to an
  // array that contains only the secrets that match the filter criteria.
  // all_secrets_ will always points to all the secrets.
  //
  // all_secrets_ is marked as final because its used as a lock for certain
  // member functions, and we don't ever want the instance to change.
  private ArrayList<Secret> secrets;
  private final ArrayList<Secret> all_secrets;
  
  // These members are used to maintain the auto complete lists for the
  // username and email fields.  I need to use the tree set because I don't
  // want to search the ArrayAdapters for existing names before inserting into
  // them.
  private TreeSet<String> usernames;
  private TreeSet<String> emails;
  private ArrayAdapter<String> username_adapter;
  private ArrayAdapter<String> email_adapter;
  
  // Cache of objects to use in the various methods.
  private Context context;
  private LayoutInflater inflater;
  private SecretsFilter filter;
  
  /**
   * Create a new secret list adapter for the UI.  The list is initially empty.
   * 
   * @param context Context of the application, used for getting resources.
   */
  SecretsListAdapter(Context context) {
    this(context, null);
  }
  
  /**
   * Create a new secret list adapter for the UI from the given list.
   * 
   * @param context Context of the application, used for getting resources.
   */
  SecretsListAdapter(Context context, ArrayList<Secret> secrets) {
    this.context = context;
    inflater = LayoutInflater.from(this.context);
    all_secrets = null != secrets ? secrets : new ArrayList<Secret>();
    this.secrets = all_secrets;
    
    // Fill in the auto complete adapters with the initial data from the
    // secrets.
    // TODO(rogerta): would probably be more efficient to use a custom
    // implementation of ListAdapter+Filterable instead of ArrayAdapter and two
    // maps, but will use this for now, since I don't expect there to be
    // hundreds of usernames or emails.
    username_adapter = new ArrayAdapter<String>(context,
        android.R.layout.simple_dropdown_item_1line);
    email_adapter = new ArrayAdapter<String>(context,
        android.R.layout.simple_dropdown_item_1line);
    usernames = new TreeSet<String>();
    emails = new TreeSet<String>();
    
    username_adapter.setNotifyOnChange(false);
    email_adapter.setNotifyOnChange(false);
    
    for (int i = 0; i < all_secrets.size(); ++i) {
      Secret secret = all_secrets.get(i);
      usernames.add(secret.getUsername());
      emails.add(secret.getEmail());
    }
    
    for (String username : usernames)
      username_adapter.add(username);
    
    for (String email : emails)
      email_adapter.add(email);
    
    username_adapter.setNotifyOnChange(true);
    email_adapter.setNotifyOnChange(true);
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
      //
      // I am using a 'simple_expandable_list_item_2' because its a view that
      // looks the way I want it: a top line of text in white letters and a
      // bottom line of text in smaller, gray letters.  The top line has id
      // 'text1', and the bottom line has id 'text2'.
      convertView = inflater.inflate(
          R.layout.list_item, parent, false);
          //android.R.layout.simple_expandable_list_item_2, parent, false);
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
    @SuppressWarnings("unchecked")
    protected FilterResults performFiltering(CharSequence prefix) {
      // NOTE: this function is *always* called from a background thread, and
      // not the UI thread.
      
      FilterResults results = new FilterResults();
      String prefix_string = null == prefix ? null
                                            : prefix.toString().toLowerCase();
      ArrayList<Secret> secrets;

      if (null != prefix_string && prefix_string.length() > 0) {
        // Do a shallow copy of the secrets list.  This works because all the
        // members that we will access here are immutable.  If this ever changes
        // then the locking strategy will need to get smarter.
        synchronized (all_secrets) {
          secrets = (ArrayList<Secret>) all_secrets.clone();
        }
  
        // We loop backwards because we may be removing elements from the array
        // in the loop, and we don't want to disturb the index when this\
        // happens.
        for (int i = secrets.size() - 1; i >= 0; --i) {
          Secret secret = secrets.get(i);
          String description = secret.getDescription().toLowerCase();
          if (!description.startsWith(prefix_string)) {
            secrets.remove(i);
          }
        }
        
        results.values = secrets;
        results.count = secrets.size();
      } else {
        // No prefix specified, so show entire list.
        synchronized (all_secrets) {
          results.values = all_secrets;
          results.count = all_secrets.size();
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
   * Get the array of secrets backing this adapter.  Its expected that callers
   * of this method will not modify the returned list.
   * 
   * Any filter applied to this adapter will not affect the secrets returned.
   * This method is meant to get all the user's secrets, and is used mainly
   * for saving the list of secrets.
   */
  public List<Secret> getAllSecrets() {
    return all_secrets;
  }
  
  /** Remove the secret at the given position. */
  public Secret remove(int position) {
    // NOTE: i will not remove usernames and emails from the auto complete
    // adapters.  For one, it would be expensive, and two, I actually think
    // this behaviour is good.  The adapters will be reset the next time the
    // list activity is restarted.
    
    // The position argument is relevant to the secrets_ array, but also need
    // to remove the corresponding element from the all_secrets_ array.  I
    // will remove from secrets_ first, then use the object found to remove
    // it from all_secrets_.  Note that this only need to be done if filtering
    // is currently enabled, i.e. when secrets_ != all_secrets_.
    Secret secret;
    synchronized (all_secrets) {
      secret = secrets.remove(position);
      if (secrets != all_secrets) {
        position = all_secrets.indexOf(secret);
        all_secrets.remove(position);
      }
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
    
    // We need to synch our access to all_secrets_ since it also accessed from
    // a background thread when doing filtering.  This is a shallow lock, in
    // the sense that access to the array elements is not synch'ed.  That's
    // OK for this purpose though.
    synchronized (all_secrets) {
      for (i = 0; i < all_secrets.size(); ++i) {
        Secret s = all_secrets.get(i);
        if (secret.getDescription().compareToIgnoreCase(s.getDescription()) < 0)
          break;
      }
      all_secrets.add(i, secret);
      
      if (secrets != all_secrets) {
        for (i = 0; i < secrets.size(); ++i) {
          Secret s = secrets.get(i);
          if (secret.getDescription().compareToIgnoreCase(s.getDescription()) < 0)
            break;
        }
        secrets.add(i, secret);
      }
    }
    
    // Add the username and email to the auto complete adapters.
    if (!usernames.contains(secret.getUsername())) {
      usernames.add(secret.getUsername());
      username_adapter.add(secret.getUsername());
    }
    
    if (!emails.contains(secret.getEmail())) {
      emails.add(secret.getEmail());
      email_adapter.add(secret.getEmail());
    }
    
    return i;
  }

  /** Gets the auto complete adapter used for completing usernames. */
  public ArrayAdapter<String> getUsernameAutoCompleteAdapter() {
    return username_adapter;
  }
  
  /** Gets the auto complete adapter used for completing emails. */
  public ArrayAdapter<String> getEmailAutoCompleteAdapter() {
    return email_adapter;
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
    String friendly_id = "";
    String username = secret.getUsername();
    Secret.LogEntry entry = secret.getMostRecentAccess();
    String last_accessed = AccessLogActivity.getElapsedString(context,
                                                              entry, 0);
    boolean hasUsername = null != username && username.length() > 0;
    if (hasUsername) {
      friendly_id = username;
    } else {
      String email = secret.getEmail();
      if (null != email && email.length() > 0)
        friendly_id = email;
    }

    if (friendly_id.length() > 0)
      friendly_id += ", ";
    
    friendly_id += last_accessed;
    return friendly_id;
  }
}
