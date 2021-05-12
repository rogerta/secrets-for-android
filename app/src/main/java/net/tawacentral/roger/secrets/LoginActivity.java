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

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;

import javax.crypto.Cipher;

/**
 * This activity handles logging into the application.  It prompts the user for
 * his password, or guides him through the process of creating one.  This
 * activity also permits the user to reset his password, which essentially
 * means deletes all his secrets.
 *
 * @author rogerta
 */
public class LoginActivity extends Activity implements TextWatcher {
  /** Dialog Id for resetting password. */
  private static final int DIALOG_RESET_PASSWORD = 1;
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "LoginActivity";

  /**
   * This is the global list of the user's secrets.  This list is accessed
   * from other parts of the program. */
  private static ArrayList<Secret> secrets;

  /**
   * Secrets that are deleted are held in the deletedSecrets list. This
   * is primarily to support synchronizing deletions across devices.
   * Saved secrets include deletions (with a delete indicator), and are
   * separated from the normal secrets list on loading.  */
  private static ArrayList<Secret> deletedSecrets;

  private boolean isFirstRun;
  private boolean isValidatingPassword;
  private String passwordString;
  private Toast toast;

  /** Called when the activity is first created. */
  @Override
  public void onCreate(Bundle savedInstanceState) {
    Log.d(LOG_TAG, "LoginActivity.onCreate");
    super.onCreate(savedInstanceState);
    setContentView(R.layout.login);

    // Setup the behaviour of the password edit view.
    EditText password = (EditText)findViewById(R.id.login_password);
    password.setOnKeyListener(new View.OnKeyListener() {
      @Override
      public boolean onKey(View v, int keyCode, KeyEvent event) {
        // In the Android 1.1 platform, I used to use a click listener, since
        // pressing the Enter key would generate that event.  However, in
        // Android 1.5 (cupcake) that no longer works.  Therefore I trap the
        // Enter key here with a key listener, and try handling the password
        // when the Enter key is released (action up).  Note that in 1.1 though,
        // if I completely ignore the Enter key action down, I get a popup menu
        // with a paste command.  To get around this, I always return true if
        // the Enter key is pressed or released, but I only try to handle the
        // password on release.  This works for both 1.1 and 1.5.  The Enter
        // does not become part of the password.
        if (KeyEvent.KEYCODE_ENTER == keyCode) {
          if (KeyEvent.ACTION_UP == event.getAction())
            handlePasswordClick((TextView) v);

          return true;
        }

        return false;
      }
    });

    // Further, we need to add ourselves as a TextWatcher of the password edit
    // view. This is because as of Android 1.6, characters are no longer passed
    // to onKey above. However, some keys, including Enter, are still processed
    // by onKey, so the above code is fine and probably needs to stay.
    password.addTextChangedListener(this);

    FileUtils.cleanupDataFiles(this);
    Log.d(LOG_TAG, "LoginActivity.onCreate done");
  }

  /**
   * Reset the activity's UI when we come back from any other activity on
   * the phone.
   */
  @Override
  protected void onResume() {
    Log.d(LOG_TAG, "LoginActivity.onResume");
    super.onResume();

    // NOTE: don't reset the static secrets member here.  I used to do that,
    // and then discovered that an orientation change, at the wrong moment,
    // would cause all secrets to be lost.  The scenario was as follows:
    //
    //  . the user start secrets, enters his password, and presses enter
    //  . while the secrets are being loaded and decrypted, he changes the
    //    orientation (i.e. opens or closes the keyboard)
    //  . once the secrets are loaded, an intent to start the
    //    SecretsListActivity is queued up with startActivity()
    //  . before switching to the new activity, the system destroys this
    //    activity and then recreates it.  onResume() is called, which used to
    //    set the static secrets member to null
    //  . the SecretsListActivity is finally started, and when it tries to get
    //    the secrets list, its null
    //  . when the user leaves the SecretsListActivity, the list of secrets
    //    is overwritten by an empty list

    // If there is no existing secrets file, this is a first-run scenario.
    // (Its also possible that the user started the app but never got passed
    // entering his password)  In this case, we will show a special first time
    // message with instructions about entering a password, followed by a
    // validation pass to get him to enter the password again.
    isFirstRun = isFirstRun();
    TextView instructions = (TextView)findViewById(R.id.login_instructions);
    TextView strength = (TextView)findViewById(R.id.password_strength);
    TextView password = (TextView) findViewById(R.id.login_password);
    if (isFirstRun) {
      instructions.setText(R.string.login_instruction_1);
      strength.setVisibility(TextView.VISIBLE);
      updatePasswordStrengthView(password.getText().toString());
    } else {
      instructions.setText("");
      strength.setVisibility(TextView.GONE);
    }

    password.setHint(R.string.login_enter_password);
    password.requestFocus();
    Log.d(LOG_TAG, "LoginActivity.onResume done");
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.login_menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    boolean handled = false;

    switch(item.getItemId()) {
      case R.id.reset_password:
        showDialog(DIALOG_RESET_PASSWORD);
        handled = true;
        break;
      default:
        break;
    }

    return handled;
  }

  @Override
  public Dialog onCreateDialog(int id) {
    Dialog dialog = null;

    switch (id) {
      case DIALOG_RESET_PASSWORD: {
        DialogInterface.OnClickListener listener =
            new DialogInterface.OnClickListener() {
              @Override
              public void onClick(DialogInterface dialog, int which) {
                if (DialogInterface.BUTTON_POSITIVE == which) {
                  // If we delete the secrets from disk, make sure to also
                  // clear them from memory too.  This is to handle the case
                  // where the user knows his password and has logged in, but
                  // he returned to the login screen and asked to reset his
                  // password.
                  if (!FileUtils.deleteSecrets(LoginActivity.this)) {
                    showToast(R.string.error_reset_password, Toast.LENGTH_LONG);
                  } else {
                    isValidatingPassword = false;
                    clearSecrets();
                  }

                  onResume();
                }
              }
            };
        dialog = new AlertDialog.Builder(this)
            .setTitle(R.string.login_menu_reset_password)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.login_menu_reset_password_message)
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

  /** Overrides from TextWatcher */
  @Override
  public void afterTextChanged(Editable s) {
  }
  @Override
  public void beforeTextChanged(
      CharSequence s, int start, int count, int after) {
  }

  @Override
  public void onTextChanged(CharSequence s, int start, int before, int count) {
    Log.d(LOG_TAG, "LoginActivity.onTextChanged");

    // A key was pressed, so update the UI.
    updatePasswordStrengthView(s.toString());
  }

  /**
   * Determines if this is the first run of the program.  A first run is
   * detected if there is no existing secrets file.
   */
  private boolean isFirstRun() {
    return !FileUtils.secretsExist(this);
  }

  /**
   * Handle a user click on the password view.
   *
   * @param passwordView The password view holding the entered password.
   */
  private void handlePasswordClick(TextView passwordView) {
    Log.d(LOG_TAG, "LoginActivity.handlePasswordClick");

    // If the secrets have already been loaded, then ignore this click.
    // This can happen in the rare case where the user has entered his password
    // correctly and the secrets were decrypted, and then this activity was
    // destroyed and re-created, like for an orientation change.  See the
    // comment in onResume() for details.  We don't want to process this click
    // since we *will* be entering the program, but with incorrect ciphers.
    // This means that on exit, the secrets will be encrypted with an incorrect
    // "password", making it impossible for the user to login again, since this
    // "password" will be unknown to the user.
    if (null != secrets) {
      Log.d(LOG_TAG, "LoginActivity.handlePasswordClick ignoring");
      return;
    }

    // The program tries to minimize the amount of the time the users password
    // is held in memory.  The password edit field is cleared immediately
    // after getting the value, and the password string is held only as long as
    // required to generated the ciphers.
    //
    // It seems that to get the hint to appear properly in the on screen
    // keyboard, the hint text must be set before before clearing the password
    // from the edit view.  So the clearing of the edit text is done in the
    // various code paths below, after setting the hint as needed.
    String passwordString = passwordView.getText().toString();

    if (isFirstRun) {
      TextView instructions = (TextView)findViewById(R.id.login_instructions);
      TextView strength = (TextView)findViewById(R.id.password_strength);

      if (!isValidatingPassword) {
        // This is the first run, and the user has created his password for the
        // first time.  We need to get him to validate it, so show the second
        // set of instructions, remember the password, clear the password field,
        // and wait for him to enter it again.
        instructions.setText(R.string.login_instruction_2);
        passwordView.setHint(R.string.login_validate_password);
        passwordView.setText("");
        strength.setVisibility(TextView.GONE);

        this.passwordString = passwordString;
        isValidatingPassword = true;
        return;
      } else {
        // This is the first run, and the user is validating his password.
        // If they are the same, continue to the next activity.  If not,
        // display an error message and go back to creating a brand new
        // password.
        if (!passwordString.equals(this.passwordString)) {
          instructions.setText(R.string.login_instruction_1);
          strength.setVisibility(TextView.VISIBLE);
          passwordView.setHint(R.string.login_enter_password);
          passwordView.setText("");
          showToast(R.string.invalid_password, Toast.LENGTH_SHORT);

          this.passwordString = null;
          isValidatingPassword = false;
          return;
        }
      }
    }

    passwordView.setText("");

    // Lets not save the password in memory anywhere.  Create all the ciphers
    // we will need based on the password and save those.  First get the salt
    // that is unique for this device.  If we can't find one, null is returned.
    FileUtils.SaltAndRounds pair = FileUtils.getSaltAndRounds(this,
        FileUtils.SECRETS_FILE_NAME);
    SecurityUtils.saveCiphers(SecurityUtils.createCiphers(passwordString,
                                                          pair.salt,
                                                          pair.rounds));

    ArrayList<Secret> loadedSecrets = null;

    if (isFirstRun) {
      loadedSecrets = new ArrayList<Secret>();

      // Immediately save an empty file to hold the secrets.
      Cipher cipher = SecurityUtils.getEncryptionCipher();
      File file = getFileStreamPath(FileUtils.SECRETS_FILE_NAME);
      byte[] salt = SecurityUtils.getSalt();
      int rounds = SecurityUtils.getRounds();
      int err = FileUtils.saveSecrets(this, file, cipher, salt, rounds,
                                      loadedSecrets);
      if (0 != err) {
        showToast(err, Toast.LENGTH_LONG);
        return;
      }
    } else {
      loadedSecrets = FileUtils.loadSecrets(this);
      if (null == loadedSecrets) {
        // Loading failed. Try the old object format using same cipher
        loadedSecrets = FileUtils.loadSecretsV3(this);
        if (null == loadedSecrets) {
          // Loading the secrets failed again. Try loading with the old
          // encryption algorithm in case were are reading an older file.
          Cipher cipher2 = SecurityUtils.createDecryptionCipherV2(
              passwordString, pair.salt, pair.rounds);
          if (null != cipher2) {
            loadedSecrets = FileUtils.loadSecretsV2(this, cipher2, pair.salt,
                                                 pair.rounds);
          }
        }
        if (null == loadedSecrets) {
          // Loading the secrets failed again. Try an even older encryption
          // algorithm.
          Cipher cipher1 =
              SecurityUtils.createDecryptionCipherV1(passwordString);
          if (null != cipher1)
            loadedSecrets = FileUtils.loadSecretsV1(this, cipher1);
        }
        if (null == loadedSecrets) {
          // TODO(rogerta): need better error message here. There are probably
          // many reasons that we might not be able to open the file.
          showToast(R.string.invalid_password, Toast.LENGTH_LONG);
          return;
        }

        // previous versions were case-sensitive and may need to be sorted
        Collections.sort(loadedSecrets);
      }
    }

    // Ensure the globals array are allocated.
    if (secrets == null)
      secrets = new ArrayList<Secret>();

    if (deletedSecrets == null)
      deletedSecrets = new ArrayList<Secret>();

    // extract the deleted secrets from the global secrets list
    replaceSecrets(loadedSecrets);

    passwordString = null;
    Intent intent = new Intent(LoginActivity.this, SecretsListActivity.class);
    startActivity(intent);
    Log.d(LOG_TAG, "LoginActivity.handlePasswordClick done");
  }

  /**
   * Show a toast on the screen with the given message.  If a toast is already
   * being displayed, the message is replaced and timer is restarted.
   *
   * @param message Resource id of tText to display in the toast.
   * @param length Length of time to show toast.
   */
  @SuppressLint("ShowToast")
  private void showToast(int message, int length) {
    if (null == toast) {
      toast = Toast.makeText(LoginActivity.this, message, length);
      toast.setGravity(Gravity.CENTER, 0, 0);
    } else {
      toast.setText(message);
    }

    toast.show();
  }

  /**
   * If the password strength field is visible, recalculate the password
   * strength and update the password strength TextView.
   */
  private void updatePasswordStrengthView(String password) {
    // First, check to see if the view is visible at all before we proceed. We
    // rely on the LoginActivity flow to hide or show the password view
    // depending on what state it is in.
    TextView strengthView = (TextView)findViewById(R.id.password_strength);
    if (TextView.VISIBLE != strengthView.getVisibility())
      return;

    if (password.isEmpty()) {
      strengthView.setText("");
      return;
    }

    // Update UI appropriately based on the strength calculated.
    PasswordStrength str = PasswordStrength.calculateStrength(password);
    strengthView.setText(MessageFormat.format(
        getText(R.string.password_strength_caption).toString(),
        str.getText(this)));
    strengthView.setTextColor(str.getColor());
  }

  /** Gets the global list of the user's deleted secrets.
   * @return secrets collection
   */
  public static ArrayList<Secret> getDeletedSecrets() {
    return deletedSecrets;
  }

  /** Gets the global list of the user's secrets.
   * @return secrets collection
   */
  public static ArrayList<Secret> getSecrets() {
    return secrets;
  }

  /** Overwrite the current secrets with the given list.
   * @param newSecrets list of new secrets

   */
  public static void replaceSecrets(ArrayList<Secret> newSecrets) {
    // I don't want to change the actual instance of the global array that
    // holds the secrets, since this array is referred to from other places
    // in the code.  I will simply replace the contents of the existing array
    // with the entries from the new one.

    // Here we separate out deletions into their own array.

    LoginActivity.secrets.clear();
    LoginActivity.deletedSecrets.clear();
    for (Secret secret : newSecrets) {
      if (secret.isDeleted()) {
        deletedSecrets.add(secret);
      } else {
        secrets.add(secret);
      }
    }
  }

  /**
   * Remove secrets from memory and clear the ciphers.  This method does not
   * save the secrets before clearing them; it is assumed they are already
   * saved.
   */
  public static void clearSecrets() {
    secrets = null;
    deletedSecrets = null;
    SecurityUtils.clearCiphers();
  }
}
