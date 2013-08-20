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

/**
 * Represents an Online Sync Agent application.
 *
 * @author Chris Wood
 */
public class OnlineSyncAgent {
	private String displayName;
	private String classId;

  /**
   * Constructor
   *
   * @param displayName
   * @param classId
   */
  public OnlineSyncAgent(String displayName, String classId) {
    this.displayName = displayName;
    this.classId = classId;
  }

  /**
   * Get displayName
   *
   * @return the displayName
   */
  public String getDisplayName() {
    return displayName;
  }

  /**
   * Get classId
   *
   * @return the classId
   */
  public String getClassId() {
    return classId;
  }

  @Override
  public String toString() {
    return displayName;
  }
}
