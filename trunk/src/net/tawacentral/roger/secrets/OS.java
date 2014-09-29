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

import java.lang.reflect.Method;

import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.util.Log;
import android.view.InputDevice;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

/**
 * This class wraps OS-specific APIs behind Java's reflection API so that the
 * application can still run on OSs that don't support the given API.  This
 * class is used as a backward compatibility layer so that I don't need to
 * build different versions of the application for different OSs.
 *
 * The root of the problem is the dalvik class verifier, which rejects classes
 * that depend on system classes or interfaces that are not present on the
 * device, causing the application to force exit.  Instead of statically
 * depending on OS-specific classes, this code uses reflection to dynamically
 * discover if a class is available or not, in order to get around the verifier.
 * This is the "correct" way to support different OS versions.
 *
 * @author rogerta
 *
 */
public class OS {
  /** Tag for logging purposes. */
  public static final String LOG_TAG = "OS";

  /** Does the device support the Honeycomb (Android 3.0) APIs? */
  public static boolean isAndroid30() {
    return android.os.Build.VERSION.SDK_INT >= 11;
  }

  /** Hide the soft keyboard if visible. */
  public static void hideSoftKeyboard(Context ctx, View view) {
    InputMethodManager manager = (InputMethodManager)
        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (null != manager)
      manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }

  /**
   * Show the soft keyboard.
   * Does not seem to work on the emulator.
   */
  public static void showSoftKeyboard(Context ctx, View view) {
    InputMethodManager manager = (InputMethodManager)
        ctx.getSystemService(Context.INPUT_METHOD_SERVICE);
    if (null != manager)
      manager.showSoftInput(view, 0);
  }

  /**
   * Invalidates the option menu so that it is recreated.  This is needed
   * for Honeycomb so that the action bar can be updated when switching to
   * and from editing mode.
   *
   * @param activity The activity containing the option menu.
   */
  public static void invalidateOptionsMenu(Activity activity) {
    if (!isAndroid30())
      return;

    try {
      Method m = activity.getClass().getMethod("invalidateOptionsMenu");
      m.invoke(activity);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "invalidateOptionMenu", ex);
    }
  }

  /**
   * Configures a SearchView if this is Android 3.0 or later.
   *
   * @param activity The activity containing the search view.
   */
  public static void configureSearchView(Activity activity, Menu menu) {
    if (!isAndroid30())
      return;

    try {
      SearchManager sm = (SearchManager) activity.getSystemService(
          Context.SEARCH_SERVICE);
      if (null == sm)
        return;

      Method m = sm.getClass().getMethod("getSearchableInfo",
          android.content.ComponentName.class);
      Object si = m.invoke(sm, activity.getComponentName());

      MenuItem item = menu.findItem(R.id.list_search);
      m = item.getClass().getMethod("getActionView");
      View widget = (View) m.invoke(item);

      m = widget.getClass().getMethod("setSearchableInfo", si.getClass());
      m.invoke(widget, si);
    } catch (Exception ex) {
      Log.e(LOG_TAG, "configureSearchView", ex);
    }
  }

  /** Does the device support a scroll wheel or trackball? */
  public static boolean supportsScrollWheel() {
    int[] ids = InputDevice.getDeviceIds();
    for (int id : ids) {
      InputDevice device = InputDevice.getDevice(id);
      int sources = device.getSources();
      final int sourceTbOrDpad =
          InputDevice.SOURCE_TRACKBALL | InputDevice.SOURCE_DPAD;
      if (0 != (sources & sourceTbOrDpad))
        return true;
    }

    return false;
  }
}
