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
      } finally {
        try {if (null != input) input.close();} catch (IOException ex) {}
      }
    }
    
    return secrets;
  }

  /** Deletes all secrets from the phone. */
  public static boolean deleteSecrets(Context context) {
    return context.deleteFile(SECRETS_FILE_NAME);
  }
}
