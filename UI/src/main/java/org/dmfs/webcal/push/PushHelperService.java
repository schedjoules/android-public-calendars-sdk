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
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;


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
	 *            A {@link Context}.
	 * @param message
	 *            The push message data.
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

			// TODO: handle the message & show a notification
			Toast.makeText(this, message.getString("message", "no message"), Toast.LENGTH_LONG).show();
		}
	}

}
