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

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FileBackupHelper;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.json.JSONArray;
import org.json.JSONException;

import java.io.*;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

/**
 * Helper class to manage reading and writing the secrets file.  The file
 * is encrypted using the ciphers created by the SecurityUtils helper
 * functions.
 * 
 * Methods that touch the main secrets files are thread safe.  This allows
 * the file to be saved in a background thread so that the UI is not blocked
 * in the most common use cases.  Note that stopping the app and restarting
 * it again may still cause the UI to block if the write take a long time. 
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
  private static final String RP_PREFIX = "@";

  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets";

  /** Lock for accessing main secrets file. */
  private static final Object lock = new Object();
  
  /** Does the secrets file exist? */
  public static boolean secretsExist(Context context) {
    // Instead of just checking for the existence of the secrets file
    // explicitly, I will check for the existence of any file in the
    // application's data directory.  This check is valid because:
    //
    //  - when the user runs the app for the first time, an empty secrets file
    //    is always written
    //  - there is at least one file in existences even during the save
    //    operation
    //
    // The benefit of making this assumption is that I don't need to acquire
    // the file lock in order to test for the existence of the secrets file.
    // This speeds up leaving the secrets list activity since the test for
    // existence does not need to wait for the save to finish.  This also
    // speeds the wake time when secrets was active at the time the phone
    // went to sleep.
    String[] filenames = context.fileList();
    return filenames.length > 0;
  }

  /** Does the secrets restore file exist on the SD card? */
  public static boolean restoreFileExist() {
    File file = new File(SECRETS_FILE_NAME_SDCARD);
    return file.exists();
  }

  /** Is the restore file too old? */
  public static boolean isRestoreFileTooOld() {
    File file = new File(SECRETS_FILE_NAME_SDCARD);
    if (!file.exists())
      return false;
    
    long lastModified = file.lastModified();
    long now = System.currentTimeMillis();
    long oneWeeks = 7 * 24 * 60 * 60 * 1000;  // One week.
    
    return (now - lastModified) > oneWeeks;
  }
  
  /** Is the restore point too old? */
  private static boolean isRestorePointTooOld(File file) {
    long lastModified = file.lastModified();
    long now = System.currentTimeMillis();
    long twoDays = 2 * 24 * 60 * 60 * 1000;  // 2 days.
    
    return (now - lastModified) > twoDays;
  }
  
  /**
   * Get all existing restore points, including the restore file on the SD card
   * if it exists.
   * 
   * @param context Avtivity context in which the save is called.
   * @return A list of all possible restore points.
   */
  public static List<String> getRestorePoints(Context context) {
    String[] filenames = context.fileList(); 
    ArrayList<String> list = new ArrayList<String>(filenames.length + 1);
    if (restoreFileExist())
      list.add(SECRETS_FILE_NAME_SDCARD);
    
    for (String filename : filenames) {
      if (filename.startsWith(RP_PREFIX))
        list.add(filename);
    }
    
    return list;
  }
  
  /**
   * Cleanup any residual data files from a previous bad run, if any.  The
   * algorithm is as follows:
   * 
   * - delete any file with "new" in the name.  These are possibly partial
   *   writes, so their contents is undefined.
   * - if no secrets file exists, rename the most recent auto resptore point
   *   file to secrets.
   * - if too many auto restore point files exist, delete the extra ones.
   *   However, don't delete any auto-backups younger than 48 hours.
   */
  public static void cleanupDataFiles(Context context) {
    Log.d(LOG_TAG, "FileUtils.cleanupDataFiles");
    synchronized (lock) {
      String[] filenames = context.fileList();
      int oldCount = filenames.length;
      boolean secretsFileExists = context.getFileStreamPath(SECRETS_FILE_NAME)
          .exists();
      
      // Cleanup any partial saves and find the most recent auto-backup file.
      {
        File mostRecent = null;
        int mostRecentIndex = -1;
        for (int i = 0; i < filenames.length; ++i) {
          String filename = filenames[i];
          if (-1 != filename.indexOf("new")) {
            context.deleteFile(filename);
            --oldCount;
            filenames[i] = null;
          } else if (filename.startsWith(RP_PREFIX)) {
            if (!secretsFileExists) {
              File f = context.getFileStreamPath(filename);
              if (null == mostRecent ||
                  f.lastModified() > mostRecent.lastModified()) {
                mostRecent = f;
                mostRecentIndex = i;
              }
            }
          } else {
            --oldCount;
            filenames[i] = null;
          }
        }

        // If we don't have a secrets file but found an auto-backup file,
        // rename the more recent auto-backup to secrets.
        if (null != mostRecent) {
          mostRecent.renameTo(context.getFileStreamPath(SECRETS_FILE_NAME));
          --oldCount;
          filenames[mostRecentIndex] = null;
        }
      }
      
      // If there are too many old files, delete the oldest extra ones.
      while (oldCount > 10) {
        File oldest = null;
        int oldestIndex = -1;
        
        for (int i = 0; i < filenames.length; ++i) {
          String filename = filenames[i];
          if (null == filename)
            continue;
          
          File f = context.getFileStreamPath(filename);
          if (null == oldest || f.lastModified() < oldest.lastModified()) {
            oldest = f;
            oldestIndex = i;
          }
        }
        
        if (null != oldest) {
          // If the oldest file is not too old, then just break out of the
          // loop.  We don't want to delete any "old" files that are too
          // recent.
          if (!FileUtils.isRestorePointTooOld(oldest))
            break;
          
          oldest.delete();
          --oldCount;
          filenames[oldestIndex] = null;
        }
      }
    }
  }
  
  /**
   * Saves the secrets to file using the password retrieved from the user.
   *
   * @param context Activity context in which the save is called.
   * @param existing The file to save into.
   * @param cipher The encryption cipher to use with the file.
   * @param secrets The list of secrets to save.
   * @return True if saved successfully.
   */
  public static int saveSecrets(Context context,
                                File existing,
                                Cipher cipher,
                                List<Secret> secrets) {
    Log.d(LOG_TAG, "FileUtils.saveSecrets");
    synchronized (lock) {
      Log.d(LOG_TAG, "FileUtils.saveSecrets: got lock");
      
      // To be as safe as possible, for example to handle low space conditions,
      // we will save the secrets to a file using the following steps:
      //
      //  1- write the secrets to a new temporary file (tempn)
      //     on error: delete tempn
      //  2- rename the existing secrets file, if any (to tempo)
      //     on error: delete tempn
      //  3- rename the new temporary file to the official file name
      //     on error: rename tempo back to existing, delete tempn
      //
      // The old file we hang around for a while.  The cleanupDataFiles()
      // method, which is called whenever Secrets is re-launched, will make
      // sure that the old file don't accumulate indefinitely.
      String prefix = MessageFormat.format(RP_PREFIX +
          "{0,date,yy.MM.dd}-{0,time,HH:mm}", new Date(),
          null);
      File parent = existing.getParentFile();
      File tempn = new File(parent, "new");
      File tempo = new File(parent, prefix);
      for (int i = 0; tempn.exists() || tempo.exists(); ++i) {
        tempn = new File(parent, "new" + i);
        tempo = new File(parent, prefix + i);
      }
      // Step 1
      ObjectOutputStream output = null;
      try {
        output = new ObjectOutputStream(new CipherOutputStream(
            new FileOutputStream(tempn), cipher));
        output.writeObject(secrets);
      } catch (Exception ex) {
        Log.d(LOG_TAG, "FileUtils.saveSecrets: could not write secrets file");
        // NOTE: this delete() works, even though the file is still open.
        tempn.delete();
        return R.string.error_save_secrets;
      } finally {
        try {if (null != output) output.close();} catch (IOException ex) {}
      }

      // Step 2
      if (existing.exists() && !existing.renameTo(tempo)) {
        Log.d(LOG_TAG, "FileUtils.saveSecrets: could not move existing file");
        tempn.delete();
        return R.string.error_cannot_move_existing;
      }

      // Step 3
      if (!tempn.renameTo(existing)) {
        Log.d(LOG_TAG, "FileUtils.saveSecrets: could not move new file");
        tempo.renameTo(existing);
        tempn.delete();
        return R.string.error_cannot_move_new;
      }

      Log.d(LOG_TAG, "FileUtils.saveSecrets: done");
      return 0;
    }
  }

  /**
   * Backup the secrets to SD card using the password retrieved from the user.
   *
   * @param context Avtivity context in which the backup is called.
   * @param cipher The encryption cipher to use with the file.
   * @param secrets The list of secrets to save.
   * @return True if saved successfully
   */
  public static boolean backupSecrets(Context context,
                                      Cipher cipher,
                                      List<Secret> secrets) {
    Log.d(LOG_TAG, "FileUtils.backupSecrets");

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


  public static ArrayList<Secret> restoreSecretsStream(Context context, byte[] secrets) {
    Cipher cipher = SecurityUtils.getDecryptionCipher();

    if(cipher == null || secrets == null || secrets.length == 0) return null;

    CipherInputStream cis = new CipherInputStream(new ByteArrayInputStream(secrets), cipher);
    BufferedInputStream bis = new BufferedInputStream(cis);
    byte[] secretStrBytes = new byte[secrets.length];
    int offset = 0;
    int read = 0;
    try {
      while (offset < secretStrBytes.length
          && (read = bis.read(secretStrBytes, offset, secretStrBytes.length - offset)) >= 0) {
        offset += read;
      }
      String jsonString = new String(secretStrBytes, "UTF-8");
      JSONArray jsonSecrets = new JSONArray(jsonString);
      ArrayList<Secret> secretList = new ArrayList<Secret>();
      for(int i = 0; i < jsonSecrets.length(); i++) {
        secretList.add(Secret.fromJSONString(jsonSecrets.getString(i)));
      }
      return secretList;
    } catch (IOException e) {
      Log.e(LOG_TAG, "Error restoring secret stream from OBA", e);
    } catch (JSONException e) {
      Log.e(LOG_TAG, "Error restoring secret stream from OBA", e);
    }
    return null;
  }

  /**
   * Returns an encrypted json stream representing the user's secrets.
   *
   * @param context Avtivity context in which the backup is called.
   * @param cipher The encryption cipher to use with the file.
   * @param secrets The list of secrets to save.
   * @return byte array of secrets
   */
  public static byte[] secretsStream(Context context,
                                      Cipher cipher,
                                      List<Secret> secrets) {
    Log.d(LOG_TAG, "FileUtils.backupSecrets");

    if (null == cipher)
      return null;

    CipherOutputStream output = null;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    boolean success = false;
    try {
      output = new CipherOutputStream(baos,
                                 cipher);
      JSONArray jsonArray = new JSONArray();
      for(Secret secret : secrets) {
        jsonArray.put(secret.toJSONString());
      }
      output.write(jsonArray.toString().getBytes("UTF-8"));
      success = true;
    } catch (Exception ex) {
    } finally {
      try {if (null != output) output.close();} catch (IOException ex) {}
    }

    if(!success) {
      throw new RuntimeException("Failed writing secrets!");
    }
    return baos.toByteArray();
  }

  /**
   * Opens the secrets file using the password retrieved from the user.
   * 
   * @param context Avtivity context in which the load is called.
   * @return A list of loaded secrets.
   */
  // TODO(rogerta): the readObject() method does not support generics.
  @SuppressWarnings("unchecked")
  public static ArrayList<Secret> loadSecrets(Context context) {
    Log.d(LOG_TAG, "FileUtils.loadSecrets");
    synchronized (lock) {
      Log.d(LOG_TAG, "FileUtils.loadSecrets: got lock");

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
  
      Log.d(LOG_TAG, "FileUtils.loadSecrets: done");
      return secrets;
    }
  }

  /**
   * Restore the secrets from the SD card using the password retrieved from
   * the user.
   * 
   * @param context Activity context in which the load is called.
   * @param rp A restore point name.  This should be one of the strings
   *     returned by the getRestorePoints() method. 
   */
  // TODO(rogerta): the readObject() method does not support generics.
  @SuppressWarnings("unchecked")
  public static ArrayList<Secret> restoreSecrets(Context context, String rp) {
    Log.d(LOG_TAG, "FileUtils.restoreSecrets");

    Cipher cipher = SecurityUtils.getDecryptionCipher();
    if (null == cipher)
      return null;

    ArrayList<Secret> secrets = null;
    ObjectInputStream input = null;

    try {
      InputStream stream = SECRETS_FILE_NAME_SDCARD.equals(rp)
          ? new FileInputStream(rp)
          : context.openFileInput(rp);
      input = new ObjectInputStream( new CipherInputStream(stream, cipher));
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
    Log.d(LOG_TAG, "FileUtils.deleteSecrets");
    synchronized (lock) {
      String filenames[] = context.fileList();
      for (String filename : filenames) {
        context.deleteFile(filename);
      }
    }
    
    return true;
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

  static public class SecretsBackupAgent extends BackupAgentHelper {
    /** Tag for logging purposes. */
    public static final String LOG_TAG = "Secrets";
    
    /** Key in backup set for file data. */
    private static final String KEY ="file";
    
    @Override
    public void onCreate() {
      Log.d(LOG_TAG, "onCreate");
      
      FileBackupHelper helper = new FileBackupHelper(this,
          FileUtils.SECRETS_FILE_NAME);
      addHelper(KEY, helper);
    }
    
    @Override
    public void onBackup(ParcelFileDescriptor oldState,
                         BackupDataOutput data,
                         ParcelFileDescriptor newState) throws IOException {
      Log.d(LOG_TAG, "onBackup");
      synchronized (lock) {
        super.onBackup(oldState, data, newState);
      }
    }
    
    @Override
    public void onRestore(BackupDataInput data,
                          int appVersionCode,
                          ParcelFileDescriptor newState)  throws IOException {    
      Log.d(LOG_TAG, "onRestore");
      synchronized (lock) {
        super.onRestore(data, appVersionCode, newState);
      }
    }
  }
}
