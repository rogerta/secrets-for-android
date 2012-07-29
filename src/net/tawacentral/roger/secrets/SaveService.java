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

import javax.crypto.Cipher;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

/**
 * A background service to save the secrets to a file.  This is done as a
 * service to that the OS keeps the process around until the save is completed.
 *
 * Note that a service runs in the main thread of the process, just like the
 * UI activities, so any lengthy task still needs to be performed in a
 * separate thread.
 * 
 * @author rogerta
 */
public class SaveService extends Service {
  private static SecretsCollection secrets;
  private static Cipher cipher;
  private static byte[] salt;
  private static int rounds;

  /** Backup manager for Android 2.2. */
  Object backupManager;

  /**
   * Queue a background save of the secrets.
   * 
   * @param context The activity requesting the save.
   * @param secrets The collection of secrets to save.
   * @param cipher The encryption cipher.
   * @param salt The salt used to create the cipher.
   * @param rounds The number of rounds for bcrypt.
   */
  public static synchronized void execute(Context context,
                                          SecretsCollection secrets,
                                          Cipher cipher,
                                          byte[] salt,
                                          int rounds) {
    SaveService.secrets = secrets;
    SaveService.cipher = cipher;
    SaveService.salt = salt;
    SaveService.rounds = rounds;

    Intent intent = new Intent(context, SaveService.class);
    context.startService(intent);
  }

  /**
   * Constructor
   */
  public SaveService() {}

  @Override
  public IBinder onBind(Intent arg0) {
    return null;
  }

  @Override
  public void onCreate() {
    super.onCreate();

    // Create a backup manager.  Will be null for versions of Android before
    // 2.2.
    backupManager = OS.createBackupManager(this);
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }

  @Override
  public void onStart(Intent intent, final int startId) {
    super.onStart(intent, startId);

    synchronized (SaveService.class) {
      final SecretsCollection secrets = SaveService.secrets;
      final Cipher cipher = SaveService.cipher;
      final File file = getFileStreamPath(FileUtils.SECRETS_FILE_NAME);
      final byte[] salt = SaveService.salt;
      final int rounds = SaveService.rounds;

      SaveService.secrets = null;
      SaveService.cipher = null;
      SaveService.salt = null;
      SaveService.rounds = 0;

      if (null != secrets && null != cipher) {
        new Thread(new Runnable() {
          @Override
          public void run() {
            int r = FileUtils.saveSecrets(SaveService.this, file, cipher,
                                          salt, rounds, secrets);

            // If the save was successful, schedule a backup. 
            if (0 == r)
              OS.backupManagerDataChanged(backupManager);

            // If no SD card backup exists, save it now.
            if (!FileUtils.restoreFileExist())
              FileUtils.backupSecrets(SaveService.this, cipher, salt, rounds,
                                      secrets);

            stopSelf(startId);
          }}, "saveSecrets").start();
      } else {
        stopSelf(startId);
      }
    }
  }
}
