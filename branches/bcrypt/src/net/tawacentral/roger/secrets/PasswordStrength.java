// Copyright (c) 2011, Google Inc.
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

import android.graphics.Color;

/**
 * This enum contains a static method that generates password strength scores
 * for string passwords.
 *
 * The strength method implements the Truong3 Password Strength Algorithm, which
 * determines if the password contains:
 * . at least 6 characters
 * . at least one upper and one lower case Latin alphabet character
 * . at least one numerical character
 * . at least one special character
 *
 * This enum provides a couple methods related to UI for retrieving a
 * descriptive String and Color associated with the strength.
 *
 * @author stevet
 */
enum PasswordStrength {
  WEAK(R.string.password_strength_weak, Color.RED),
  MEDIUM(R.string.password_strength_medium,
         Color.argb(255, 220, 185, 0)),  // Orange
  STRONG(R.string.password_strength_strong, Color.YELLOW),
  VERY_STRONG(R.string.password_strength_very_strong,
              Color.argb(255, 170, 255, 0)),  // Chartreuse
  CRAZY_STRONG(R.string.password_strength_crazy_strong, Color.GREEN);

  PasswordStrength(int resId, int color) {
    this.resId = resId;
    this.color = color;
  }

  /**
   * Returns the text for this password strength.
   */
  CharSequence getText(android.content.Context ctx) {
    return ctx.getText(resId);
  }

  /**
   * Returns the color associated with this password strength.
   */
  int getColor() {
    return color;
  }

  /**
   * Static helper for calculating a password strength from scratch.
   * Runtime: O(N).
   */
  static PasswordStrength calculateStrength(String password) {
    int currentScore = 0;
    boolean sawUpper = false;
    boolean sawLower = false;
    boolean sawDigit = false;
    boolean sawSpecial = false;

    // The first time the length passes 6, we increment the score.
    if (password.length() > 6)
      currentScore += 1;

    // Do this as efficiently as possible.
    for (int i = 0; i < password.length(); i++) {
      char c = password.charAt(i);
      if (!sawSpecial && !Character.isLetterOrDigit(c)) {
        currentScore += 1;
        sawSpecial = true;
      } else {
        if (!sawDigit && Character.isDigit(c)) {
          currentScore += 1;
          sawDigit = true;
        } else {
          if (!sawUpper || !sawLower) {
            if (Character.isUpperCase(c))
              sawUpper = true;
            else
              sawLower = true;
            if (sawUpper && sawLower)
              currentScore += 1;
          }
        }
      }
    }

    switch (currentScore) {
      case 0: return WEAK;
      case 1: return MEDIUM;
      case 2: return STRONG;
      case 3: return VERY_STRONG;
      case 4: return CRAZY_STRONG;
      default:  // Fall through.
    }
    // This shouldn't happen with this particular algorithm, but if it does,
    // that means we have a score over 4, so that's good, right?
    // TODO(stevet): assert instead.
    return CRAZY_STRONG;
  }

  int resId;
  int color;
}
