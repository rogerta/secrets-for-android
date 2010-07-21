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

import java.io.File;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.KeyguardManager;
import android.app.ListActivity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.ClipboardManager;
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
  private static final int DIALOG_CONFIRM_RESTORE = 2;
  private static final int DIALOG_IMPORT_SUCCESS = 3;

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
  private boolean isEditing;  // true if changing a secret
  private int editingPosition;  // position of item being edited
  private View root;  // root of the layout for this activity
  private View edit;  // root view for the editing layout
  private File importedFile;  // File that was imported
  private boolean isConfigChange;  // being destroyed for config change?
  
  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle state) {
    Log.d(LOG_TAG, "SecretsListActivity.onCreate");
    super.onCreate(state);
    setContentView(R.layout.list);

    // If for any reason we get here and there is no secrets list, then we
    // cannot continue.  Finish the activity and return.
    if (null == LoginActivity.getSecrets()) {
      finish();
      return;
    }
    
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

    // If there is state information, use it to initialize the activity.
    if (null != state) {
      isEditing = state.getBoolean(STATE_IS_EDITING);
      if (isEditing) {
        EditText description = (EditText) findViewById(R.id.list_description);
        EditText password = (EditText) findViewById(R.id.list_password);
        EditText notes = (EditText) findViewById(R.id.list_notes);

        editingPosition = state.getInt(STATE_EDITING_POSITION);
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
        CharSequence password = secret.getPassword(false);
        if (password.length() == 0)
          password = getText(R.string.no_password);
        
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
  }

  /**
   * Check to see if the keyguard is enabled.  If so, its means the device
   * probably went to sleep due to inactivity.  If this is the case, this
   * activity is finished().
   * 
   * @return True if the activity is finished, false otherwise. 
   */
  private boolean checkKeyguard() {
    // If the keyguard has been displayed, exit this activity.  This returns
    // us to the login page requiring the user to enter his password again
    // before getting access again to his secrets.
    KeyguardManager key_guard = (KeyguardManager) getSystemService(
        KEYGUARD_SERVICE);
    boolean isInputRestricted = key_guard.inKeyguardRestrictedInputMode();
    if (isInputRestricted) {
      Log.d(LOG_TAG, "SecretsListActivity.checkKeyguard finishing");
      finish();
    }
    
    return isInputRestricted;
  }

  /** Set the title for this activity. */
  private void setTitle() {
    CharSequence title;
    int count = secretsList.getAllSecrets().size();
    if (count > 0) {
      StringBuilder builder = new StringBuilder(24);
      builder.append(count).append(' ');
      builder.append(getText(R.string.list_name));
      title = builder.toString();
    } else {
      title = getText(OS.isAndroid15() ? R.string.list_no_data
                                       : R.string.list_no_data_1_1);
    }
    
    setTitle(title);
  }

  @Override
  protected void onResume() {
    Log.d(LOG_TAG, "SecretsListActivity.onResume");
    super.onResume();
    
    // If checkKeyguard() returns true, then this activity has been finished.
    // We don't want to execute any more in this function.
    if (checkKeyguard())
      return;

    // Show instruction toast auto popup options menu if there are no secrets
    // in the list.  This check used to be done in the onCreate() method above,
    // that could occasionally cause a crash when changing layout from
    // portrait to landscape, or back.  Not sure why exactly, but I suspect
    // its becaue the UI elements are not actually ready to be rendered until
    // onResume() is called.
    if (0 == secretsList.getAllSecrets().size() && !isEditing) {
      if (OS.isAndroid15()) {
        // openOptionsMenu() crashes in Android 1.1, even though this API is
        // available.  Until I figure that out, I will call this only for
        // 1.5 and later.
        showToast(getText(R.string.list_no_data));
        getListView().post(new Runnable() {
          @Override
          public void run() {
            openOptionsMenu();
          }
        });
      } else {
        showToast(getText(R.string.list_no_data_1_1));
      }
    } else if (FileUtils.isRestoreFileTooOld()) {
      if (OS.isAndroid15()) {
        // openOptionsMenu() crashes in Android 1.1, even though this API is
        // available.  Until I figure that out, I will call this only for
        // 1.5 and later.
        getListView().post(new Runnable() {
          @Override
          public void run() {
            showToast(getText(R.string.restore_file_too_old));
            openOptionsMenu();
          }
        });
      }
    }
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
    boolean isPositionValid = position != AdapterView.INVALID_POSITION; 
      
    // We must always set the state of all the buttons, since we don't know
    // their states before this method is called.
    menu.findItem(R.id.list_add).setVisible(!isEditing);
    menu.findItem(R.id.list_edit).setVisible(!isEditing && isPositionValid);
    menu.findItem(R.id.list_save).setVisible(isEditing);
    menu.findItem(R.id.list_generate_password).setVisible(isEditing);
    menu.findItem(R.id.list_discard).setVisible(isEditing);
    menu.findItem(R.id.list_delete).setVisible(isPositionValid);
    menu.findItem(R.id.list_access).setVisible(isPositionValid);
    menu.findItem(R.id.list_backup).setVisible(!isEditing &&
        !secretsList.isEmpty());
    menu.findItem(R.id.list_restore).setVisible(!isEditing);
    menu.findItem(R.id.list_import).setVisible(!isEditing);
    menu.findItem(R.id.list_export).setVisible(!isEditing &&
        !secretsList.isEmpty());
    menu.findItem(R.id.list_copy_password_to_clipboard).setVisible(!isEditing
        && isPositionValid);
    
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
      case R.id.list_edit:
        SetEditViews(position);
        animateToEditView();
        break;
      case R.id.list_save:
        saveSecret();
        // NO BREAK
      case R.id.list_discard:
        animateFromEditView();
        break;
      case R.id.list_generate_password: {
        String pwd = generatePassword();
        EditText password = (EditText) findViewById(R.id.list_password);
        password.setText(pwd);
        break;
      }
      case R.id.list_delete:
        if (AdapterView.INVALID_POSITION != position) {
          showDialog(DIALOG_DELETE_SECRET);
        }
        break;
      case R.id.list_backup:
        backupSecrets();
        break;
      case R.id.list_restore:
        showDialog(DIALOG_CONFIRM_RESTORE);
        break;
      case R.id.list_export:
        exportSecrets();
        break;
      case R.id.list_import:
        importSecrets();
        break;
      case R.id.list_access: {
        // TODO(rogerta): maybe just stuff the index into the intent instead
        // of serializing the whole secret, it seems to be slow.
        Secret secret = secretsList.getSecret(position);
        Intent intent = new Intent(this, AccessLogActivity.class);
        intent.putExtra(EXTRA_ACCESS_LOG, secret);
        startActivity(intent);
        break;
      }
      case R.id.list_copy_password_to_clipboard: {
        Secret secret = secretsList.getSecret(position);
        ClipboardManager cm = (ClipboardManager) getSystemService(
            CLIPBOARD_SERVICE);
        cm.setText(secret.getPassword(false));
        String template = getText(R.string.copied_to_clipboard).toString();
        String msg = MessageFormat.format(template, secret.getDescription());
        showToast(msg);
        break;
      }
      default:
        break;
    }

    return handled;
  }

  /** Import from a CSV file on the SD card. */
  private void importSecrets() {
    importedFile = FileUtils.getFileToImport();
    if (null == importedFile) {
      String template = getText(R.string.import_not_found).toString();
      String msg = MessageFormat.format(template, FileUtils.getCsvFileNames());
      showToast(msg);
      return;
    }

    ArrayList<Secret> secrets = new ArrayList<Secret>();
    boolean allSucceeded = FileUtils.importSecrets(this, importedFile, secrets);

    if (!secrets.isEmpty()) {
      for (Secret secret : secrets) {
        secretsList.insert(secret);
      }

      secretsList.notifyDataSetChanged();
      setTitle();

      if (allSucceeded) {
        showDialog(DIALOG_IMPORT_SUCCESS);
      } else {
        String template = getText(R.string.import_partial).toString();
        String msg = MessageFormat.format(template, importedFile.getName());
        showToast(msg);
      }
    } else {
      String template = getText(R.string.import_failed).toString();
      String msg = MessageFormat.format(template, importedFile.getName());
      showToast(msg);
    }
  }

  private void deleteImportedFile() {
    if (null != importedFile) {
      importedFile.delete();
      importedFile = null;
    }
  }

  private void exportSecrets() {
    // Export everything to the SD card.
    if (FileUtils.exportSecrets(this, secretsList.getAllSecrets())) {
      showToast(R.string.export_succeeded);
    } else {
      showToast(R.string.export_failed);
    }
  }

  /** Restore secrets from the given restore point. */
  private void restoreSecrets(String rp) {
    // Restore everything to the SD card.
    ArrayList<Secret> secrets = FileUtils.restoreSecrets(this, rp);
    if (null != secrets) {
      LoginActivity.restoreSecrets(secrets);
      secretsList.notifyDataSetInvalidated();
      setTitle();
      showToast(R.string.restore_succeeded);
    } else {
      showToast(R.string.restore_failed);
    }
  }

  private void backupSecrets() {
    // Backup everything to the SD card.
    Cipher cipher = SecurityUtils.getEncryptionCipher();
    if (FileUtils.backupSecrets(this, cipher, secretsList.getAllSecrets())) {
      showToast(R.string.backup_succeeded);
    } else {
      showToast(R.string.error_save_secrets);
    }
  }

  /** Holds the currently chosen item in the restore dialog. */
  private class RestoreDialogState {
    public int selected = 0;
    private List<String> restorePoints; 
    
    /** Get an array of choices for the restore dialog. */
    public CharSequence[] getRestoreChoices() {
      restorePoints = FileUtils.getRestorePoints(SecretsListActivity.this);
      return restorePoints.toArray(new CharSequence[restorePoints.size()]);
    }
    
    public String getSelectedRestorePoint() {
      return restorePoints.get(selected);
    }
  }
  
  @Override
  public Dialog onCreateDialog(int id) {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_DELETE_SECRET: {
        // NOTE: the assumption at this point is that position is valid,
        // otherwise we would never get here because of the check done
        // in onOptionsItemSelected().
        DialogInterface.OnClickListener listener =
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                if (DialogInterface.BUTTON1 == which) {
                  deleteSecret(getCurrentSecretIndex());
                }
              }
            };

        // NOTE: the message part of this dialog is dynamic, so its value is
        // set in onPrepareDialog() below.  However, its important to set it
        // to something here, even the empty string, so that the setMessage()
        // call done later actually has an effect.
        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.list_menu_delete)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(EMPTY_STRING)
            .setPositiveButton(R.string.login_reset_password_pos, listener)
            .setNegativeButton(R.string.login_reset_password_neg, null)
            .create();
        break;
      }
      case DIALOG_CONFIRM_RESTORE: {
        final RestoreDialogState state = new RestoreDialogState();
        
        DialogInterface.OnClickListener itemListener =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              state.selected = which;
              dialog.dismiss();
              restoreSecrets(state.getSelectedRestorePoint());
            }
          };

        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_restore_title)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setSingleChoiceItems(state.getRestoreChoices(),
                                  state.selected,
                                  itemListener)
            .create();
        break;
      }
      case DIALOG_IMPORT_SUCCESS: {
        DialogInterface.OnClickListener listener =
          new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
              if (DialogInterface.BUTTON1 == which) {
                deleteImportedFile();
              }

              importedFile = null;
            }
          };

        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.list_menu_import)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setMessage(EMPTY_STRING)
            .setPositiveButton(R.string.login_reset_password_pos, listener)
            .setNegativeButton(R.string.login_reset_password_neg, null)
            .create();
        break;
      }

      default:
        break;
    }

    return dialog;
  }

  @Override
  protected void onPrepareDialog(int id, Dialog dialog) {
    super.onPrepareDialog(id, dialog);

    switch(id) {
      case DIALOG_DELETE_SECRET: {
        AlertDialog alert = (AlertDialog) dialog;
        int position = getCurrentSecretIndex();
        Secret secret = secretsList.getSecret(position);
        String template = getText(R.string.edit_menu_delete_secret_message).
            toString();
        String msg = MessageFormat.format(template, secret.getDescription());
        alert.setMessage(msg);
        break;
      }
      case DIALOG_IMPORT_SUCCESS: {
        AlertDialog alert = (AlertDialog) dialog;
        String template =
            getText(R.string.edit_menu_import_secrets_message).toString();
        String msg = MessageFormat.format(template, importedFile.getName());
        alert.setMessage(msg);
        break;
      }
    }
  }

  /**
   * Get the index of the current secret.  This works in list mode and in
   * edit mode.  In list mode, if no secret is selected,
   * AdpaterView.INVALID_POSITION is returned.
   */
  private int getCurrentSecretIndex() {
    return isEditing ? editingPosition
                      : getListView().getSelectedItemPosition();
  }

  /**
   * Trap the "back" button to simulate going back from the secret edit
   * view to the list view.  Note this needs to be done in key-down and not
   * key-up, since the system's default action for "back" happen on key-down.
   */
  @Override
  public boolean onKeyDown(int keyCode, KeyEvent event) {
    if (isEditing && KeyEvent.KEYCODE_BACK == keyCode) {
      saveSecret();
      animateFromEditView();
      return true;
    } else if (KeyEvent.KEYCODE_MENU == keyCode) {
    }

    return super.onKeyDown(keyCode, event);
  }

  /**
   * Called before the view is to be destroyed, so we can save state.  Its
   * important to use this method for saving state if the user happens to
   * open/close the keyboard while the view is displayed.
   */
  @Override
  protected void onSaveInstanceState(Bundle state) {
    Log.d(LOG_TAG, "SecretsListActivity.onSaveInstanceState");
    super.onSaveInstanceState(state);

    // Save our state for later.
    state.putBoolean(STATE_IS_EDITING, isEditing);

    if (isEditing) {
      saveSecret();

      EditText description = (EditText) findViewById(R.id.list_description);
      EditText username = (EditText) findViewById(R.id.list_username);
      EditText password = (EditText) findViewById(R.id.list_password);
      EditText email = (EditText) findViewById(R.id.list_email);
      EditText notes = (EditText) findViewById(R.id.list_notes);

      state.putInt(STATE_EDITING_POSITION, editingPosition);
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

    // Do the save in the background, so that we don't block the UI thread.
    // For people with lots and lots of secrets, it can take a long time to
    // save, and they may get a "force close" dialog if the save was done in
    // the UI thread.
    //
    // The issue is that we cannot give the user feedback about the save,
    // unless I use a notification (need to look into that).  Also, because
    // the process hangs around, this thread should continue running until
    // completion even if the user switches to another task/application.
    List<Secret> secrets = secretsList.getAllSecrets();
    Cipher cipher = SecurityUtils.getEncryptionCipher();
    SaveService.execute(this, secrets, cipher);
    super.onPause();
  }

  /**
   * This method is called when the activity is being destroyed and recreated
   * due to a configuration change, such as the keyboard being opened or closed.
   * Its called after onPause() and onSaveInstanceState(), but before
   * onDestroy().
   * 
   * When this is called, I will set a boolean value so that onDestroy() will
   * not clear the secrets data.
   */
  @Override
  public Object onRetainNonConfigurationInstance() {
    Log.d(LOG_TAG, "SecretsListActivity.onRetainNonConfigurationInstance");
    isConfigChange = true;
    return super.onRetainNonConfigurationInstance();
  }

  /** Called before activity is destroyed. */
  @Override
  protected void onDestroy() {
    // Don't clear the secrets if this is a configuration change, since we
    // are going to need it immediately anyway.  We do want to clear it in
    // other circumstances, otherwise the login activity will ignore attempt
    // to login again.
    if (!isConfigChange) {
      Log.d(LOG_TAG, "SecretsListActivity.onDestroy");
      LoginActivity.clearSecrets();
    }

    super.onDestroy();
  }

  /**
   * Set the secret specified by the given position in the list into the
   * edit fields used to modify the secret.  Position 0 means "add secret".
   *
   * @param position Position of secret to edit.
   */
  private void SetEditViews(int position) {
    editingPosition = position;

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
      password.setText(secret.getPassword(false));
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

    if (AdapterView.INVALID_POSITION == editingPosition) {
      if (0 == description.getText().length() &&
          0 == username.getText().length() &&
          0 == password.getText().length() &&
          0 == email.getText().length() &&
          0 == notes.getText().length())
        return;

      secret = new Secret();
    } else {
      secret = secretsList.getSecret(editingPosition);

      if (description_text.equals(secret.getDescription()) &&
          username_text.equals(secret.getUsername()) &&
          password_text.equals(secret.getPassword(false)) &&
          email_text.equals(secret.getEmail()) &&
          note_text.equals(secret.getNote()))
        return;

      secretsList.remove(editingPosition);
    }

    secret.setDescription(description.getText().toString());
    secret.setUsername(username.getText().toString());
    secret.setPassword(password.getText().toString());
    secret.setEmail(email.getText().toString());
    secret.setNote(notes.getText().toString());

    editingPosition = secretsList.insert(secret);
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
      if (isEditing) {
        // We need to clear the edit position so that when the animation is
        // done, the code does not try to make visible a secret that no longer
        // exists.  This was causing a crash (issue 16).
        editingPosition = AdapterView.INVALID_POSITION;
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
    assert(!isEditing);
    isEditing = true;

    // Cancel any toast and soft keyboard that may currently be displayed.
    if (null != toast)
      toast.cancel();
    
    OS.hideSoftKeyboard(this, getListView());

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
    assert(isEditing);
    isEditing = false;

    OS.hideSoftKeyboard(this, getListView());
    
    View list = getListView();
    int cx = root.getWidth() / 2;
    int cy = root.getHeight() / 2;
    Animation animation = new Flip3dAnimation(list, edit, cx, cy, false);
    animation.setAnimationListener(new AnimationListener() {
      @Override
      public void onAnimationEnd(Animation animation) {
        if (AdapterView.INVALID_POSITION != editingPosition) {
          ListView list_view = getListView();
          list_view.requestFocus();
          list_view.setSelection(editingPosition);

          Rect rect = new Rect();
          list_view.getFocusedRect(rect);
          list_view.requestChildRectangleOnScreen(list_view, rect, false);
        }

        setTitle();

        if (1 == secretsList.getAllSecrets().size()) {
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

  /** Generate and return a difficult to guess password. */
  private String generatePassword() {
    StringBuilder builder = new StringBuilder(8);
    try {
      SecureRandom r = SecureRandom.getInstance("SHA1PRNG");
      final String p = "ABCDEFGHIJKLMNOPQRSTUVWXYZ" +
                       "abcdefghijklmnopqrstuvwxyz" +
                       "abcdefghijklmnopqrstuvwxyz" +
                       "0123456789" +
                       "0123456789" +
                       "~!@#$%^&*()_+`-=[]{}|;':,./<>?";

      for (int i = 0; i < 8; ++i)
        builder.append(p.charAt(r.nextInt(128)));
    } catch (NoSuchAlgorithmException ex) {
      Log.e(LOG_TAG, "generatePassword", ex);
    }
    return builder.toString();
  }
}
