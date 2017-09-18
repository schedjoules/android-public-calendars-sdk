/*
 * Copyright (C) 2014 SchedJoules
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * 
 */

package org.dmfs.webcal.push;

import android.app.IntentService;
import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.NotificationManagerCompat;
import android.text.TextUtils;

import org.dmfs.android.xmlmagic.XmlLoader;
import org.dmfs.android.xmlmagic.tokenresolvers.BundleTokenResolver;
import org.dmfs.webcal.R;
import org.dmfs.xmlobjects.pull.XmlObjectPullParserException;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;


/**
 * Handles any incoming push messages.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class PushHelperService extends IntentService
{
    private final static String EXTRA_COMMAND = "org.dmfs.webcal.push.COMMAND";
    private final static String EXTRA_PUSH_MESSAGE = "org.dmfs.webcal.push.PUSH_MESSAGE";

    private final static String COMMAND_HANDLE_PUSH = "HANDLE_PUSH";


    /**
     * Static helper method to handle a push message. It launches the {@link PushHelperService} with the right parameters.
     *
     * @param context
     *         A {@link Context}.
     * @param message
     *         The push message data.
     */
    public static void handlePushMessage(Context context, Bundle message)
    {
        Intent addIntent = new Intent(context, PushHelperService.class);
        addIntent.putExtra(EXTRA_COMMAND, COMMAND_HANDLE_PUSH);
        addIntent.putExtra(EXTRA_PUSH_MESSAGE, message);
        context.startService(addIntent);
    }


    public PushHelperService()
    {
        super("PushHelperService");
    }


    @Override
    protected void onHandleIntent(Intent intent)
    {
        String command = intent.getStringExtra(EXTRA_COMMAND);

        if (COMMAND_HANDLE_PUSH.equals(command))
        {
            Bundle message = intent.getParcelableExtra(EXTRA_PUSH_MESSAGE);

            if (message == null || discardMessage(message))
            {
                return;
            }

            try
            {
                NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
                Notification notification = XmlLoader.loadNotification(this, R.xml.schedjoules_notification, new BundleTokenResolver(message));
                notificationManager.notify(1, notification);
            }
            catch (IOException | XmlPullParserException | XmlObjectPullParserException e)
            {
                // ignore
            }
        }
    }


    /**
     * Checks whether the message should be discarded or shown.
     *
     * @param message
     *         The message
     *
     * @return <code>true</code> if the message should be discarded, <code>false</code> if it should be presented to the user.
     */
    private boolean discardMessage(Bundle message)
    {
        try
        {
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;

            if (message.containsKey("target-version") && versionCode != toInt(message.getString("target-version"), 0))
            {
                // this is directed to a different app version
                return true;
            }

            if (message.containsKey("max-target-version") && versionCode > toInt(message.getString("max-target-version"), Integer.MAX_VALUE))
            {
                // this is directed to a different app version
                return true;
            }

            if (message.containsKey("min-target-version") && versionCode < toInt(message.getString("min-target-version"), 0))
            {
                // this is directed to a different app version
                return true;
            }

            if (message.containsKey("show") && !Boolean.parseBoolean(message.getString("show")))
            {
                return true;
            }

            return TextUtils.isEmpty(message.getString("title")) || TextUtils.isEmpty(message.getString("message"));
        }
        catch (NameNotFoundException e)
        {
            // this should be impossible
        }

        return false;
    }


    /**
     * Convert the given {@link String} to an int with a fallback value if the conversion fails.
     *
     * @param string
     *         The String to convert.
     * @param defaultValue
     *         The value to return if the string didn't contain an int.
     *
     * @return Either the converted value or the default value.
     */
    private int toInt(String string, int defaultValue)
    {
        try
        {
            return Integer.parseInt(string);
        }
        catch (NumberFormatException e)
        {
            return defaultValue;
        }
    }

}
