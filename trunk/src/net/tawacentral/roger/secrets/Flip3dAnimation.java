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

import android.graphics.Camera;
import android.graphics.Matrix;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * Performs a flip animation between two views.  This implementation is highly
 * inspired by the 3D transition sample in the android SDK, but corrects a
 * huge bug where the destination view remains flipped 180 degree!  This is
 * not apparent when the destination is a simple image.  Also, this class is
 * more easily reusable, but less customizable by the caller.
 *
 * The following restrictions apply:
 * <ul>
 * <li>The view that this animation is applied to must be the parent of the
 *     two views specified in the constructor
 * <li>The two views specified in the constructor should be siblings, and
 *     direct children of the view to which the animation is applied
 * </ul>
 *
 * The animation is a rotation of the first view, until it reaches 90 degrees,
 * at which point the rotation continues but with the second view.  The views
 * also move backwards slightly and them come forward into position again.
 * The net effect is as if you flipped a sheet of paper, where each side
 * shows one of the views.  Its a very similar effect to editing the properties
 * of a Mac dashboard widget. 
 * 
 * @author rogerta
 */
public class Flip3dAnimation extends Animation {
  private Camera camera;
  private View view1;
  private View view2;
  private float centerX;
  private float centerY;
  private boolean forward;
  private boolean visibilitySwapped;

  /**
   * Creates a 3D flip animation between two views.  If forward is true, its
   * assumed that view1 is "visible" and view2 is "gone" before the animation
   * starts.  At the end of the animation, view1 will be "gone" and view2 will
   * be "visible".  If forward is false, the reverse is assumed.
   * 
   * @param view1 First view in the transition.
   * @param view2 Second view in the transition.
   * @param centerX The center of the views in the x-axis.
   * @param centerY The center of the views in the y-axis.
   * @param forward The direction of the animation.
   */
  Flip3dAnimation(View view1, View view2, int centerX, int centerY,
                  boolean forward) {
    this.view1 = view1;
    this.view2 = view2;
    this.centerX = centerX;
    this.centerY = centerY;
    this.forward = forward;
    
    setDuration(1000);
    setFillAfter(true);
    setInterpolator(new AccelerateDecelerateInterpolator());
  }
  
  @Override
  public void initialize(int width, int height, int parentWidth,
                         int parentHeight) {
    super.initialize(width, height, parentWidth, parentHeight);
    
    camera = new Camera();
  }

  @Override
  protected void applyTransformation(float interpolatedTime, Transformation t) {
    // Angle around the y-axis of the rotation at the given time.  It is
    // calculated both in radians and in the equivalent degrees.
    final double radians = Math.PI * interpolatedTime;
    float degrees = (float)(180.0 * radians / Math.PI);

    // Once we reach the midpoint in the animation, we need to hide the
    // source view and show the destination view.  We also need to change
    // the angle by 180 degrees so that the destination does not come in
    // flipped around.  This is the main problem with SDK sample, it does not
    // do this.
    if (interpolatedTime >= 0.5f) {
      degrees -= 180.f;
      
      if (!visibilitySwapped) {
        if (forward) {
          view1.setVisibility(View.GONE);
          view2.setVisibility(View.VISIBLE);
        } else {
          view2.setVisibility(View.GONE);
          view1.setVisibility(View.VISIBLE);
        }
        
        visibilitySwapped = true;
      }
    }
    
    if (!forward)
      degrees = -degrees;
    
    final Matrix matrix = t.getMatrix();

    camera.save();
    camera.translate(0.0f, 0.0f, (float)(310.0 * Math.sin(radians)));
    camera.rotateY(degrees);
    camera.getMatrix(matrix);
    camera.restore();

    matrix.preTranslate(-centerX, -centerY);
    matrix.postTranslate(centerX, centerY);
  }
}
