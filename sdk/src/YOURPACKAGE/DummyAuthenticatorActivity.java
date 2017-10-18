package YOURPACKAGE;

import android.accounts.AccountAuthenticatorActivity;
import android.content.Intent;
import android.os.Bundle;


/**
 * An activity that serves as AuthenticatorActivity. It just launches the main activity and finishes. We need it to satisfy Android when the users attempts
 * to create a new account.
 */
public class DummyAuthenticatorActivity extends AccountAuthenticatorActivity
{
    @Override
    protected void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);

        Intent intent = new Intent(this, MainActivity.class /* adjust to your main activity */);
        startActivity(intent);
        setResult(RESULT_OK);

        finish();
    }
}
