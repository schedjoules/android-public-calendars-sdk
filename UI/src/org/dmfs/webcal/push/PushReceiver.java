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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.google.android.gms.gcm.GoogleCloudMessaging;


/**
 * This broadcast receiver receives the push notification broadcast. If a push message has been received it calls
 * {@link PushHelperService#handlePushMessage(Context, android.os.Bundle)} to handle the message asynchronously.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class PushReceiver extends BroadcastReceiver
{

	@Override
	public void onReceive(Context context, Intent intent)
	{
		GoogleCloudMessaging gcm = GoogleCloudMessaging.getInstance(context);
		String messageType = gcm.getMessageType(intent);
		if (GoogleCloudMessaging.MESSAGE_TYPE_MESSAGE.equals(messageType))
		{
			PushHelperService.handlePushMessage(context, intent.getExtras());
		}
		setResultCode(Activity.RESULT_OK);
	}

}
