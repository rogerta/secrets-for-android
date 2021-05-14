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
import android.app.backup.FullBackupDataOutput;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import net.tawacentral.roger.secrets.SecurityUtils.CipherInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;

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
@SuppressWarnings("javadoc")
public class FileUtils {
  /** Return value for the getSaltAndRounds() function. */
  public static class SaltAndRounds {
    public SaltAndRounds(byte[] salt, int rounds) {
      this.salt = salt;
      this.rounds = rounds;
    }
    public byte[] salt;
    public int rounds;
  }

  /** Name of the preferences file for backup. */
  public static final String PREFS_FILE_NAME = "backup";

  /**
   * Name of last backup date preference. Long value as number or millis as
   * returned by System.currentTimeMillis().
   */
  public static final String PREF_LAST_BACKUP_DATE = "last_backup_date";

  /**
   * Name of last nag date preference. Long value as number or
   * millis as returned by System.currentTimeMillis().  This is the time at
   * which we last nagged the user to enable online backup.
   */
  public static final String PREF_LAST_NAG_DATE = "last_nag_date";

  /** Name of the secrets file. */
  public static final String SECRETS_FILE_NAME = "secrets";

  /** Name of the secrets backup file on the SD card. */
  public static final String SECRETS_FILE_NAME_SDCARD =
      Environment.getExternalStorageDirectory().getPath() + "/secrets";

  /** Name of the secrets CSV file on the SD card. */
  public static final String SECRETS_FILE_NAME_CSV =
      Environment.getExternalStorageDirectory().getPath() + "/secrets.csv";

  /** Name of the OI Safe CSV file on the SD card. */
  public static final String OI_SAFE_FILE_NAME_CSV =
      Environment.getExternalStorageDirectory().getPath() + "/oisafe.csv";

  /** Name of file to disable deprecation warning. */
  public static final String DISABLE_DEPRECATION_WARNING =
          Environment.getExternalStorageDirectory().getPath() + "/.secrets.dep.disable";

  private static final File SECRETS_FILE_CSV = new File(SECRETS_FILE_NAME_CSV);
  private static final File OI_SAFE_FILE_CSV = new File(OI_SAFE_FILE_NAME_CSV);

  /** Secrets CSV column names */
  public static final String COL_DESCRIPTION = "Description";
  public static final String COL_USERNAME = "Id";
  public static final String COL_PASSWORD = "PIN";
  public static final String COL_EMAIL = "Email";
  public static final String COL_NOTES= "Notes";

  private static final String EMPTY_STRING = "";
  private static final String INDENT = "   ";
  private static final String RP_PREFIX = "@";

  // secrets ID for JSON
  private static final String JSON_SECRETS_ID = "secrets";

  /** Tag for logging purposes. */
  public static final String LOG_TAG = "FileUtils";

  /** Lock for accessing main secrets file. */
  private static final Object lock = new Object();

  private static final byte[] SIGNATURE = {0x22, 0x34, 0x56, 0x79};

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

    // On some Samsung Galaxy S5/S6 devices, a file called
    // "rList-net.tawacentral.roger.secrets.LoginActivity" exists here.  Not
    // sure why.  However, it interferes with the detection of first run.
    // Trying to delete the file does not work, it comes back again. Therefore
    // ignore any files that are not an auto-backup or the main secrets file.
    int length = filenames.length;
    for (String filename: filenames) {
      if (SECRETS_FILE_NAME.equals(filename) ||
          filename.startsWith(RP_PREFIX)) {
        continue;
      }
      --length;
    }

    return length > 0;
  }

  /** Does the secrets restore file exist on the SD card? */
  public static boolean restoreFileExist() {
    File file = new File(SECRETS_FILE_NAME_SDCARD);
    return file.exists();
  }

  /**
   * Gets the time of the last online backup.
   *
   * @param ctx A context to get the preferences from.
   * @return The time of the last online backup, as millisecs since epoch.
   */
  public static long getTimeOfLastOnlineBackup(Context ctx) {
    return ctx.getSharedPreferences(PREFS_FILE_NAME, 0)
        .getLong(PREF_LAST_BACKUP_DATE, 0);
  }

  /**
   * Is the online backup too old?
   *
   * @param ctx A context to get the preferences from.
   * @return True if the last backup is too old.
   */
  public static boolean isOnlineBackupTooOld(Context ctx) {
    long now = System.currentTimeMillis();
    long lastSaved = getTimeOfLastOnlineBackup(ctx);

    // If lastSaved is zero, this means an online backup has never been done.
    // Nag the user about it, but no more than once per week.
    if (lastSaved == 0) {
      SharedPreferences prefs = ctx.getSharedPreferences(PREFS_FILE_NAME, 0);
      long lastNag = prefs.getLong(PREF_LAST_NAG_DATE, 0);
      final long sixDays = 6 * 24 * 60 * 60 * 1000;  // 6 days in millis.
      final long oneWeek = 7 * 24 * 60 * 60 * 1000;  // One week in millis.

      // Don't warn the very first day the user runs the program.
      if (lastNag == 0) {
        prefs.edit().putLong(PREF_LAST_NAG_DATE, now - sixDays).apply();
      } else if ((now - lastNag) > oneWeek) {
        // In order not to nag users more than once a week about missing online
        // backup support, set the last nag time to now.
        prefs.edit().putLong(PREF_LAST_NAG_DATE, now).apply();
        return true;
      }
    }

    return false;
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
   * @param context Activity context in which the save is called.
   * @return A list of all possible restore points.
   */
  public static List<String> getRestorePoints(Context context) {
    String[] filenames = context.fileList();
    ArrayList<String> list = new ArrayList<String>(filenames.length + 2);

    if (restoreFileExist())
      list.add(SECRETS_FILE_NAME_SDCARD);

    // To ease restoring from Google Drive, look for a secrets file in the
    // downloads folder and add that option.
    File downloads = Environment.getExternalStoragePublicDirectory(
        Environment.DIRECTORY_DOWNLOADS);
    File rpDownload = new File(downloads, SECRETS_FILE_NAME);
    if (rpDownload.exists())
      list.add(rpDownload.getAbsolutePath());

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
   * - if no secrets file exists, rename the most recent auto restore point
   *   file to secrets.
   * - if too many auto restore point files exist, delete the extra ones.
   *   However, don't delete any auto-backups younger than 48 hours.
   *
   * @param context Activity context in which the save is called.
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
          if (0 == filename.indexOf("new")) {
            // This is a partial write file, probably corrupted.  Delete it.
            context.deleteFile(filename);
            --oldCount;
            filenames[i] = null;
          } else if (filename.startsWith(RP_PREFIX)) {
            // This is an auto-backup file.  Remember most recent.
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
   * Gets the salt and rounds already in use on this device, or null if none
   * exists.
   *
   * @param context Activity context in which the save is called.
   * @param path The file to read the salt and rounds from.  Can either be the
   *     string SECRETS_FILE_NAME_SDCARD, SECRETS_FILE_NAME, or the  name of a
   *     restore point.
   * @return the salt and rounds
   */
  public static SaltAndRounds getSaltAndRounds(Context context, String path) {
    // The salt is stored as a byte array at the start of the secrets file.
    FileInputStream input = null;
    try {
      input = path.startsWith("/")
          ? new FileInputStream(path)
          : context.openFileInput(path);
      return getSaltAndRounds(input);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "getSaltAndRounds", ex);
    } finally {
      try {if (null != input) input.close();} catch (IOException ex) {}
    }
    return new SaltAndRounds(null, 0);
  }

  /**
   * Gets the salt and rounds already in use on this device, or null if none
   * exists.
   *
   * @param input The stream to read the salt and rounds from.
   * @return the salt and rounds
   *
   * @throws IOException
   */
  public static SaltAndRounds getSaltAndRounds(InputStream input)
      throws IOException {
    // The salt is stored as a byte array at the start of the secrets file.
    byte[] signature = new byte[SIGNATURE.length];
    byte[] salt = null;
    int rounds = 0;
    input.read(signature);
    if (Arrays.equals(signature, SIGNATURE)) {
      int length = input.read();
      salt = new byte[length];
      input.read(salt);
      rounds = input.read();
      if (rounds < 4 || rounds > 31) {
        salt = null;
        rounds = 0;
      }
    }

    return new SaltAndRounds(salt, rounds);
  }

  /**
   * Saves the secrets to file using the password retrieved from the user.
   *
   * @param context Activity context in which the save is called.
   * @param existing The file to save into.
   * @param cipher The encryption cipher to use with the file.
   * @param salt The salt used to create the cipher.
   * @param rounds The number of rounds for bcrypt.
   * @param secrets The collection of secrets to save.
   * @return True if saved successfully.
   */
  public static int saveSecrets(Context context,
                                File existing,
                                Cipher cipher,
                                byte[] salt,
                                int rounds,
                                ArrayList<Secret> secrets) {
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
      // Old files will hang around for a while.  The cleanupDataFiles()
      // method, which is called whenever Secrets is re-launched, will make
      // sure that the old files don't accumulate indefinitely.
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
      FileOutputStream fos = null;
      try {
        fos = new FileOutputStream(tempn);
        writeSecrets(fos, cipher, salt, rounds, secrets);
      } catch (Exception ex) {
        Log.d(LOG_TAG, "FileUtils.saveSecrets: could not write secrets file");
        // NOTE: this delete() works, even though the file is still open.
        tempn.delete();
        return R.string.error_save_secrets;
      } finally {
        try {if (null != fos) fos.close();} catch (IOException ex) {}
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
   * @param context Activity context in which the backup is called.
   * @param cipher The encryption cipher to use with the file.
   * @param salt The salt used to create the cipher.
   * @param rounds The number of rounds for bcrypt.
   * @param secrets The list of secrets to save.
   * @return True if saved successfully
   */
  public static boolean backupSecrets(Context context,
                                      Cipher cipher,
                                      byte[] salt,
                                      int rounds,
                                      ArrayList<Secret> secrets) {
    Log.d(LOG_TAG, "FileUtils.backupSecrets");

    if (null == cipher)
      return false;

    FileOutputStream output = null;
    boolean success = false;

    try {
    	output = new FileOutputStream(SECRETS_FILE_NAME_SDCARD);
      writeSecrets(output, cipher, salt, rounds, secrets);
      success = true;
    } catch (Exception ex) {
    } finally {
      try {if (null != output) output.close();} catch (IOException ex) {}
    }

    return success;
  }

  /* start new load/restore methods */

  /*
   * Load methods.
   * These handle historic file and cipher formats for backward compatability
   * with old secrets and backup files. They differ in both the cipher used and
   * underlying data format.
   *
   * V1 used a cipher with fixed salt and rounds values (C1), and Java serialized
   * object format (F1).
   * V2 used a cipher with variable salt and round values (C2), stored in the secrets
   * file header, and Java serialized object format. Because of the new
   * stored values, the file format (F2) differs from V1.
   * V3 used a modified version of the V2 cipher (password fix) (C3), and the same
   * file format as V2.
   * Current (V4): uses same V3 cipher mechanism, and JSON file format (F3)
   *
   * Pictorially:
   *                 Cipher format
   *
   *             |  C1  |  C2  |  C3
   *          ---|------|------|------
   *          F1 |  V1  |      |
   * File     ---|------|------|------
   * format   F2 |      |  V2  |  V3
   *          ---|------|------|------
   *          F3 |      |      |  V4
   */

  /**
   * Opens the secrets file using the password retrieved from the user.
   *
   * @param context Activity context in which the load is called.
   * @return A list of loaded secrets.
   */
  public static ArrayList<Secret> loadSecrets(Context context) {
    synchronized (lock) {
      Log.d(LOG_TAG, "FileUtils.loadSecrets: got lock");
      return loadSecrets(context, SECRETS_FILE_NAME,
          SecurityUtils.getCipherInfo());
    }
  }

  /**
   * Opens the secrets file using the password retrieved from the user.
   *
   * @param context Activity context in which the load is called.
   * @param fileName Name of file to be loaded
   * @param info CipherInfo
   * @return A list of loaded secrets.
   */
  public static ArrayList<Secret> loadSecrets(Context context,
      String fileName, CipherInfo info) {
    Log.d(LOG_TAG, "FileUtils.loadSecrets");

    if (null == info)
      return null;

    ArrayList<Secret> secrets = null;
    InputStream input = null;

    try {
      input = fileName.startsWith("/")
          ? new FileInputStream(fileName)
          : context.openFileInput(fileName);
      secrets = readSecrets(input, info.decryptCipher, info.salt, info.rounds);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "loadSecrets", ex);
    } finally {
      try {
        if (null != input)
          input.close();
      } catch (IOException ex) {
      }
    }
    Log.d(LOG_TAG, "FileUtils.loadSecrets: done");
    return secrets;
  }

  /**
   * Opens the secrets file using the password retrieved from the user and
   * the old encryption cipher.  This function is called only for backward
   * compatibility purposes, when Secrets encounters an error trying to load
   * the secrets using the current encryption method.
   *
   * V1 used fixed rounds and salt, not stored in the file.
   *
   * @param context Activity context in which the load is called.
   * @param cipher Decryption cipher for old encryption.
   * @return A list of loaded secrets.
   */
  public static ArrayList<Secret> loadSecretsV1(Context context, Cipher cipher) {
    synchronized (lock) {
      Log.d(LOG_TAG, "FileUtils.loadSecretsV1: got lock");
      return loadSecretsV1(context, cipher, SECRETS_FILE_NAME);
    }
  }

  /**
   * See previous method for description.
   *
   * @param context Activity context in which the load is called.
   * @param cipher Decryption cipher for old encryption.
   * @param fileName Name of file to be loaded
   * @return A list of loaded secrets.
   */
  @SuppressWarnings("unchecked")
  public static ArrayList<Secret> loadSecretsV1(Context context, Cipher cipher,
      String fileName) {
    Log.d(LOG_TAG, "FileUtils.loadSecretsV1");
    if (null == cipher)
      return null;

    ArrayList<Secret> secrets = null;
    ObjectInputStream input = null;

    try {
      InputStream fis = fileName.startsWith("/")
          ? new FileInputStream(fileName)
      : context.openFileInput(fileName);
          input = new ObjectInputStream(new CipherInputStream(fis, cipher));
          secrets = (ArrayList<Secret>)input.readObject();
    } catch (Exception ex) {
      Log.e(LOG_TAG, "loadSecretsV1", ex);
    } finally {
      try {if (null != input) input.close();} catch (IOException ex) {}
    }
    Log.d(LOG_TAG, "FileUtils.loadSecretsV1: done");
    return secrets;
  }

  /**
   * Opens the secrets file using the password retrieved from the user and
   * the old encryption cipher.  This function is called only for backward
   * compatibility purposes, when Secrets encounters an error trying to load
   * the secrets using the current encryption method.
   *
   * V2 used variable rounds and salt, stored in the file header.
   *
   * @param context Activity context in which the load is called.
   * @param cipher Decryption cipher for old encryption.
   * @param salt The salt to use when creating the encryption key.
   * @param rounds The number of rounds for bcrypt.
   * @return A list of loaded secrets.
   */
  public static ArrayList<Secret> loadSecretsV2(Context context, Cipher cipher,
      byte[] salt, int rounds) {
    synchronized (lock) {
      return loadSecretsV2(context, SECRETS_FILE_NAME, cipher, salt, rounds);
    }
  }

  /**
   * See previous method for description.
   *
   * @param context Activity context in which the load is called.
   * @param fileName Name of file to be loaded
   * @param cipher Decryption cipher for old encryption.
   * @param salt The salt to use when creating the encryption key.
   * @param rounds The number of rounds for bcrypt.
   * @return A list of loaded secrets.
   */
  public static ArrayList<Secret> loadSecretsV2(Context context,
      String fileName, Cipher cipher, byte[] salt, int rounds) {
    Log.d(LOG_TAG, "FileUtils.loadSecretsV2");
    if (null == cipher)
      return null;
    ArrayList<Secret> secrets = null;
    InputStream input = null;

    try {
      input = fileName.startsWith("/")
          ? new FileInputStream(fileName)
          : context.openFileInput(fileName);
      secrets = readSecretsV2(input, cipher, salt, rounds);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "loadSecretsV2", ex);
    } finally {
      try {
        if (null != input)
          input.close();
      } catch (IOException ex) {
      }
    }

    return secrets;
  }

  /**
   * Opens the secrets file using the password retrieved from the user. This
   * function is called only for backward compatibility purposes, when Secrets
   * encounters an error trying to load the secrets using the current encryption
   * method.
   *
   * V3 used the current cipher mechanism and the old object file format.
   *
   * @param context
   *          Activity context in which the load is called.
   * @return A list of loaded secrets.
   */
  public static ArrayList<Secret> loadSecretsV3(Context context) {
    synchronized (lock) {
      Log.d(LOG_TAG, "FileUtils.loadSecretsV21: got lock");
      return loadSecretsV3(context, SecurityUtils.getCipherInfo(),
          SECRETS_FILE_NAME);
    }
  }

  /**
   * See previous method for description.
   *
   * @param context
   *          Activity context in which the load is called.
   * @param fileName Name of file to be loaded
   * @param info CipherInfo
   * @return A list of loaded secrets.
   */
  public static ArrayList<Secret> loadSecretsV3(Context context,
      CipherInfo info, String fileName) {
    Log.d(LOG_TAG, "FileUtils.loadSecretsV3");

    if (null == info)
      return null;

    ArrayList<Secret> secrets = null;
    InputStream input = null;

    try {
      input = fileName.startsWith("/")
          ? new FileInputStream(fileName)
          : context.openFileInput(fileName);
      secrets = readSecretsV2(input, info.decryptCipher, info.salt, info.rounds);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "loadSecretsV3", ex);
    } finally {
      try {
        if (null != input)
          input.close();
      } catch (IOException ex) {
      }
    }
    Log.d(LOG_TAG, "FileUtils.loadSecretsv3: done");
    return secrets;
  }

  /* end new load/restore methods */

  /**
   * Writes the secrets to the given output stream encrypted with the given
   * cipher.
   *
   * The output stream is closed by the caller.
   *
   * @param output The output stream to write the secrets to.
   * @param cipher The cipher to encrypt the secrets with.
   * @param secrets The secrets to write.
   * @param rounds The number of rounds for bcrypt.
   * @throws IOException
   */
  private static void writeSecrets(OutputStream output,
                                   Cipher cipher,
                                   byte[] salt,
                                   int rounds,
                                   ArrayList<Secret> secrets) throws IOException {
    output.write(SIGNATURE);
    output.write(salt.length);
    output.write(salt);
    output.write(rounds);
    output.write(FileUtils.toEncryptedJSONSecretsStream(cipher, secrets));
  	output.flush();
  }

  /**
   * Read the secrets from the given input stream, decrypting with the given
   * cipher.
   *
   * @param input
   *          The input stream to read the secrets from.
   * @param cipher
   *          The cipher to decrypt the secrets with.
   * @return The secrets read from the stream.
   * @throws IOException
   * @throws ClassNotFoundException
   */
  private static ArrayList<Secret> readSecrets(InputStream input,
                                               Cipher cipher, byte[] salt,
                                               int rounds) throws IOException,
      ClassNotFoundException {
    SaltAndRounds pair = getSaltAndRounds(input);
    if (!Arrays.equals(pair.salt, salt) || pair.rounds != rounds) {
      return null;
    }
    BufferedInputStream bis = new BufferedInputStream(input);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try {
      // read the whole stream into the buffer
      int nRead;
      byte[] data = new byte[4096];
      while ((nRead = bis.read(data, 0, data.length)) != -1) {
        buffer.write(data, 0, nRead);
      }
      buffer.flush();
      return FileUtils.fromEncryptedJSONSecretsStream(cipher,
              buffer.toByteArray());
    } finally {
      try {
        bis.close();
      } catch (IOException ex) {
      }
    }
  }

  /**
   * Read the secrets from the given input stream, decrypting with the given
   * cipher. This uses the old object format and exists for compatibility.
   *
   * @param input The input stream to read the secrets from.
   * @param cipher The cipher to decrypt the secrets with.
   * @return The secrets read from the stream.
   * @throws IOException
   * @throws ClassNotFoundException
   */
  @SuppressWarnings("unchecked")
  private static ArrayList<Secret> readSecretsV2(InputStream input,
                                               Cipher cipher,
                                               byte[] salt,
                                               int rounds)
      throws IOException, ClassNotFoundException {
    SaltAndRounds pair = getSaltAndRounds(input);
    if (!Arrays.equals(pair.salt, salt) || pair.rounds != rounds) {
      return null;
    }
    ObjectInputStream oin = new ObjectInputStream(
        new CipherInputStream(input, cipher));
    try {
      return (ArrayList<Secret>)oin.readObject();
    } finally {
      try {oin.close();} catch (IOException ex) {}
    }
  }

  /**
   * Returns an json object representing the contained secrets.
   *
   * @param secrets
   *          The list of secrets.
   * @return String of secrets
   * @throws JSONException
   */
  public static JSONObject toJSONSecrets(ArrayList<Secret> secrets) throws JSONException {
    JSONObject jsonValues = new JSONObject();
    JSONArray jsonSecrets = new JSONArray();
    for (Secret secret : secrets) {
      jsonSecrets.put(secret.toJSON());
    }
    jsonValues.put(JSON_SECRETS_ID, jsonSecrets);

    return jsonValues;
  }

  /**
   * Constructs a secrets collection from the supplied JSON object
   *
   * @param jsonValues
   *          JSON object
   * @return list of secrets
   * @throws JSONException
   *           if error with JSON data
   */
  public static ArrayList<Secret> fromJSONSecrets(JSONObject jsonValues)
      throws JSONException {
    JSONArray jsonSecrets = jsonValues.getJSONArray(JSON_SECRETS_ID);
    ArrayList<Secret> secretList = new ArrayList<Secret>();
    for (int i = 0; i < jsonSecrets.length(); i++) {
      secretList.add(Secret.fromJSON((JSONObject) jsonSecrets.get(i)));
    }

    return secretList;
  }

  /**
   * Returns an encrypted json stream representing the user's secrets.
   *
   * @param cipher
   *          The encryption cipher to use with the file.
   * @param secrets
   *          The list of secrets.
   * @return byte array of secrets
   * @throws IOException
   *           if any error occurs
   */
  public static byte[] toEncryptedJSONSecretsStream(Cipher cipher,
      ArrayList<Secret> secrets) throws IOException {
    CipherOutputStream output = null;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    try {
      output = new CipherOutputStream(baos, cipher);
      output.write(FileUtils.toJSONSecrets(secrets).toString().getBytes(
          StandardCharsets.UTF_8));
    } catch (Exception e) {
      Log.e(LOG_TAG, "toEncryptedJSONSecretsStream", e);
      throw new IOException("toEncryptedJSONSecretsStream failed: " + e.getMessage());
    } finally {
      try { if (null != output) output.close(); } catch (IOException ex) {}
    }

    return baos.toByteArray();
  }

  /**
   * Constructs secrets from the supplied encrypted byte stream
   *
   * @param cipher
   *          cipher to use
   * @param secrets
   *          encrypted byte array
   * @return list of secrets
   * @throws IOException
   *           if any error occurs
   */
  public static ArrayList<Secret> fromEncryptedJSONSecretsStream(Cipher cipher,
      byte[] secrets) throws IOException {
    try {
      byte[] secretStrBytes = cipher.doFinal(secrets);
      JSONObject jsonValues =
          new JSONObject(new String(secretStrBytes, StandardCharsets.UTF_8));
      return FileUtils.fromJSONSecrets(jsonValues);
    } catch (Exception e) {
      Log.e(LOG_TAG, "fromEncryptedJSONSecretsStream", e);
      throw
          new IOException("fromEncryptedJSONSecretsStream failed: " + e.getMessage());
    }
  }

  /** Deletes all secrets from the phone.
   * @param context the current context
   * @return always true
   */
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
   * @param context the current context
   * @param secrets the secrets to export
   * @return true if successful, false otherwise
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
   * @return the file to import
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
          secret.setPassword(row[4], false);
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
          secret.setPassword(row[2], false);
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
    return headers[0].equalsIgnoreCase("Category") &&
        headers[1].equalsIgnoreCase("Description") &&
        headers[2].equalsIgnoreCase("Website") &&
        headers[3].equalsIgnoreCase("Username") &&
        headers[4].equalsIgnoreCase("Password") &&
        headers[5].equalsIgnoreCase("Notes");

  }

  /** Is it likely that the CSV file is in secrets format? */
  private static boolean isSecretsCsv(String[] headers) {
    return headers[0].equalsIgnoreCase(COL_DESCRIPTION) &&
        headers[1].equalsIgnoreCase(COL_USERNAME) &&
        headers[2].equalsIgnoreCase(COL_PASSWORD) &&
        headers[3].equalsIgnoreCase(COL_EMAIL) &&
        headers[4].equalsIgnoreCase(COL_NOTES);

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
    public static final String LOG_TAG_AGENT = "SecretsBackupAgent";

    /** Key in backup set for file data. */
    private static final String KEY ="file";

    @Override
    public void onCreate() {
      Log.d(LOG_TAG_AGENT, "onCreate");

      FileBackupHelper helper = new FileBackupHelper(this,
          FileUtils.SECRETS_FILE_NAME);
      addHelper(KEY, helper);
    }

    @Override
    public void onBackup(ParcelFileDescriptor oldState,
                         BackupDataOutput data,
                         ParcelFileDescriptor newState) throws IOException {
      Log.d(LOG_TAG_AGENT, "onBackup");
      synchronized (lock) {
        super.onBackup(oldState, data, newState);
      }
      getSharedPreferences(PREFS_FILE_NAME, 0).edit()
          .putLong(PREF_LAST_BACKUP_DATE, System.currentTimeMillis()).apply();
    }

    @Override
    public void onRestore(BackupDataInput data,
                          int appVersionCode,
                          ParcelFileDescriptor newState)  throws IOException {
      Log.d(LOG_TAG_AGENT, "onRestore");
      synchronized (lock) {
        super.onRestore(data, appVersionCode, newState);
      }
    }

    @Override
    public void onFullBackup(FullBackupDataOutput data) throws IOException {
      super.onFullBackup(data);
      getSharedPreferences(PREFS_FILE_NAME, 0).edit()
          .putLong(PREF_LAST_BACKUP_DATE, System.currentTimeMillis()).apply();
    }

    @Override
    public void onDestroy() {
      Log.d(LOG_TAG_AGENT, "onDestroy");
      super.onDestroy();
    }
  }
}
