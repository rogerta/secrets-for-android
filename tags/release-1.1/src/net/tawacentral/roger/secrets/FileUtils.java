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
import android.util.Log;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

/**
 * Helper class to manage reading and writing the secrets file.  The file
 * is encrypted using the ciphers created by the SecurityUtils helper
 * functions.
 *
 * @author rogerta
 */
public class FileUtils {
  /** Name of the secrets file. */
  public static final String SECRETS_FILE_NAME = "secrets";

  /** Name of the secrets backup file on the SD card. */
  public static final String SECRETS_FILE_NAME_SDCARD = "/sdcard/secrets";

  /** Name of the secrets CSV file on the SD card. */
  public static final String SECRETS_FILE_NAME_CSV = "/sdcard/secrets.csv";

  /** Name of the OI Safe CSV file on the SD card. */
  public static final String OI_SAFE_FILE_NAME_CSV = "/sdcard/oisafe.csv";

  private static final File SECRETS_FILE_CSV = new File(SECRETS_FILE_NAME_CSV);
  private static final File OI_SAFE_FILE_CSV = new File(OI_SAFE_FILE_NAME_CSV);

  // Secrets CSV column names
  public static final String COL_DESCRIPTION = "Description";
  public static final String COL_USERNAME = "Id";
  public static final String COL_PASSWORD = "PIN";
  public static final String COL_EMAIL = "Email";
  public static final String COL_NOTES= "Notes";

  private static final String EMPTY_STRING = "";
  private static final String INDENT = "   ";

  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets";

  /** Does the secrets file exist? */
  public static boolean secretsExist(Context context) {
    String[] filenames = context.fileList();
    for (String name : filenames) {
      if (name.equals(SECRETS_FILE_NAME)) {
        return true;
      }
    }

    return false;
  }

  /**
   * Saves the secrets to file using the password retrieved from the user.
   *
   * @return True if saved successfully
   * */
  public static boolean saveSecrets(Context context, List<Secret> secrets) {
    Log.d(LOG_TAG, "FileUtils.saveSecrets");

    Cipher cipher = SecurityUtils.getEncryptionCipher();
    if (null == cipher)
      return false;

    ObjectOutputStream output = null;
    boolean success = false;

    try {
      output = new ObjectOutputStream(
          new CipherOutputStream(context.openFileOutput(SECRETS_FILE_NAME,
                                                        Context.MODE_PRIVATE),
                                 cipher));
      output.writeObject(secrets);
      success = true;
    } catch (Exception ex) {
    } finally {
      try {if (null != output) output.close();} catch (IOException ex) {}
    }

    return success;
  }

  /**
   * Backup the secrets to SD card using the password retrieved from the user.
   *
   * @return True if saved successfully
   */
  public static boolean backupSecrets(Context context, List<Secret> secrets) {
    Log.d(LOG_TAG, "FileUtils.backupSecrets");

    Cipher cipher = SecurityUtils.getEncryptionCipher();
    if (null == cipher)
      return false;

    ObjectOutputStream output = null;
    boolean success = false;

    try {
      output = new ObjectOutputStream(
          new CipherOutputStream(new FileOutputStream(SECRETS_FILE_NAME_SDCARD),
                                 cipher));
      output.writeObject(secrets);
      success = true;
    } catch (Exception ex) {
    } finally {
      try {if (null != output) output.close();} catch (IOException ex) {}
    }

    return success;
  }

  /** Opens the secrets file using the password retrieved from the user. */
  // TODO(rogerta): the readObject() method does not support generics.
  @SuppressWarnings("unchecked")
  public static ArrayList<Secret> loadSecrets(Context context) {
    Log.d(LOG_TAG, "FileUtils.loadSecrets");

    Cipher cipher = SecurityUtils.getDecryptionCipher();
    if (null == cipher)
      return null;

    ArrayList<Secret> secrets = null;
    ObjectInputStream input = null;

    try {
      input = new ObjectInputStream(
          new CipherInputStream(context.openFileInput(SECRETS_FILE_NAME),
                                cipher));
      secrets = (ArrayList<Secret>) input.readObject();
    } catch (Exception ex) {
      Log.e(LOG_TAG, "loadSecrets", ex);
    } finally {
      try {if (null != input) input.close();} catch (IOException ex) {}
    }

    // If we were not able to load the secrets, try using the old decryption
    // cipher.
    if (null == secrets) {
      try {
        cipher = SecurityUtils.getOldDecryptionCipher();
        input = new ObjectInputStream(
            new CipherInputStream(context.openFileInput(SECRETS_FILE_NAME),
                                  cipher));
        secrets = (ArrayList<Secret>) input.readObject();
      } catch (Exception ex) {
        Log.e(LOG_TAG, "loadSecrets(old)", ex);
      } finally {
        try {if (null != input) input.close();} catch (IOException ex) {}
      }
    }

    return secrets;
  }

  /**
   * Restore the secrets from the SD card using the password retrieved from
   * the user.
   */
  // TODO(rogerta): the readObject() method does not support generics.
  @SuppressWarnings("unchecked")
  public static ArrayList<Secret> restoreSecrets(Context context) {
    Log.d(LOG_TAG, "FileUtils.restoreSecrets");

    Cipher cipher = SecurityUtils.getDecryptionCipher();
    if (null == cipher)
      return null;

    ArrayList<Secret> secrets = null;
    ObjectInputStream input = null;

    try {
      input = new ObjectInputStream(
          new CipherInputStream(new FileInputStream(SECRETS_FILE_NAME_SDCARD),
                                cipher));
      secrets = (ArrayList<Secret>) input.readObject();
    } catch (Exception ex) {
      Log.e(LOG_TAG, "restoreSecrets", ex);
    } finally {
      try {if (null != input) input.close();} catch (IOException ex) {}
    }

    return secrets;
  }

  /** Deletes all secrets from the phone. */
  public static boolean deleteSecrets(Context context) {
    return context.deleteFile(SECRETS_FILE_NAME);
  }

  /**
   * Export secrets to a CSV file on the SD card.  See the description of
   * the importSecrets() method for more details about the format written.
   */
  public static boolean exportSecrets(Context context,List<Secret> secrets) {
    // An array to hold the rows that will be written to the CSV file.
    String[] row = new String[] {
        COL_DESCRIPTION, COL_USERNAME, COL_PASSWORD, COL_EMAIL, COL_NOTES
    };
    CSVWriter writer = null;
    boolean success = false;

    try {
      writer = new CSVWriter(new FileWriter(SECRETS_FILE_NAME_CSV));

      // Write descriptive headers.
      writer.writeNext(row);

      // Write out each secret.
      for (Secret secret : secrets) {
        row[0] = secret.getDescription();
        row[1] = secret.getUsername();
        row[2] = secret.getPassword(true);  // true: forExport
        row[3] = secret.getEmail();
        row[4] = secret.getNote();

        // NOTE: writeNext() handles nulls in row[] gracefully.
        writer.writeNext(row);
        success = true;
      }
    } catch (Exception ex) {
      Log.e(LOG_TAG, "exportSecrets", ex);
    } finally {
      try {if (null != writer) writer.close();} catch (IOException ex) {}
    }

    return success;
  }

  /**
   * Returns the file that should be imported.  This method will look for a file
   * on the SD card whose name is either the secrets CSV file or the OI Safe
   * CSV file.  To support other formats, should add some code here to detect
   * those files.
   *
   * If more than one CSV file of exist, the one last modified is used.
   */
  public static File getFileToImport() {
    boolean haveSecretsCsv = SECRETS_FILE_CSV.exists();
    boolean haveOiSafeCsv = OI_SAFE_FILE_CSV.exists();
    File file = null;

    if (haveSecretsCsv && haveOiSafeCsv) {
      if (SECRETS_FILE_CSV.lastModified() > OI_SAFE_FILE_CSV.lastModified()) {
        file = SECRETS_FILE_CSV;
      } else {
        file = OI_SAFE_FILE_CSV;
      }
    } else if (haveSecretsCsv) {
      file = SECRETS_FILE_CSV;
    } else if (haveOiSafeCsv) {
      file = OI_SAFE_FILE_CSV;
    }

    return file;
  }

  /**
   * Import secrets from a CSV file.  This function will first clear the secrets
   * list argument before adding anything read from the file. If there are no
   * errors while reading, true is returned.
   *
   * Note that its possible for this function to return false, and yet the
   * secrets list is not empty.  This means that some, but not all, secrets
   * were able to be read from the file.
   *
   * For the moment, this function assumes that the first line is a description
   * so it is not imported.  At least 5 columns are assumed for each line:
   * description, username, password, email, and notes, in that order (this is
   * the default format as written by exportSecrets()).
   *
   * This function will attempt to detect OI Safe CSV files and import them
   * accordingly.  It does this by reading the first line of file, and looking
   * for column descriptions as exported by OI Safe 1.1.0.
   *
   * @param context Activity context for global services
   * @param file File to import
   * @param secrets List to append secrets read from the file
   * @return True if all secrets read successfully, and false otherwise
   */
  public static boolean importSecrets(Context context,
                                      File file,
                                      ArrayList<Secret> secrets) {
    secrets.clear();

    boolean isOiSafeCsv = false;
    boolean isSecretsScv = false;
    boolean success = false;
    CSVReader reader = null;

    try {
      reader = new CSVReader(new FileReader(file));

      // Use the first line to determine the type of csv file.  Secrets will
      // output 5 columns, with the names as used in the exportSecrets()
      // function.  OI Safe 1.1.0 is also detected.
      String headers[] = reader.readNext();
      if (null != headers) {
        isSecretsScv = isSecretsCsv(headers);
        if (!isSecretsScv)
          isOiSafeCsv = isOiSafeCsv(headers);
      }

      // Read all the rest of the lines as secrets.
      for (;;) {
        String[] row = reader.readNext();
        if (null == row)
          break;

        Secret secret = new Secret();
        if (isOiSafeCsv) {
          secret.setDescription(row[1]);
          secret.setUsername(row[3]);
          secret.setPassword(row[4]);
          secret.setEmail(EMPTY_STRING);

          // I will combine the category, website, and notes columns into
          // the notes field in secrets.
          int approxMaxLength = row[0].length() + row[2].length() +
                                row[5].length() + 32;
          StringBuilder builder = new StringBuilder(approxMaxLength);
          builder.append(row[5]).append("\n\n");
          builder.append("Category: ").append(row[0]).append('\n');
          if (null != row[2] && row[2].length() > 0)
            builder.append("Website: ").append(row[2]).append('\n');

          secret.setNote(builder.toString());
        } else {
          // If we get here, then this may be an unknown format.  For better
          // or for worse, this is a "best effort" to import that data.
          secret.setDescription(row[0]);
          secret.setUsername(row[1]);
          secret.setPassword(row[2]);
          secret.setEmail(row[3]);
          secret.setNote(row[4]);
        }

        secrets.add(secret);
      }

      // We'll only return complete success if we get here, and we detected
      // that we knew the file format.  This will give the user an indication
      // do look at the secrets if the format was not automatically detected.
      success = isOiSafeCsv || isSecretsScv;
    } catch (Exception ex) {
      Log.e(LOG_TAG, "importSecrets", ex);
    } finally {
      try {if (null != reader) reader.close();} catch (IOException ex) {}
    }

    return success;
  }

  /** Is it likely that the CSV file is in OI Safe format? */
  private static boolean isOiSafeCsv(String[] headers) {
    if (headers[0].equalsIgnoreCase("Category") &&
        headers[1].equalsIgnoreCase("Description") &&
        headers[2].equalsIgnoreCase("Website") &&
        headers[3].equalsIgnoreCase("Username") &&
        headers[4].equalsIgnoreCase("Password") &&
        headers[5].equalsIgnoreCase("Notes"))
      return true;

    return false;
  }

  /** Is it likely that the CSV file is in secrets format? */
  private static boolean isSecretsCsv(String[] headers) {
    if (headers[0].equalsIgnoreCase(COL_DESCRIPTION) &&
        headers[1].equalsIgnoreCase(COL_USERNAME) &&
        headers[2].equalsIgnoreCase(COL_PASSWORD) &&
        headers[3].equalsIgnoreCase(COL_EMAIL) &&
        headers[4].equalsIgnoreCase(COL_NOTES))
      return true;

    return false;
  }

  /** Returns a list of the supported CSV file names, newline separated. */
  public static String getCsvFileNames() {
    StringBuilder builder = new StringBuilder();
    builder.append(INDENT).append(SECRETS_FILE_CSV.getName()).append('\n');
    builder.append(INDENT).append(OI_SAFE_FILE_CSV.getName());

    return builder.toString();
  }
}
