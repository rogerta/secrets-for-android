The source tree includes project files for building the Android application with Eclipse.

# Supported Platforms for Android Development #

## Linux-based Environment ##
  * [Ubuntu 10.04 Lucid Lynx](http://www.ubuntu.com/)
  * [Eclipse 3.5.2, Build id: M20100211-1343](http://www.eclipse.org/downloads/)
  * [Android Development Toolkit 8.0.1v201012062107-92219](http://developer.android.com/sdk/eclipse-adt.html)
  * [android-sdk\_r08](http://developer.android.com/sdk/index.html)

## Windows-based Environment ##
  * Microsoft Windows 7
  * [Eclipse Helios Service Release 1, Build id: 20100917-0705](http://www.eclipse.org/downloads/)
  * [Android Development Toolkit 9.0.0.v201101191456-93220](http://developer.android.com/sdk/eclipse-adt.html)
  * [android-sdk\_r09](http://developer.android.com/sdk/index.html)

## Mac OSX Environment ##
  * OSX 10.5.7
  * [Eclipse 3.5.0, Build id: 20090619-0625](http://www.eclipse.org/downloads/)
  * [Android Development Toolkit 8.0.1v201012062107-92219](http://developer.android.com/sdk/eclipse-adt.html)
  * [android-sdk\_r08](http://developer.android.com/sdk/index.html)

Other platforms supported by Eclipse and the Android SDK should also work fine, please let me know.  Note that Secrets now uses some 1.5 specific APIs, so it will no longer build with the 1.1 SDK.

## Building ##

To build Secrets for Android yourself, follow these steps:

  * check out the code as instructed under the "Source" tab above
  * start Eclipse and choose the menu item "File / Import..."
  * in the Import dialog choose "General / Existing Projects into Workspace" in the tree control
  * Press the "Next>" button and follow the instructions

Make sure that the project is set to target JDK 1.6, otherwise the @Override tags will generate compile time errors.  To verify this, do the following:

  * select the secrets project in the Package Explorer
  * choose menu item Project / Properties
  * click on Java Compiler along the left-hand side
  * make sure that the Compiler compliance level is set to 1.6