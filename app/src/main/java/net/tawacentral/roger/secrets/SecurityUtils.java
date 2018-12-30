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

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.spec.AlgorithmParameterSpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.PBEParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.mindrot.jbcrypt.BCrypt;

import android.util.Log;

/**
 * Helper class to manage cipher keys and encrypting and decrypting data.
 *
 * @author rogerta
 */
public class SecurityUtils {
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "Secrets";

  /** Return value of createCiphers call. */
  public static class CipherInfo {
    public Cipher encryptCipher;
    public Cipher decryptCipher;
    public byte[] salt;
    public int rounds;
  }

  // The following three constants were used with the initial implementation of
  // secrets.  Secrets now uses a more secure algorithm, but for backwards
  // compatibility, the program needs to be able to load the secrets with the
  // old algorithm.  However, secrets will only be saved with the new algorithm
  // moving forward.
  //
  // All members that end with V1 or v1 have to do with the old algorithm. 
  private static final String KEY_FACTORY_V1 =
	  "PBEWITHSHA-256AND256BITAES-CBC-BC";

  // TODO(rogerta): for now, I'm using an iteration count of 100.  I had
  // initially set it to 5000, but that took *ages* to create the cipher.  I
  // need to figure out if this is a safe value to use.
  private static final int KEY_ITERATION_COUNT_V1 = 100;

  /* (ChrisW) The comments below refer to the original implementation. The
   * salt is now generated. A random salt is more secure than a fixed one
   * because it prevents a decryption attack using a pre-generated dictionary.
   */
  
  // The salt can be hardcoded, because the secrets file is never transmitted
  // off the phone.  Generating a ramdom salt would not provide any real extra
  // protection, because if an attacker can get to the secrets file, then he
  // has broken into the phone, and therefore would be able to get to the
  // random salt too.
  private static final byte[] salt_v1 = {
    (byte)0xA4, (byte)0x0B, (byte)0xC8, (byte)0x34,
    (byte)0xD6, (byte)0x95, (byte)0xF3, (byte)0x13
  };

  // Factory to use for version 2 of encryption.
  private static final String KEY_FACTORY_V2 = "AES";
  private static final String CIPHER_FACTORY_V2 = "AES/CBC/PKCS5Padding";

  private static final String KEY_FACTORY = "AES";
  private static final String CIPHER_FACTORY = "AES/CBC/PKCS5Padding";

  /** Class used to time the execution of functions */
  static public class ExecutionTimer {
    private long start = System.currentTimeMillis();

    /** Returns the time in millisecs since the object was created. */
    public long getElapsed() {
      return System.currentTimeMillis() - start;
    }

    /** Prints the time in millisecs since the object was created to the log. */
    public void logElapsed(String message) {
      Log.d(LOG_TAG, message + getElapsed());
    }
  }

  private static Cipher encryptCipher;
  private static Cipher decryptCipher;
  private static byte[] salt;
  private static int rounds;

  /**
   * Get the cipher used to encrypt data using the password given to the
   * createCiphers() function.
   */
  public static Cipher getEncryptionCipher() {
    return encryptCipher;
  }

  /**
   * Get the cipher used to decrypt data using the password given to the
   * createCiphers() function.
   */
  public static Cipher getDecryptionCipher() {
    return decryptCipher;
  }

  /**
   * Gets the salt for this device.
   * @return A byte array representing the salt for this specific device.
   */
  public static byte[] getSalt() {
    return salt;
  }

  /**
   * Gets the rounds for this device.
   * @return an integer representing the number of bcrypt rounds.
   */
  public static int getRounds() {
    return rounds;
  }
  
  /** Gets information about current ciphers. */
  public static CipherInfo getCipherInfo() {
    CipherInfo info = new CipherInfo();
    info.encryptCipher = encryptCipher;
    info.decryptCipher = decryptCipher;
    info.salt = salt.clone();
    info.rounds = rounds;
    return info;
  }

  /**
   * Creates a new unique random salt.
   * @return A new salt value used to generate the secret key. 
   */
  private static byte[] createNewSalt() {
    byte[] bytes = new byte[BCrypt.BCRYPT_SALT_LEN];
    SecureRandom random = new SecureRandom();
    random.nextBytes(bytes);
    return bytes;
  }
  
  /**
   * Create a decryption cipher using an old algorithm based on the given
   * password string.  The string is not stored internally.
   *
   * This method is used for backward compatibility only.
   * 
   * @param password String to use for creating the ciphers.
   * @return True if the ciphers were successfully created.
   */
  public static Cipher createDecryptionCipherV1(String password) {
    Cipher cipher = null;

    ExecutionTimer timer = new ExecutionTimer();

    try {
      PBEKeySpec keyspec = new PBEKeySpec(password.toCharArray(),
                                          salt_v1,
                                          KEY_ITERATION_COUNT_V1,
                                          32);
      SecretKeyFactory skf = SecretKeyFactory.getInstance(KEY_FACTORY_V1);
      SecretKey key = skf.generateSecret(keyspec);
      AlgorithmParameterSpec aps = new PBEParameterSpec(salt_v1,
                                                        KEY_ITERATION_COUNT_V1);
      cipher = Cipher.getInstance(KEY_FACTORY_V1);
      cipher.init(Cipher.DECRYPT_MODE, key, aps);
    } catch (Exception ex) {
      Log.d(LOG_TAG, "createDecryptionCiphersV1", ex);
      cipher = null;
    }

    timer.logElapsed("Time to create V1 d-cipher: ");
    return cipher;
  }

  /**
   * Create a decryption cipher using an old algorithm based on the given
   * password string.  The string is not stored internally.
   *
   * This method is used for backward compatibility only.
   * 
   * @param password String to use for creating the ciphers.
   * @param salt The salt to use when creating the encryption key.
   * @param rounds The number of rounds for bcrypt.
   * @return True if the ciphers were successfully created.
   */
  public static Cipher createDecryptionCipherV2(String password,
                                                byte[] salt,
                                                int rounds) {
    if (salt == null || rounds == 0)
      return null;

    Cipher cipher = null;

    try {
      int plaintext[] = {0x155cbf8e, 0x57f57513, 0x3da787b9, 0x71679d82,
                         0x7cf72e93, 0x1ae25274, 0x64b54adc, 0x335cbd0b};
      BCrypt bcrypt = new BCrypt();
      byte[] rawBytes = bcrypt.crypt_raw(password.getBytes(
          StandardCharsets.UTF_8), salt,
                                         rounds, plaintext);
      SecretKeySpec spec = new SecretKeySpec(rawBytes, KEY_FACTORY_V2);

      // For backwards compatibility with secrets create on Android M and
      // earlier, create an initial vector of all zeros.
      IvParameterSpec params = new IvParameterSpec(new byte[16]);

      cipher = Cipher.getInstance(CIPHER_FACTORY_V2);
      cipher.init(Cipher.DECRYPT_MODE, spec, params);
    } catch (Exception ex) {
      Log.d(LOG_TAG, "createCiphersV2", ex);
    }

    return cipher;
  }

  /**
   * Create a pair of encryption and decryption ciphers based on the given
   * password string.  The string is not stored internally.  This function
   * needs to be called before calling getEncryptionCipher() or
   * getDecryptionCipher().
   *
   * @param password String to use for creating the ciphers.
   * @param salt The salt to use when creating the encryption key.
   * @param rounds The number of rounds for bcrypt.
   * @return CipherInfo structure with information about the created ciphers.
   */
  public static CipherInfo createCiphers(String password,
                                         byte[] salt,
                                         int rounds) {
    CipherInfo info = new CipherInfo();

    ExecutionTimer timer = new ExecutionTimer();
    
    try {
      // Append a null at the end of the password string to prevent multiple
      // repetitions of the password from being valid.
      password += '\000';

      if (salt == null || rounds == 0) {
        salt = createNewSalt();
        rounds = determineBestRounds();
      }

      int plaintext[] = {0x155cbf8e, 0x57f57513, 0x3da787b9, 0x71679d82,
                         0x7cf72e93, 0x1ae25274, 0x64b54adc, 0x335cbd0b};
      BCrypt bcrypt = new BCrypt();
      byte[] rawBytes = bcrypt.crypt_raw(password.getBytes(
          StandardCharsets.UTF_8),
                                         salt, rounds, plaintext);
      SecretKeySpec spec = new SecretKeySpec(rawBytes, KEY_FACTORY);

      // For backwards compatibility with secrets create on Android M and
      // earlier, create an initial vector of all zeros.
      IvParameterSpec params = new IvParameterSpec(new byte[16]);

      info.encryptCipher = Cipher.getInstance(CIPHER_FACTORY);
      info.encryptCipher.init(Cipher.ENCRYPT_MODE, spec, params);

      info.decryptCipher = Cipher.getInstance(CIPHER_FACTORY);
      info.decryptCipher.init(Cipher.DECRYPT_MODE, spec, params);

      info.salt = salt;
      info.rounds = rounds;
    } catch (Exception ex) {
      Log.d(LOG_TAG, "createCiphers", ex);
      info = null;
    }

    timer.logElapsed("Time to create ciphers rounds=" + rounds + ": ");
    return info;
  }

  /**
   * Create a pair of encryption and decryption ciphers based on the given
   * password string.  The string is not stored internally.  This function
   * needs to be called before calling getEncryptionCipher() or
   * getDecryptionCipher().
   *
   * @param info Information about ciphers to save in global variables.
   *
   * @return True if the ciphers were successfully created.
   */
  public static void saveCiphers(CipherInfo info) {
    encryptCipher = info.encryptCipher;
    decryptCipher = info.decryptCipher;
    salt = info.salt.clone();
    rounds = info.rounds;
  }

  /** Clear the ciphers from memory. */
  public static void clearCiphers() {
    decryptCipher = null;
    encryptCipher = null;
    salt = null;
    rounds = 0;
  }

  /**
   * Determines the ideal number of rounds to use for the bcrypt algorithm.
   * More rounds are more secure, but require more time to log into Secrets.
   * This function tries to balance security and convenience.
   *
   * Each round increment doubles the amount of work required by bcrypt to
   * generate a key.  This function assumes that time is proportional to work.
   * So for example, if 4 rounds takes 0.1 seconds to generate a key, 5 rounds
   * will take 0.2 seconds, 6 rounds 0.4 seconds, and so on.  The assumption
   * will be that the key must be generated in less than 0.9 seconds to remain
   * convenient for the user.
   *
   * This function calculate how long it takes to generate a key using 4 rounds
   * on the current device, then determines the maximum number of rounds such
   * that the time to generate will remain below the convenience threshold. 
   */
  public static int determineBestRounds() {
      byte[] salt = createNewSalt();
      int plaintext[] = {0x155cbf8e, 0x57f57513, 0x3da787b9, 0x71679d82,
                         0x7cf72e93, 0x1ae25274, 0x64b54adc, 0x335cbd0b};
      final byte[] password = {1, 2, 3, 4, 5, 6, 7, 8};
      BCrypt bcrypt = new BCrypt();

      // Calculate the time to create a cipher key with 4 rounds, in msecs.
      // Do it twice and take the average.
      final long start = System.currentTimeMillis();
      bcrypt.crypt_raw(password, salt, 4, plaintext);
      bcrypt.crypt_raw(password, salt, 4, plaintext);
      final long T4 = (System.currentTimeMillis() - start) / 2;

      // If T4 is the time in msecs to create the key with 4 rounds, then
      // the time Tn to calculate the key using n rounds (n > 4) is:
      //
      //   Tn = 2^(n - 4) * T4
      //
      // where we want Tn to be less than 900 msecs.  Solving for n gives:
      //
      //   Tn = 2^(n - 4) * T4 < 900
      //   n - 4 + log2(T4) < log2(900)
      //   n < 4 + log2(900) - log2(T4)          -- solve for n
      //   n < 4 + ln(900)/ln(2) - ln(T4)/ln(2)  -- convert to natural logs 
      //
      // The best number of rounds is the floor of n.
      final double n = 4 + (Math.log(900) - Math.log(T4)) / Math.log(2);
      int rounds = (int) n;

      // Make sure rounds does not exceed its valid range.
      if (rounds < 4) {
        rounds = 4;
      } else if (rounds > 31) {
        rounds = 31;
      }

      return rounds;
  }

  /*public static void test_behaviour() {
    byte[] rawBytes = java.security.SecureRandom.getSeed(16);
    String plainText = "{\"secrets\":[]}";

    try {
      SecretKeySpec spec = new SecretKeySpec(rawBytes, "AES");

      // Build encryption cipher and encrypt plaintext.

      Cipher cipherE = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipherE.init(Cipher.ENCRYPT_MODE, spec);

      byte[] encrypted = cipherE.doFinal(plainText.getBytes(
          StandardCharsets.UTF_8));

      // Build decryption cipher and decrypt ciphertext.

      IvParameterSpec ivparams = cipherE.getParameters()
              .getParameterSpec(IvParameterSpec.class);
      byte[] iv = ivparams.getIV();

      Cipher cipherD = Cipher.getInstance("AES/CBC/PKCS5Padding");
      cipherD.init(Cipher.DECRYPT_MODE, spec, new IvParameterSpec(iv));

      byte[] decrypted = cipherD.doFinal(encrypted);
      String plainText2 = new String(decrypted, StandardCharsets.UTF_8);

      // Make sure plaintext == dec(enc(plaintext))

      if (BuildConfig.DEBUG && !plainText.equals(plainText2))
        throw new AssertionError();
    } catch (Exception ex) {
      Log.e(LOG_TAG, "error", ex);
    }
  }*/

  /** This method returns all available services types. */
  /*public static String[] getServiceTypes() {
      java.util.HashSet result = new HashSet();
      // All all providers.
      java.security.Provider[] providers =
          java.security.Security.getProviders();
      for (int i = 0; i < providers.length; ++i) {
          // Get services provided by each provider.
          java.util.Set keys = providers[i].keySet();
          for (Iterator it=keys.iterator(); it.hasNext(); ) {
              String key = (String)it.next();
              key = key.split(" ")[0];
              if (key.startsWith("Alg.Alias.")) {
                  // Strip the alias.
                  key = key.substring(10);
              }
              int ix = key.indexOf('.');
              result.add(key.substring(0, ix));
          }
      }
      return (String[])result.toArray(new String[result.size()]);
  }*/

  /** This method returns the available implementations for a service type. */
  /*public static String[] getCryptoImpls(String serviceType) {
      java.util.HashSet<String> result = new java.util.HashSet<String>();
      // All all providers.
      java.security.Provider[] providers =
          java.security.Security.getProviders();
      for (int i = 0; i < providers.length; ++i) {
          // Get services provided by each provider.
          java.util.Set<Object> keys = providers[i].keySet();
          for (Object o : keys) {
            String key = ((String)o).split(" ")[0];
            if (key.startsWith(serviceType+".")) {
                result.add(key.substring(serviceType.length()+1));
            } else if (key.startsWith("Alg.Alias."+serviceType+".")) {
                // This is an alias
                result.add(key.substring(serviceType.length()+11));
            }
          }
      }
      return (String[])result.toArray(new String[result.size()]);
  }*/
}
