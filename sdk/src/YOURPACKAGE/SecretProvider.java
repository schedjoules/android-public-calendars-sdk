package YOURPACKAGE.germany;

import android.content.Context;

import org.dmfs.android.calendarcontent.secrets.ISecretProvider;
import org.dmfs.android.calendarcontent.secrets.Secret;
import org.dmfs.android.calendarcontent.secrets.SecurityToken;


public class SecretProvider implements ISecretProvider
{

    /**
     * Your public key to access Google Play services. You should obfuscate the key, so it won't be included in your APK in plain text. Better yet, use
     * DexGuard.
     */
    private final static String LICENSE_KEY = "your Google play API public key";

    /**
     * Your SchedJoules API token. This value must be obfuscated, so the APK won't contain it in plain text. Consider to use DexGuard.
     * <p>
     * Leaked keys will be revoked!
     */
    private final static String API_TOKEN = "your SchedJoules API token";


    @Override
    public Secret getSecret(Context context, String key, SecurityToken token)
    {
        if (KEY_LICENSE_KEY.equals(key))
        {
            return new Secret(context, token /* just forward the token */, LICENSE_KEY /* pass the license key in plain text here */);
        }
        else if (KEY_API_TOKEN.equals(key))
        {
            return new Secret(context, token /* just forward the token */, API_TOKEN /* pass the API token in plain text here */);
        }
        throw new IllegalArgumentException("unknown key '" + key + "'");
    }


    @Override
    public String getSecret(Context context, String key)
    {
        /*
		 * This method is meant to provide the Google Play License Key to the SDK UI provided by SchedJoules. If you implement your own UI just return null,
		 * otherwise uncomment the code below to return the key.
		 *		
		 * Never ever return the API token here!
		 */
        return null;

        // if (KEY_LICENSE_KEY.equals(key))
        // {
        // 	return LICENSE_KEY;
        // }
        // /* don't return API token by this method !!! */
        // throw new IllegalArgumentException("unknown key '" + key + "'");
    }
}
