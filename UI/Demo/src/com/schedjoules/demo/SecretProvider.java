package com.schedjoules.demo;

import org.dmfs.android.calendarcontent.secrets.ISecretProvider;
import org.dmfs.android.calendarcontent.secrets.Secret;
import org.dmfs.android.calendarcontent.secrets.SecurityToken;

import android.content.Context;


public class SecretProvider implements ISecretProvider
{

	/**
	 * The in-app product license key. You get it from your developer console when you open an app and go to "Services & API".
	 *
	 * Make sure you obfuscate this key. It should not appear in plain text in your app package. Consider to use DexGuard.
	 */
	private final static String LICENSE_KEY = "Your play store public key";

	/**
	 * Your SchedJoules API token. Don't use the test key below in production!
	 *
	 * Make sure you obfuscate this key. It should not appear in plain text in your app package. Consider to use DexGuard.
	 */
	private final static String API_TOKEN = "0443a55244bb2b6224fd48e0416f0d9c";


	@Override
	public Secret getSecret(Context context, String key, SecurityToken keyFragment)
	{
		if (KEY_LICENSE_KEY.equals(key))
		{
			return new Secret(context, keyFragment, LICENSE_KEY);
		}
		else if (KEY_API_TOKEN.equals(key))
		{
			return new Secret(context, keyFragment, API_TOKEN);
		}else if (KEY_PUSH_SENDER_ID.equals(key))
		{
			return new Secret(context, keyFragment, "");
		}
		throw new IllegalArgumentException("unknown key '" + key + "'");
	}


	@Override
	public String getSecret(Context context, String key)
	{
		if (KEY_LICENSE_KEY.equals(key))
		{
			return LICENSE_KEY;
		}
		/* don't return API key in this method */
		throw new IllegalArgumentException("unknown key '" + key + "'");
	}
}
