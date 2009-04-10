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

import java.text.MessageFormat;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;

/**
 * An activity that handles two main functions: displaying the list of all
 * secrets, and modifying an existing secret.  The reason that these two
 * activities are combined into one is to take advantage of the 3d page flip
 * effect, which basically happens inside one View.
 * 
 * If the 3d transition could be done while transferring from the view of one
 * activity to the view of another, I would do that instead, since its more
 * natural for an android app.  Because of this hack, I need to override the
 * behaviour of the back button to restore the "natural feel" of the back
 * button to the user.
 *  
 * @author rogerta
 */
public class SecretsListActivity extends ListActivity {
  private static final int DIALOG_DELETE_SECRET = 1;
  private static final String EMPTY_STRING = "";
  
  public static final String EXTRA_ACCESS_LOG =
      "net.tawacentreal.secrets.accesslog";
  
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets";

  public static final String STATE_IS_EDITING = "is_editing";
  public static final String STATE_EDITING_POSITION = "editing_position";
  public static final String STATE_EDITING_DESCRIPTION = "editing_description";
  public static final String STATE_EDITING_USERNAME = "editing_username";
  public static final String STATE_EDITING_PASSWORD = "editing_password";
  public static final String STATE_EDITING_EMAIL = "editing_email";
  public static final String STATE_EDITING_NOTES = "editing_notes";
  
  private SecretsListAdapter secretsList;  // list of secrets
  private Toast toast;  // toast used to show password
  private boolean is_editing;  // true if changing a secret
  private int editing_position;  // position of item being edited
  private View root;  // root of the layout for this activity
  private View edit;  // root view for the editing layout
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    setContentView(R.layout.list);
    
    secretsList = new SecretsListAdapter(this, LoginActivity.getSecrets());
    setTitle();
     
    setListAdapter(secretsList);
    getListView().setTextFilterEnabled(true);

    // Setup the auto complete adapters for the username and email views.
    AutoCompleteTextView username = (AutoCompleteTextView)
        findViewById(R.id.list_username);
    AutoCompleteTextView email = (AutoCompleteTextView)
        findViewById(R.id.list_email);
    
    username.setAdapter(secretsList.getUsernameAutoCompleteAdapter());
    email.setAdapter(secretsList.getEmailAutoCompleteAdapter());
    
    // The 3d flip animation will be done on the root view of this activity.
    // Also get the edit group of views for use as the second view in the
    // animation.
    root = findViewById(R.id.list_container);
    edit = findViewById(R.id.edit_layout);
    
    // Show instruction toast if only one secret in the list.
    // TODO(rogerta): this is annoying when filtering is enabled in the list
    // view. May need to use a preference to show this only once.
    if (0 == secretsList.getCount()) {
      showToast(getText(R.string.list_no_data));
    }
    
    // If there is state information, use it to initialize the activity.
    if (null != state) {
      is_editing = state.getBoolean(STATE_IS_EDITING);
      if (is_editing) {
        EditText description = (EditText) findViewById(R.id.list_description);
        EditText password = (EditText) findViewById(R.id.list_password);
        EditText notes = (EditText) findViewById(R.id.list_notes);

        editing_position = state.getInt(STATE_EDITING_POSITION);
        description.setText(state.getCharSequence(STATE_EDITING_DESCRIPTION));
        username.setText(state.getCharSequence(STATE_EDITING_USERNAME));
        password.setText(state.getCharSequence(STATE_EDITING_PASSWORD));
        email.setText(state.getCharSequence(STATE_EDITING_EMAIL));
        notes.setText(state.getCharSequence(STATE_EDITING_NOTES));
        
        getListView().setVisibility(View.GONE);
        edit.setVisibility(View.VISIBLE);
      }
    }
    
    // Hook up interactions.
    getListView().setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position,
                              long id) {
        Secret secret = getSecret(position);
        String password = secret.getPassword();
        showToast(password);
        // TODO(rogerta): to reliably record "view" access, we would want to
        // checkpoint the secrets and save them here.  But doing so causes
        // unacceptable delays is displaying the toast.
        //FileUtils.saveSecrets(SecretsListActivity.this, 
        //                      secretsList_.getAllSecrets());
      }
    });
    
    getListView().setOnItemLongClickListener(new OnItemLongClickListener() {
      @Override
      public boolean onItemLongClick(AdapterView<?> parent, View view,
                                     int position, long id) {
        SetEditViews(position);
        animateToEditView();
        return true;
      }
    });
    
    Log.d(LOG_TAG, "SecretsListActivity.onResume");
  }

  private void checkKeyguard() {
    // If the keyguard has been displayed, exit this activity.  This returns
    // us to the login page requiring the user to enter his password again
    // before get access again to his secrets.
    KeyguardManager key_guard = (KeyguardManager) getSystemService(
        KEYGUARD_SERVICE);
    boolean isInputRestricted = key_guard.inKeyguardRestrictedInputMode();
    if (isInputRestricted) {
      Log.d(LOG_TAG, "SecretsListActivity.checkKeyguard finishing");
      finish();
    }
  }

  /** Set the title for this activity. */ 
  private void setTitle() {
    StringBuilder builder = new StringBuilder(24);
    int count = secretsList.getCount();
    if (count > 0)
      builder.append(count).append(' ');
    
    builder.append(getText(R.string.list_name));
    setTitle(builder.toString());
  }

  @Override
  protected void onResume() {
    super.onResume();
    checkKeyguard();
    Log.d(LOG_TAG, "SecretsListActivity.onResume");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.list_menu, menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    // Collect information needed to decide the state of the menu buttons.
    int position = getCurrentSecretIndex();
    
    // We must always set the state of all the buttons, since we don't know
    // their states before this method is called.
    menu.findItem(R.id.list_add).setVisible(!is_editing);
    menu.findItem(R.id.list_save).setVisible(is_editing);
    menu.findItem(R.id.list_discard).setVisible(is_editing);
    menu.findItem(R.id.list_delete).setVisible(
        position != AdapterView.INVALID_POSITION);
    menu.findItem(R.id.list_access).setVisible(
        position != AdapterView.INVALID_POSITION);
    
    // The menu should be shown if we are editing, or if we must show the
    // delete/access menu items.
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    boolean handled = false;
    int position = getCurrentSecretIndex();

    // TODO(rogerta): when using this menu to finish the editing activity, for
    // some reason the selected item in the list view is not highlighted.  Need
    // to figure out what the interaction with the menu is.  This does not
    // happen when using the back button to finish the editing activity.
    switch (item.getItemId()) {
      case R.id.list_add:
        SetEditViews(AdapterView.INVALID_POSITION);
        animateToEditView();
        break;
      case R.id.list_save:
        saveSecret();
        // NO BREAK
      case R.id.list_discard:
        animateFromEditView();
        break;
      case R.id.list_delete:
        if (AdapterView.INVALID_POSITION != position) {
          showDialog(DIALOG_DELETE_SECRET);
        }
        break;
      case R.id.list_access:
        // TODO(rogerta): maybe just stuff the index into the intent instead
        // of serializing the whole secret, it seems to be slow.
        Secret secret = secretsList.getSecret(position);
        Intent intent = new Intent(this, AccessLogActivity.class);
        intent.putExtra(EXTRA_ACCESS_LOG, secret);
        startActivity(intent);
      default:
        break;
    }
    
    return handled;
  }

  @Override
  public Dialog onCreateDialog(int id) {
    Dialog dialog = null;
    
    switch (id) {
      case DIALOG_DELETE_SECRET: {
        // NOTE: the assumption at this point is that position is valid,
        // otherwise we would never get here because of the check done
        // in onOptionsItemSelected().
        final int position = getCurrentSecretIndex();
        Secret secret = secretsList.getSecret(position);
        
        DialogInterface.OnClickListener listener =
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                if (DialogInterface.BUTTON1 == which) {
                  deleteSecret(position);
                }
              }
            };

        String template = getText(R.string.edit_menu_delete_secret_message).
            toString();
        String msg = MessageFormat.format(template, secret.getDescription());
        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.list_menu_delete)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(msg)
            .setPositiveButton(R.string.login_reset_password_pos, listener)
            .setNegativeButton(R.string.login_reset_password_neg, null)
            .create();
        }
        break;
      default:
        break;
    }
    
    return dialog;
  }

  /**
   * Get the index of the current secret.  This works in list mode and in
   * edit mode.  In list mode, if no secret is selected,
   * AdpaterView.INVALID_POSITION is returned. 
   */
  private int getCurrentSecretIndex() {
    return is_editing ? editing_position
                      : getListView().getSelectedItemPosition();
  }
  
  /**
   * Trap the "back" button to simulate going back from the secret edit
   * view to the list view.  Note this needs to be done in key-down and not
   * key-up, since the system's default action for "back" happen on key-down.
   */
  @Override
  public boolean onKeyDown(int key_code, KeyEvent event) {
    if (is_editing && KeyEvent.KEYCODE_BACK == key_code) {
      saveSecret();
      animateFromEditView();
      return true;
    }
    
    return super.onKeyDown(key_code, event);
  }

  /**
   * Called before the view is to be destroyed, so we can save state.  Its
   * important to use this method for saving state if the user happens to
   * open/close the keyboard while the view is displayed.
   */
  @Override
  protected void onSaveInstanceState(Bundle state) {
    super.onSaveInstanceState(state);
    
    // Save our state for later.
    state.putBoolean(STATE_IS_EDITING, is_editing);
    
    if (is_editing) {
      saveSecret();
      
      EditText description = (EditText) findViewById(R.id.list_description);
      EditText username = (EditText) findViewById(R.id.list_username);
      EditText password = (EditText) findViewById(R.id.list_password);
      EditText email = (EditText) findViewById(R.id.list_email);
      EditText notes = (EditText) findViewById(R.id.list_notes);

      state.putInt(STATE_EDITING_POSITION, editing_position);
      state.putCharSequence(STATE_EDITING_DESCRIPTION, description.getText());
      state.putCharSequence(STATE_EDITING_USERNAME, username.getText());
      state.putCharSequence(STATE_EDITING_PASSWORD, password.getText());
      state.putCharSequence(STATE_EDITING_EMAIL, email.getText());
      state.putCharSequence(STATE_EDITING_NOTES, notes.getText());
    }
    
    Log.d(LOG_TAG, "SecretsListActivity.onSaveInstanceState");
  }

  /** Called when the activity is no longer visible. */
  @Override
  protected void onPause() {
    Log.d(LOG_TAG, "SecretsListActivity.onPause");
    
    // Cancel any toast that may currently be displayed.
    if (null != toast)
      toast.cancel();
    
    // Save everything now.  Saves are only done when the activity is paused
    // to keep the save time from affecting the user too much.  I'll if this
    // is a good idea or not, or if there are common scenarios where we might
    // not pass through this code.
    if (!FileUtils.saveSecrets(this, secretsList.getAllSecrets()))
      showToast(R.string.error_save_secrets);
    
    super.onPause();
  }

  /**
   * Set the secret specified by the given position in the list into the
   * edit fields used to modify the secret.  Position 0 means "add secret".
   * 
   * @param position Position of secret to edit.
   */
  private void SetEditViews(int position) {
    editing_position = position;
    
    EditText description = (EditText) findViewById(R.id.list_description);
    EditText username = (EditText) findViewById(R.id.list_username);
    EditText password = (EditText) findViewById(R.id.list_password);
    EditText email = (EditText) findViewById(R.id.list_email);
    EditText notes = (EditText) findViewById(R.id.list_notes);
    
    if (AdapterView.INVALID_POSITION == position) {
      description.setText(EMPTY_STRING);
      username.setText(EMPTY_STRING);
      password.setText(EMPTY_STRING);
      email.setText(EMPTY_STRING);
      notes.setText(EMPTY_STRING);
      
      description.requestFocus();
    } else {
      Secret secret = secretsList.getSecret(position);
      
      description.setText(secret.getDescription());
      username.setText(secret.getUsername());
      password.setText(secret.getPassword());
      email.setText(secret.getEmail());
      notes.setText(secret.getNote());
      
      password.requestFocus();
    }
    
    ScrollView scroll = (ScrollView) findViewById(R.id.edit_layout);
    scroll.scrollTo(0, 0);
  }

  /**
   * Save the current values in the edit views into the current secret being
   * edited.  If the current secret is at position 0, this means add a new
   * secret.
   * 
   * Secrets will be added in alphabetical order by description. 
   *
   * All secrets are flushed to persistent storage.
   */
  private void saveSecret() {
    EditText description = (EditText) findViewById(R.id.list_description);
    EditText username = (EditText) findViewById(R.id.list_username);
    EditText password = (EditText) findViewById(R.id.list_password);
    EditText email = (EditText) findViewById(R.id.list_email);
    EditText notes = (EditText) findViewById(R.id.list_notes);

    // If all the text views are blank, then don't do anything if we are
    // supposed to be adding a secret.  Also, if all the views are
    // the same as the current secret, don't do anything either.
    Secret secret;
    String description_text = description.getText().toString(); 
    String username_text = username.getText().toString();
    String password_text = password.getText().toString();
    String email_text = email.getText().toString();
    String note_text = notes.getText().toString();
    
    if (AdapterView.INVALID_POSITION == editing_position) {
      if (0 == description.getText().length() &&
          0 == username.getText().length() &&
          0 == password.getText().length() &&
          0 == email.getText().length() &&
          0 == notes.getText().length())
        return;
      
      secret = new Secret();
    } else {
      secret = secretsList.getSecret(editing_position);
      
      if (description_text.equals(secret.getDescription()) &&
          username_text.equals(secret.getUsername()) &&
          password_text.equals(secret.getPassword()) &&
          email_text.equals(secret.getEmail()) &&
          note_text.equals(secret.getNote()))
        return;
      
      secretsList.remove(editing_position);
    }
    
    secret.setDescription(description.getText().toString());
    secret.setUsername(username.getText().toString());
    secret.setPassword(password.getText().toString());
    secret.setEmail(email.getText().toString());
    secret.setNote(notes.getText().toString());
    
    editing_position = secretsList.insert(secret);
    secretsList.notifyDataSetChanged();
  }

  /**
   * Delete the secret at the given position. If the user is currently editing
   * a secret, he is returned to the list. */
  public void deleteSecret(int position) {
    if (AdapterView.INVALID_POSITION != position) {
      secretsList.remove(position);
      secretsList.notifyDataSetChanged();

      // TODO(rogerta): is this is really a performance issue to save here?
      //if (!FileUtils.saveSecrets(this, secretsList_.getAllSecrets()))
      //  showToast(R.string.error_save_secrets);
      if (is_editing) {
        animateFromEditView();
      } else {
        setTitle();
      }
    }
  }
  
  /**
   * Show a toast on the screen with the given message.  If a toast is already
   * being displayed, the message is replaced and timer is restarted.
   * 
   * @param message Resource id of the text to display in the toast.
   */
  private void showToast(int message) {
    showToast(getText(message));
  }
  
  /**
   * Show a toast on the screen with the given message.  If a toast is already
   * being displayed, the message is replaced and timer is restarted.
   * 
   * @param message Text to display in the toast.
   */
  private void showToast(CharSequence message) {
    if (null == toast) {
      toast = Toast.makeText(SecretsListActivity.this, message,
          Toast.LENGTH_LONG);
      toast.setGravity(Gravity.CENTER, 0, 0);
    } else {
      toast.setText(message);
    }
    
    toast.show();
  }

  /** Get the secret at the specified position in the list. */
  private Secret getSecret(int position) {
    return (Secret) getListAdapter().getItem(position);
  }
  
  /**
   * Start the view animation that transitions from the list of secrets to
   * the secret edit view.
   */
  private void animateToEditView() {
    assert(!is_editing);
    is_editing = true;

    // Cancel any toast that may currently be displayed.
    if (null != toast)
      toast.cancel();
    
    View list = getListView();
    int cx = root.getWidth() / 2;
    int cy = root.getHeight() / 2;
    Animation animation = new Flip3dAnimation(list, edit, cx, cy, true);
    animation.setAnimationListener(new AnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        if (0 == secretsList.getCount()) {
          showToast(getText(R.string.edit_instructions));
        }
      }
      @Override
      public void onAnimationRepeat(Animation animation) {
      }
      @Override
      public void onAnimationStart(Animation animation) {
      }
    });
    
    root.startAnimation(animation);
  }
  
  /**
   * Start the view animation that transitions from the secret edit view to
   * the list of secrets.
   */
  private void animateFromEditView() {
    assert(is_editing);
    is_editing = false;
    
    View list = getListView();
    int cx = root.getWidth() / 2;
    int cy = root.getHeight() / 2;
    Animation animation = new Flip3dAnimation(list, edit, cx, cy, false);
    animation.setAnimationListener(new AnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        if (AdapterView.INVALID_POSITION != editing_position) {
          ListView list_view = getListView();
          list_view.requestFocus();
          list_view.setSelection(editing_position);
          
          Rect rect = new Rect();
          list_view.getFocusedRect(rect);
          list_view.requestChildRectangleOnScreen(list_view, rect, false);
        }
        
        setTitle();
        
        if (1 == secretsList.getCount()) {
          showToast(getText(R.string.list_instructions));
        }
      }
      @Override
      public void onAnimationRepeat(Animation animation) {
      }
      @Override
      public void onAnimationStart(Animation animation) {
      }
    });
    root.startAnimation(animation);
  }
}
