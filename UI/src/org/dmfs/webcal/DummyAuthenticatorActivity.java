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

package org.dmfs.webcal;

import android.accounts.AccountAuthenticatorActivity;
import android.content.Intent;
import android.os.Bundle;


/**
 * An activity that serves as AuthenticatorActivity. It just launches {@link MainActivity} and finishes. We need it to satisfy Android when the users attempts
 * to create a new account from the device settings.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class DummyAuthenticatorActivity extends AccountAuthenticatorActivity
{
	@Override
	protected void onCreate(Bundle icicle)
	{
		super.onCreate(icicle);

		Intent intent = new Intent(this, MainActivity.class);
		startActivity(intent);
		setResult(RESULT_OK);

		finish();
	}
}
