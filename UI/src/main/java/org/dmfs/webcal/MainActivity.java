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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscriptionId;
import org.dmfs.android.calendarcontent.secrets.ISecretProvider;
import org.dmfs.android.calendarcontent.secrets.SecretProvider;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.fragments.CalendarItemFragment;
import org.dmfs.webcal.fragments.CategoriesListFragment.CategoryNavigator;
import org.dmfs.webcal.fragments.GenericListFragment;
import org.dmfs.webcal.fragments.PagerFragment;
import org.dmfs.webcal.utils.billing.IabHelper;
import org.dmfs.webcal.utils.billing.IabHelper.OnIabPurchaseFinishedListener;
import org.dmfs.webcal.utils.billing.IabHelper.OnIabSetupFinishedListener;
import org.dmfs.webcal.utils.billing.IabHelper.QueryInventoryFinishedListener;
import org.dmfs.webcal.utils.billing.IabResult;
import org.dmfs.webcal.utils.billing.Inventory;
import org.dmfs.webcal.utils.billing.Purchase;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import com.schedjoules.analytics.Analytics;
import com.schedjoules.analytics.PurchaseState;
import com.schedjoules.android.sdk.utils.PushHelper;


/**
 * The Home Activity is used to display the main page along with the subsections.
 */
public class MainActivity extends NavbarActivity implements CategoryNavigator, IBillingActivity, OnIabSetupFinishedListener, QueryInventoryFinishedListener,
	LoaderCallbacks<Cursor>
{
	public static final String SUBCATEGORY_EXTRA = "org.dmfs.webcal.SUBCATEGORY_EXTRA";
	private final static int REQUEST_CODE_LAUNCH_PURCHASE_FLOW = 10003;
	private final static int REQUEST_CODE_LAUNCH_SUBSCRIPTION_FLOW = 10004;
	/**
	 * The interval in milliseconds to retry to load the inventory in case of an error.
	 **/
	private static final long RELOAD_INVENTORY_INTERVAL = 15000;
	private final static long MAX_ANALYTICS_AGE = 60L * 1000L; // 1 minute
	/**
	 * List of callbacks for inventory results.
	 */
	private final List<WeakReference<OnInventoryListener>> mBillingCallbacks = Collections
		.synchronizedList(new ArrayList<WeakReference<OnInventoryListener>>(8));
	private final List<String> mMoreItemSkus = Collections.synchronizedList(new ArrayList<String>());
	private final Handler mHandler = new Handler();
	/**
	 * A {@link Runnable} that triggers the inventory loading.
	 *
	 */
	private final Runnable mReloadInventoryRunnable = new Runnable()
	{

		@Override
		public void run()
		{
			refreshInventory();
		}
	};
	protected FragmentManager mFragmentManager;
	@Retain
	protected long mSelectedItemId = 0;
	/**
	 * Helper for billing services.
	 */
	private IabHelper mIabHelper;
	private boolean mIabHelperReady = false;
	private Inventory mInventoryCache = null;


	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);

		Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
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

		PushHelper.registerPush(this);

		LoaderManager lm = getSupportLoaderManager();
		lm.initLoader(-2, null, this);

		initIabHelper();
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
		if (mIabHelper != null)
		{
			try
			{
				mIabHelper.dispose();
			}
			catch (Exception e)
			{
				// ignore, it seems to throw an exception every now and then in
				// the emulator
			}
			mIabHelper = null;
			mIabHelperReady = false;
		}
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
		if (mIabHelper != null)
		{
			mIabHelper.handleActivityResult(requestCode, resultCode, data);
		}
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
			emailContent.append("os version: ").append(android.os.Build.VERSION.RELEASE).append(" / ").append(android.os.Build.VERSION.SDK_INT).append("\r\n");
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


	private void initIabHelper()
	{
		if (mIabHelper != null)
		{
			// nothing to do
			return;
		}

		// helper has not been set up yet, do that now
		mIabHelper = new IabHelper(this, SecretProvider.INSTANCE.getSecret(this, ISecretProvider.KEY_LICENSE_KEY));

		try
		{
			mIabHelper.startSetup(this);
		}
		catch (Exception e)
		{
			try
			{
				mIabHelper.dispose();
			}
			catch (Exception e2)
			{
				// ignore
			}
			mIabHelper = null;

			notifyInventoryListenersAboutError();

			// try again
			mHandler.postDelayed(mReloadInventoryRunnable, RELOAD_INVENTORY_INTERVAL);
		}
	}


	@Override
	public void onIabSetupFinished(IabResult result)
	{
		if (result.isSuccess())
		{
			mIabHelperReady = true;

			// initialize mMoreItemSkus with subscription id if we have any
			String subscriptionId = SubscriptionId.getSubscriptionId(this);
			if (subscriptionId != null)
			{
				mMoreItemSkus.add(subscriptionId);
			}

			refreshInventory();
		}
		else
		{
			notifyInventoryListenersAboutError();
		}
	}


	@Override
	public void onQueryInventoryFinished(IabResult result, Inventory inv)
	{
		if (result.isSuccess())
		{
			Log.v(TAG, "got new inventory ");
			mInventoryCache = inv;
			notifyInventoryListeners();
		}
		else
		{
			Log.e(TAG, "can't load inventory " + result.getMessage());
			notifyInventoryListenersAboutError();

			// try again
			mHandler.postDelayed(mReloadInventoryRunnable, RELOAD_INVENTORY_INTERVAL);
		}
	}


	@Override
	public void refreshInventory()
	{
		if (mIabHelper != null && mIabHelperReady && !mIabHelper.asyncInProgress())
		{
			Log.v(TAG, "refeshing inventory");
			mIabHelper.queryInventoryAsync(true, mMoreItemSkus, this);
		}
	}


	@Override
	public Inventory getInventory()
	{
		return mInventoryCache;
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
	public boolean requestSkuData(String sku)
	{
		if (!mMoreItemSkus.contains(sku))
		{
			mMoreItemSkus.add(sku);
			return true;
		}
		return false;
	}


	@Override
	public void purchase(final String productId, final OnIabPurchaseFinishedListener callback)
	{
		if (mIabHelper != null && mIabHelperReady && !mIabHelper.asyncInProgress())
		{
			Analytics.purchase(null, PurchaseState.STARTED, 0.0f, null, null, productId);
			// we inject our own listener to update the inventory
			mIabHelper.launchPurchaseFlow(this, productId, REQUEST_CODE_LAUNCH_PURCHASE_FLOW, new OnIabPurchaseFinishedListener()
			{
				@Override
				public void onIabPurchaseFinished(IabResult result, Purchase info)
				{
					if (result.isSuccess())
					{
						Analytics.purchase(info.getOrderId(), PurchaseState.SUCCEEDED, 0.0f, null, null, productId);
						// update inventory
						refreshInventory();
					}
					else if (result.isFailure())
					{
						Analytics.purchase(null, PurchaseState.FAILED, 0.0f, null, result.getMessage(), productId);
					}

					// forward result
					callback.onIabPurchaseFinished(result, info);
				}
			});
		}
	}


	@Override
	public void subscribe(final String subscriptionId, final OnIabPurchaseFinishedListener callback)
	{
		if (mIabHelper != null && mIabHelperReady && !mIabHelper.asyncInProgress())
		{
			Analytics.purchase(null, PurchaseState.STARTED, 0.0f, null, null, subscriptionId);
			// we inject our own listener to update the inventory
			mIabHelper.launchSubscriptionPurchaseFlow(this, subscriptionId, REQUEST_CODE_LAUNCH_SUBSCRIPTION_FLOW, new OnIabPurchaseFinishedListener()
			{
				@Override
				public void onIabPurchaseFinished(IabResult result, Purchase info)
				{
					if (result.isSuccess())
					{
						Analytics.purchase(info.getOrderId(), PurchaseState.SUCCEEDED, 0.0f, null, null, subscriptionId);
						// update inventory
						refreshInventory();
					}
					else if (result.isFailure())
					{
						Analytics.purchase(null, PurchaseState.FAILED, 0.0f, null, result.getMessage(), subscriptionId);
					}

					// forward result
					callback.onIabPurchaseFinished(result, info);
				}
			});
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


	/**
	 * Notify all listeners about an error.
	 */
	private synchronized void notifyInventoryListenersAboutError()
	{
		if (mBillingCallbacks != null)
		{
			List<WeakReference<OnInventoryListener>> listenerRefs = new ArrayList<WeakReference<OnInventoryListener>>(mBillingCallbacks);

			for (WeakReference<OnInventoryListener> listenerRef : listenerRefs)
			{
				OnInventoryListener callback = listenerRef.get();

				if (callback != null)
				{
					callback.onInventoryError();
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
		Log.v("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx", "load finished");
		if (cursor != null && cursor.moveToFirst())
		{
			String subscriptionId = cursor.getString(1);
			if (subscriptionId != null && !mMoreItemSkus.contains(subscriptionId))
			{
				Log.v(TAG, "got new subscription id " + subscriptionId);
				mMoreItemSkus.add(subscriptionId);
				refreshInventory();
			}
		}
	}


	@Override
	public void onLoaderReset(Loader<Cursor> arg0)
	{
	}

	/**
	 * A {@link Runnable} that triggers a transmission of analytics hits at least every {@value #MAX_INVENTORY_AGE} milliseconds.
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
