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

import android.app.Activity;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.security.ProviderInstaller;
import com.schedjoules.analytics.Analytics;
import com.schedjoules.analytics.PurchaseState;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscriptionId;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.iterables.elementary.PresentValues;
import org.dmfs.jems.iterable.composite.Joined;
import org.dmfs.jems.iterable.decorators.Mapped;
import org.dmfs.jems.iterable.elementary.Seq;
import org.dmfs.jems.optional.Optional;
import org.dmfs.jems.optional.adapters.Conditional;
import org.dmfs.jems.optional.elementary.NullSafe;
import org.dmfs.jems.procedure.Procedure;
import org.dmfs.jems.procedure.composite.ForEach;
import org.dmfs.jems.single.elementary.Collected;
import org.dmfs.webcal.fragments.CalendarItemFragment;
import org.dmfs.webcal.fragments.CategoriesListFragment.CategoryNavigator;
import org.dmfs.webcal.fragments.GenericListFragment;
import org.dmfs.webcal.fragments.PagerFragment;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import static com.android.billingclient.api.BillingClient.BillingResponseCode.OK;
import static com.android.billingclient.api.BillingClient.BillingResponseCode.USER_CANCELED;
import static com.android.billingclient.api.BillingClient.SkuType.INAPP;
import static com.android.billingclient.api.BillingClient.SkuType.SUBS;
import static java.util.Arrays.asList;


/**
 * The Home Activity is used to display the main page along with the subsections.
 */
public class MainActivity extends NavbarActivity
        implements CategoryNavigator, IBillingActivity, BillingClientStateListener, LoaderManager.LoaderCallbacks<Cursor>, ProviderInstaller.ProviderInstallListener
{

    private boolean retryProviderInstall;


    @Override
    public void onProviderInstallFailed(int errorCode, @Nullable Intent intent)
    {
        GoogleApiAvailability availability = GoogleApiAvailability.getInstance();
        if (availability.isUserResolvableError(errorCode))
        {
            // Recoverable error. Show a dialog prompting the user to
            // install/update/enable Google Play services.
            availability.showErrorDialogFragment(
                    this,
                    errorCode,
                    ERROR_DIALOG_REQUEST_CODE,
                    new DialogInterface.OnCancelListener()
                    {
                        @Override
                        public void onCancel(DialogInterface dialog)
                        {
                            // The user chose not to take the recovery action.
                            // onProviderInstallerNotAvailable();
                        }
                    });
        }
        else
        {
            // Google Play services isn't available.
            //   onProviderInstallerNotAvailable();
        }

    }


    @Override
    public void onProviderInstalled()
    {
        initBilling();
        Log.d(TAG, "billing Initialized");
    }


    public interface OnBilledListener
    {
        void onBillingFinished(BillingResult result, Purchase info);
    }


    private final static String PREFS_INTRO = "intro";
    private final static String PREF_INTRO_VERSION = "intro_version";
    public static final String SUBCATEGORY_EXTRA = "org.dmfs.webcal.SUBCATEGORY_EXTRA";
    private final static int REQUEST_CODE_LAUNCH_PURCHASE_FLOW = 10003;
    private final static int REQUEST_CODE_LAUNCH_SUBSCRIPTION_FLOW = 10004;
    private final static int REQUEST_CODE_INTRO = 10005;
    private static final int ERROR_DIALOG_REQUEST_CODE = 11006;

    /**
     * The interval in milliseconds to retry to load the inventory in case of an error.
     **/
    private static final long RELOAD_INVENTORY_INTERVAL = 15000;
    private final static long MAX_ANALYTICS_AGE = 60L * 1000L; // 1 minute
    /**
     * List of callbacks for inventory results.
     */
    private final List<WeakReference<OnInventoryListener>> mBillingCallbacks = Collections.synchronizedList(new ArrayList<>(8));
    private final List<String> mMoreItemSkus = Collections.synchronizedList(new ArrayList<String>());
    private final Handler mHandler = new Handler();

    protected FragmentManager mFragmentManager;
    @Retain
    protected long mSelectedItemId = 0;
    /**
     * Helper for billing services.
     */
    private BillingClient mBillingClient;

    Reference<OnBilledListener> mOnPurchaseListenerReference = null;


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        setupNavbar(toolbar);

        mFragmentManager = getSupportFragmentManager();

        if (savedInstanceState == null)
        {
            selectItem(R.id.side_nav_all_calendars);
            setNavigationSelection(0);
        }
        else if (mSelectedItemId >= 0)
        {
            selectItem(mSelectedItemId);
            setNavigationSelectionById(mSelectedItemId);
        }

        if (savedInstanceState == null)
        {
            Analytics.sessionStart("MAIN");
        }

        SharedPreferences prefs = getSharedPreferences(getPackageName() + "_preferences", 0);

        if (prefs.getBoolean("enable_analytics", true))
        {
            Analytics.enable();
        }
        else
        {
            Analytics.disable();
        }

        // PushHelper.registerPush(this);

        LoaderManager lm = getSupportLoaderManager();
        lm.initLoader(-2, null, this);
        ProviderInstaller.installIfNeededAsync(this, this);

        if (savedInstanceState == null)
        {
            handleIntent(getIntent());

            // TODO Intro screen is disabled now until it is finalized:
            // if (getSharedPreferences(PREFS_INTRO, 0).getInt(PREF_INTRO_VERSION, 0) < getResources().getInteger(R.integer.com_schedjoules_intro_version))
            // {
            //     Intent introIntent = new Intent(this, Xtivity.class);
            //     startActivityForResult(introIntent, REQUEST_CODE_INTRO);
            // }
        }
    }


    @Override
    protected void onNewIntent(Intent intent)
    {
        super.onNewIntent(intent);
        handleIntent(intent);
    }


    private void handleIntent(Intent intent)
    {
        if (intent.getData() != null)
        {
            CharSequence parent = intent.getCharSequenceExtra("parent");
            if (parent != null && TextUtils.isDigitsOnly(parent.toString()))
            {
                openCategory(Integer.parseInt(parent.toString()), "", -1);
            }

            long page_id = ContentUris.parseId(intent.getData());
            if (page_id >= 0)
            {
                openCategory(page_id, "", -1);
            }
        }
    }


    @Override
    protected void onResume()
    {
        super.onResume();
        mHandler.postDelayed(mAnalyticsTrigger, MAX_ANALYTICS_AGE);
    }


    @Override
    protected void onPause()
    {
        super.onPause();
        mHandler.removeCallbacks(mAnalyticsTrigger);
    }


    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        Analytics.sessionEnd();
    }


    /*
     * (non-Javadoc)
     *
     * Forward onActivityResult to mIabHelper;
     *
     * @see android.support.v4.app.FragmentActivity#onActivityResult(int, int, android.content.Intent)
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_INTRO)
        {
            if (resultCode == Activity.RESULT_OK)
            {
                SharedPreferences prefs = getSharedPreferences(PREFS_INTRO, 0);
                prefs.edit().putInt(PREF_INTRO_VERSION, getResources().getInteger(R.integer.com_schedjoules_intro_version)).apply();
            }
            else
            {
                finish();
            }
        }

        if (requestCode == ERROR_DIALOG_REQUEST_CODE)
        {
            // Adding a fragment via GoogleApiAvailability.showErrorDialogFragment
            // before the instance state is restored throws an error. So instead,
            // set a flag here, which causes the fragment to delay until
            // onPostResume.
            retryProviderInstall = true;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    /**
     * On resume, check whether a flag indicates that the provider needs to be reinstalled.
     */
    @Override
    protected void onPostResume()
    {
        super.onPostResume();
        if (retryProviderInstall)
        {
            // It's safe to retry installation.
            ProviderInstaller.installIfNeededAsync(this, this);
        }
        retryProviderInstall = false;
    }


    @Override
    protected String getActivityTitle()
    {
        Fragment contentFragment = mFragmentManager.findFragmentById(R.id.content);
        Bundle args = contentFragment.getArguments();
        return args != null ? args.getString(PagerFragment.ARG_PAGE_TITLE) : "";
    }


    @Override
    public void openCategory(long id, String title, long icon)
    {
        mFragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();

        PagerFragment fragment = PagerFragment.newInstance(this, id, title, icon);
        fragmentTransaction.replace(R.id.content, fragment);
        fragmentTransaction.addToBackStack("");
        fragmentTransaction.commit();
        mSelectedItemId = -1;
        setNavigationSelection(-1);
    }


    @Override
    public void openCalendar(long id, long icon)
    {
        mFragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
        Fragment contentFragment = mFragmentManager.findFragmentById(R.id.content);
        String title = "";
        if (contentFragment != null)
        {
            title = contentFragment.getArguments().getString(PagerFragment.ARG_PAGE_TITLE);
        }
        CalendarItemFragment fragment = CalendarItemFragment.newInstance(this, id, title, icon);
        fragmentTransaction.replace(R.id.content, fragment);
        fragmentTransaction.addToBackStack("");
        fragmentTransaction.commit();
        mSelectedItemId = -1;
        setNavigationSelection(-1);
    }


    @Override
    protected boolean selectItem(long id)
    {
        if (id == mSelectedItemId)
        {
            super.selectItem(id);
            return true;
        }

        Fragment fragment = null;
        if (id == R.id.side_nav_favorite_calendars)
        {
            fragment = GenericListFragment.newInstance(CalendarContentContract.ContentItem.getStarredItemsContentUri(this),
                    getString(R.string.side_nav_favorite_calendars), R.string.error_favorite_calendars_empty, GenericListFragment.PROJECTION2, true);
            mSelectedItemId = id;
            Analytics.event("fav-calendars", "menu", null, null, null, null);
        }
        else if (id == R.id.side_nav_my_calendars)
        {
            fragment = GenericListFragment.newInstance(CalendarContentContract.SubscribedCalendars.getContentUri(this),
                    getString(R.string.side_nav_my_calendars), R.string.error_my_calendars_empty, GenericListFragment.PROJECTION, true);

            mSelectedItemId = id;
            Analytics.event("my-calendars", "menu", null, null, null, null);
        }
        else if (id == R.id.side_nav_all_calendars)
        {
            fragment = PagerFragment.newInstance(this, 0, getItemTitleById(id), -1);
            mSelectedItemId = id;
            Analytics.event("all-calendars", "menu", null, null, null, null);
        }
        else if (id == R.id.side_nav_faq)
        {
            Intent faqIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.faq_url)));
            startActivity(faqIntent);
            Analytics.event("faq", "menu", null, null, null, null);
        }
        else if (id == R.id.side_nav_feedback)
        {
            Analytics.event("feedback", "menu", null, null, null, null);
            PackageManager pm = getPackageManager();
            PackageInfo pi = null;
            try
            {
                pi = pm.getPackageInfo(getPackageName(), 0);
            }
            catch (NameNotFoundException e)
            {
                // that should not happen!
            }

            StringBuilder emailContent = new StringBuilder(256);
            emailContent.append("\r\n\r\n\r\n\r\n\r\n--------------\r\n");
            emailContent.append("app: ").append(getPackageName()).append("\r\n");
            emailContent.append("version: ").append(pi.versionName).append(" / ").append(pi.versionCode).append("\r\n");
            emailContent.append("locale: ").append(Locale.getDefault().getLanguage()).append("\r\n");
            emailContent.append("location: ").append(Locale.getDefault().getCountry()).append("\r\n");
            emailContent.append("timezone: ").append(TimeZone.getDefault().getID()).append("\r\n");
            emailContent.append("device: ").append(android.os.Build.DEVICE).append("\r\n");
            emailContent.append("model: ").append(android.os.Build.MODEL).append("\r\n");
            emailContent.append("os version: ")
                    .append(android.os.Build.VERSION.RELEASE)
                    .append(" / ")
                    .append(android.os.Build.VERSION.SDK_INT)
                    .append("\r\n");
            emailContent.append("firmware: ").append(android.os.Build.ID).append("\r\n");

            Intent emailIntent = new Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", getString(R.string.contact_address), null));
            emailIntent.putExtra(Intent.EXTRA_SUBJECT, "Contact");
            emailIntent.putExtra(Intent.EXTRA_TEXT, emailContent.toString());
            startActivity(Intent.createChooser(emailIntent, getString(R.string.send_email)));
            // fragment = ContactFragment.newInstance();
        }
        else if (id == R.id.side_nav_settings)
        {
            Intent preferencesIntent = new Intent(this, PreferencesActivity.class);
            startActivity(preferencesIntent);
            Analytics.event("settings", "menu", "settings clicked", null, null, null);
        }

        if (fragment != null)
        {
            // drop backstack
            mFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

            FragmentTransaction fragmentTransaction = mFragmentManager.beginTransaction();
            fragmentTransaction.replace(R.id.content, fragment);
            fragmentTransaction.commit();
        }

        super.selectItem(id);

        return fragment != null;
    }


    private void initBilling()
    {
        if (mBillingClient != null && mBillingClient.isReady())
        {
            // nothing to do
            return;
        }

        mBillingClient = BillingClient.newBuilder(this).setListener((billingResult, purchases) -> {

            if (billingResult.getResponseCode() == OK && purchases != null)
            {
                for (Purchase purchase : purchases)
                {
                    new ForEach<>(purchase.getSkus()).process(sku ->
                            Analytics.purchase(purchase.getOrderId(), PurchaseState.SUCCEEDED, 0.0f, null, null, sku));
                    if (!purchase.isAcknowledged())
                    {
                        mBillingClient.acknowledgePurchase(
                                AcknowledgePurchaseParams
                                        .newBuilder()
                                        .setPurchaseToken(purchase.getPurchaseToken())
                                        .build(),
                                billingResult1 -> {
                                });
                        Optional<OnBilledListener> listener = new org.dmfs.jems.optional.decorators.Mapped<>(
                                Reference::get,
                                new org.dmfs.jems.optional.decorators.Sieved<>(pl -> pl.get() != null,
                                        new NullSafe<>(mOnPurchaseListenerReference)));
                        if (listener.isPresent())
                        {
                            listener.value().onBillingFinished(billingResult, purchase);
                        }
                    }
                }
                notifyInventoryListeners();

            }
            else if (billingResult.getResponseCode() == USER_CANCELED)
            {
                Analytics.purchase(null, PurchaseState.CANCELLED, 0.0f, null, billingResult.getDebugMessage(), "");
            }
            else
            {
                Analytics.purchase(null, PurchaseState.FAILED, 0.0f, null, billingResult.getDebugMessage(), "");
            }

        }).enablePendingPurchases().build();
        mBillingClient.startConnection(this);
    }


    @Override
    public void onBillingSetupFinished(BillingResult billingResult)
    {
        notifyInventoryListeners();
    }


    @Override
    public void onBillingServiceDisconnected()
    {
        initBilling();
    }


    @Override
    public Set<Purchase> getInventory()
    {
        return new Collected<>(HashSet::new, new Joined<>(
                new Mapped<>(
                        Purchase.PurchasesResult::getPurchasesList,
                        new PresentValues<>(
                                new Seq<>(
                                        new Conditional<Purchase.PurchasesResult>(
                                                p -> p.getResponseCode() == OK,
                                                () -> mBillingClient.queryPurchases(SUBS)),
                                        new Conditional<Purchase.PurchasesResult>(
                                                p -> p.getResponseCode() == OK,
                                                () -> mBillingClient.queryPurchases(INAPP))))))).value();
    }


    @Override
    public synchronized void addOnInventoryListener(OnInventoryListener onInventoryListener)
    {
        if (onInventoryListener == null)
        {
            return;
        }

        for (WeakReference<OnInventoryListener> cb : mBillingCallbacks)
        {
            if (onInventoryListener == cb.get())
            {
                // callback already in list
                return;
            }
        }

        mBillingCallbacks.add(new WeakReference<OnInventoryListener>(onInventoryListener));
        if (mBillingClient.isReady())
        {
            onInventoryListener.onInventoryLoaded();
        }
    }


    @Override
    public synchronized void removeOnInventoryListener(OnInventoryListener onInventoryListener)
    {
        if (onInventoryListener == null)
        {
            return;
        }

        for (WeakReference<OnInventoryListener> cb : mBillingCallbacks)
        {
            if (onInventoryListener == cb.get())
            {
                mBillingCallbacks.remove(cb);
                return;
            }
        }
    }


    @Override
    public void billme(final String productId, final OnBilledListener callback, SkuDetails skuDetails)
    {
        if (mBillingClient.isReady())
        {
            Analytics.purchase(null, PurchaseState.STARTED, skuDetails.getPriceAmountMicros() / 1000000, skuDetails.getPriceCurrencyCode(), null, productId);
            mOnPurchaseListenerReference = new WeakReference<>(callback);
            mBillingClient.launchBillingFlow(this, BillingFlowParams.newBuilder().setSkuDetails(skuDetails).build());
        }
    }


    /**
     * Notify all listeners about a new inventory.
     */
    private synchronized void notifyInventoryListeners()
    {
        if (mBillingCallbacks != null)
        {
            List<WeakReference<OnInventoryListener>> listenerRefs = new ArrayList<WeakReference<OnInventoryListener>>(mBillingCallbacks);

            for (WeakReference<OnInventoryListener> listenerRef : listenerRefs)
            {
                OnInventoryListener callback = listenerRef.get();

                if (callback != null)
                {
                    callback.onInventoryLoaded();
                }
                else
                {
                    mBillingCallbacks.remove(listenerRef);
                }
            }
        }

    }


    @Override
    public Loader<Cursor> onCreateLoader(int loaderId, Bundle extras)
    {
        return new CursorLoader(this, SubscriptionId.getContentUri(this), null, null, null, null);
    }


    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
    {
        if (cursor != null && cursor.moveToFirst())
        {
            String subscriptionId = cursor.getString(1);
            if (subscriptionId != null && !mMoreItemSkus.contains(subscriptionId))
            {
                Log.v(TAG, "got new subscription id " + subscriptionId);
                mMoreItemSkus.add(subscriptionId);
            }
        }
    }


    @Override
    public void onLoaderReset(Loader<Cursor> arg0)
    {
    }


    public void skuDetails(String sku, @NonNull Procedure<SkuDetails> listener)
    {
        mBillingClient.querySkuDetailsAsync(
                SkuDetailsParams.newBuilder().setSkusList(asList(sku)).setType(SUBS).build(),
                (billingResult, skuDetailsList) -> {
                    if (billingResult.getResponseCode() == OK && skuDetailsList.size() > 0)
                    {
                        listener.process(skuDetailsList.get(0));
                    }
                });
    }


    public boolean billingReady()
    {
        return mBillingClient.isReady();
    }


    ;;

    /**
     * A {@link Runnable} that triggers a transmission of analytics hits at least every {@value #MAX_ANALYTICS_AGE} milliseconds.
     *
     * @see #MAX_ANALYTICS_AGE
     */
    private final Runnable mAnalyticsTrigger = new Runnable()
    {

        @Override
        public void run()
        {
            Analytics.triggerSendBatch();
            mHandler.postDelayed(mAnalyticsTrigger, MAX_ANALYTICS_AGE);
        }
    };

}
