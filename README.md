# SchedJoules SDK

__Android SDK for SchedJoules services__

## The provider

The API interface is implemented as a content provider. There is some [JavaDoc](https://rawgithub.com/schedjoules/Android-SDK/master/javadoc/index.html) that describes the contract used by the interface.


### Content structure

The SchedJoules API is structured like a tree. There are *nodes* (i.e. *pages*) that can contain other nodes and leafs and there are *leafs* (i.e. *calendars*) that can not contain any other elements.
Pages don't directly contain the other pages and calendars, instead they contain one or multiple *sections* that contain other pages and calendars.

To start with a page you always need the sections on that page. You can retrieve the sections by using the `Uri` returned by `CalendarContentContract.ContentItem.getSectionContentUri(context, id)` where `id` is the internal content item id. To retrieve the start page just pass id `0`.

Having loaded the sections on a page you can reteive all items in that section by querying the `Uri` returned by `CalendarContentContract.Section.getItemContentUri(getActivity(), sectionId)` where `sectionId` is one of the row ids returned by the previous `Uri`.

## The library

Make sure you copy `schedjoules-sdk.jar` into your `libs` directory to use the SDK.

## Resources

The SDK needs a few resources to be set by the implementor.

### [`schedjoules_constants.xml`](sdk/res/values/schedjoules_constants.xml)

This file needs to be imported as it is. Do not modify this file.

### [`schedjoules_sdk.xml`](sdk/res/values/schedjoules_sdk.xml)

This file contains a couple of values that need to be adjusted by the implementor. See the inline documentation for details.

### [`schedjoules_syncadapter.xml`](sdk/res/xml/schedjoules_syncadapter.xml)

This is the sync adapter definition file. Include it as it is and do not modify it!

### [`schedjoules_authenticator.xml`](sdk/res/xml/schedjoules_authenticator.xml)

This is the authenticator definition file. The only values you may have to update are `android:icon` and `android:smallIcon` which must point to an icon (and a small version of it) to be shown in the settings.
Usually you set this to your app icon.

## AndroidManifest

You need to include a few tags in your `AndroidManifest.xml` to advertize authenticator, sync service and content provider. Note that you need to update the package name in the authority name of the provider.

This is the basic Manifest file:

```
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="YOUR_PACKAGE_NAME"
    android:installLocation="internalOnly"
    android:versionCode="1"
    android:versionName="0.1" >

    <uses-sdk
        android:minSdkVersion="14"
        android:targetSdkVersion="19" />

    <uses-permission android:name="android.permission.INTERNET" />  <!-- required to access SchedJoules API services and the calendar data -->
    <uses-permission android:name="com.android.vending.BILLING" />  <!-- required to to in-app purchases -->
    <uses-permission android:name="android.permission.GET_ACCOUNTS" />  <!-- required to check if the account this app maintains is already present -->
    <uses-permission android:name="android.permission.USE_CREDENTIALS" />  <!-- required to use the authenticator properly -->
    <uses-permission android:name="android.permission.MANAGE_ACCOUNTS" />  <!-- required to create the account -->
    <uses-permission android:name="android.permission.AUTHENTICATE_ACCOUNTS" />  <!-- required to act as an authenticator -->
    <uses-permission android:name="android.permission.READ_CALENDAR" />  <!-- required to sync calendars -->
    <uses-permission android:name="android.permission.WRITE_CALENDAR" />  <!-- required to sync calendars -->
    <uses-permission android:name="android.permission.READ_SYNC_STATS" />  <!-- required to read the sync status (syncing, pending, idle ...)  -->
    <uses-permission android:name="android.permission.READ_SYNC_SETTINGS" />  <!-- required to check if syncing is enabled for this account -->
    <uses-permission android:name="android.permission.WRITE_SYNC_SETTINGS" />  <!-- required to enable syncing for the account -->
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />  <!-- required to check the account and calendars on boot -->
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />  <!-- required to check network connectivity during sync -->

    <application
        android:allowBackup="false"
        android:icon="@drawable/ic_launcher"
        android:label="@string/app_name"
        android:theme="@style/AppTheme" >


	<!-- Your activities, providers, services, receivers go here -->



	<!-- The entries below are required by the SDK -->

	<!--
        This should be present to act properly when the user chooses tp create a new account.

        The Activity should inherit from android.accounts.AccountAuthenticatorActivity and call your main Activity.
        -->
        <activity
            android:name=".DummyAuthenticatorActivity"
            android:label="" >
            <intent-filter>
                <action android:name="org.dmfs.android.authenticator.action.ADD_ACCOUNT" />

                <category android:name="android.intent.category.DEFAULT" />
            </intent-filter>
        </activity>

        <!-- The sync service that does all the syncing -->
        <service
            android:name="org.dmfs.caldav.syncadapter.SyncService"
            android:exported="true"
            android:process=":sync" >
            <intent-filter>
                <action android:name="android.content.SyncAdapter" />
            </intent-filter>

            <meta-data
                android:name="android.content.SyncAdapter"
                android:resource="@xml/schedjoules_syncadapter" />
        </service>

        <!-- The authenticator service. This is required to manage calendars. Without it all calendars would be removed. --> 
        <service
            android:name="org.dmfs.android.authenticator.AuthenticationService"
            android:exported="true"
            android:process=":auth" >
            <intent-filter>
                <action android:name="android.accounts.AccountAuthenticator" />
            </intent-filter>

            <meta-data
                android:name="android.accounts.AccountAuthenticator"
                android:resource="@xml/schedjoules_authenticator" />
        </service>

	<!--
	The API content provider. Insert your package name accordingly. The authority name must be your package name followed by ".calendarcontentprovider"
	
        You can enable multiprocess on API level 16 or later. Do not enable it on API levels below 16. These versions are broken and will crash.
	-->
        <provider
            android:name="org.dmfs.android.calendarcontent.provider.CalendarContentProvider"
            android:authorities="YOUR.PACKAGE.NAME.calendarcontentprovider"
            android:enabled="true"
            android:exported="false"
            android:multiprocess="false"
            android:process=":provider" >
        </provider>


	<!-- This is required to check the account and all calendars on boot and recreate them if necessary -->
        <receiver android:name="org.dmfs.android.calendarcontent.provider.OnBootReceiver" >
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
                <action android:name="android.intent.action.QUICKBOOT_POWERON" />
            </intent-filter>
        </receiver>
    </application>
</manifest>

```
