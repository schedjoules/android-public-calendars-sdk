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

package org.dmfs.webcal.utils;

import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;


/**
 * An {@link AsyncTask} that starts an empty service during it's runtime to ensure it's not killed during it's operation.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 * 
 * @param <T>
 * @param <R>
 */
public abstract class ProtectedBackgroundJob<T, R> extends AsyncTask<T, Void, R>
{
	private final Context mApplicationContext;


	public ProtectedBackgroundJob(Context context)
	{
		mApplicationContext = context.getApplicationContext();
	}


	@Override
	protected final void onPreExecute()
	{
		mApplicationContext.startService(new Intent(mApplicationContext, EmptyService.class));
	}


	protected abstract void doPostExecute(R result);


	@Override
	protected final void onPostExecute(R result)
	{
		doPostExecute(result);
		mApplicationContext.stopService(new Intent(mApplicationContext, EmptyService.class));
	}
}
