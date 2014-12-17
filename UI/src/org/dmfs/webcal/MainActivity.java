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
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.secrets.ISecretProvider;
import org.dmfs.android.calendarcontent.secrets.SecretProvider;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.fragments.CalendarItemFragment;
import org.dmfs.webcal.fragments.CategoriesListFragment.CategoryNavigator;
import org.dmfs.webcal.fragments.GenericListFragment;
import org.dmfs.webcal.fragments.MyCalendarsFragment;
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
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.text.TextUtils;

import com.schedjoules.analytics.Analytics;
import com.schedjoules.analytics.PurchaseState;


/**
 * The Home Activity is used to display the main page along with the subsections.
 */
public class MainActivity extends NavbarActivity implements CategoryNavigator, IBillingActivity, OnIabSetupFinishedListener, QueryInventoryFinishedListener
{
	private final static int REQUEST_CODE_LAUNCH_PURCHASE_FLOW = 10003;

	private final static long MAX_INVENTORY_AGE = 15L * 60L * 1000L; // 15 minutes

	private final static long MAX_ANALYTICS_AGE = 60L * 1000L; // 1 minute

	public static final String SUBCATEGORY_EXTRA = "org.dmfs.webcal.SUBCATEGORY_EXTRA";

	private FragmentManager mFragmentManager;

	/**
	 * List of callbacks for inventory results.
	 */
	private List<WeakReference<OnInventoryListener>> mBillingCallbacks = null;

	private List<String> mMoreItemSkus = Collections.synchronizedList(new ArrayList<String>());

	/**
	 * Helper for billing services.
	 */
	private IabHelper mIabHelper;

	private boolean mIabHelperReady = false;

	private Inventory mInventoryCache = null;
	private long mInventoryTime = 0;

	@Retain
	private long mSelectedItemId = 0;

	@Retain(key = "firststart", permanent = true, classNS = "MainActivity")
	private boolean mFirstStart = true;

	private final Handler mHandler = new Handler();


	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_home);
		setupNavbar();

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

		if (mFirstStart)
		{
			mFirstStart = false;
			openDrawer();
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
				// ignore, it seems to throw an exception every now and then in the emulator
			}
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
		String title = contentFragment.getArguments().getString(PagerFragment.ARG_PAGE_TITLE);
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
			fragment = MyCalendarsFragment.newInstance(getItemTitleById(id));
			mSelectedItemId = id;
			Analytics.event("my-calendars", "menu", null, null, null, null);
		}
		else if (id == R.id.side_nav_all_calendars)
		{
			fragment = PagerFragment.newInstance(this, 0, getItemTitleById(id), -1);
			mSelectedItemId = id;
			Analytics.event("all-calendars", "menu", null, null, null, null);
		}
		else if (id == R.id.side_nav_free_calendars)
		{
			fragment = GenericListFragment.newInstance(CalendarContentContract.ContentItem.getFreeItemsContentUri(this),
				getString(R.string.side_nav_free_calendars), R.string.error_free_calendars_empty, GenericListFragment.PROJECTION2, false);
			mSelectedItemId = id;
			Analytics.event("free-calendars", "menu", null, null, null, null);
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


	@Override
	public void getInventory()
	{
		getSkuData(null);
	}


	@Override
	public synchronized void getSkuData(OnInventoryListener onInventoryListener, String... productIds)
	{
		if (mInventoryCache != null && mInventoryTime + MAX_INVENTORY_AGE > System.currentTimeMillis() && !addProductIds(productIds))
		{
			// nothing has changed, just inform the calling listener
			if (onInventoryListener != null)
			{
				onInventoryListener.onInventory(mInventoryCache);
			}
			return;
		}

		// add listener if not already known
		addOnInventoryListener(onInventoryListener);

		if (mIabHelper == null)
		{
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
			}
		}
		else
		{
			if (mIabHelperReady)
			{
				if (!mIabHelper.asyncInProgress())
				{
					queryIfPurchased();
				}
			}
		}
	}


	private boolean addProductIds(String... productIds)
	{
		boolean result = false;
		for (String productId : productIds)
		{
			if (!TextUtils.isEmpty(productId) && !mMoreItemSkus.contains(productId))
			{
				mMoreItemSkus.add(productId);
				result = true;
			}
		}
		return result;
	}


	@Override
	public void onIabSetupFinished(IabResult result)
	{
		if (result.isSuccess())
		{
			mIabHelperReady = true;
			queryIfPurchased();
		}
		else
		{
			notifyInventoryListenersAboutError();
		}
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
					// forward result
					callback.onIabPurchaseFinished(result, info);
					if (result.isSuccess())
					{
						Analytics.purchase(info.getOrderId(), PurchaseState.SUCCEEDED, 0.0f, null, null, productId);
						// update inventory
						mInventoryCache = null;
						getInventory();
					}
					else if (result.isFailure())
					{
						Analytics.purchase(null, PurchaseState.FAILED, 0.0f, null, result.getMessage(), productId);
					}
				}
			});
		}
	}


	@Override
	public synchronized void addOnInventoryListener(OnInventoryListener onInventoryListener)
	{
		List<WeakReference<OnInventoryListener>> callbacks = mBillingCallbacks;
		if (callbacks == null)
		{
			callbacks = Collections.synchronizedList(new ArrayList<WeakReference<OnInventoryListener>>());
			mBillingCallbacks = callbacks;
		}

		for (WeakReference<OnInventoryListener> cb : callbacks)
		{
			if (onInventoryListener == cb.get())
			{
				// callback already in list
				return;
			}
		}

		callbacks.add(new WeakReference<OnInventoryListener>(onInventoryListener));

		if (mInventoryCache == null || mInventoryTime + MAX_INVENTORY_AGE <= System.currentTimeMillis())
		{
			getInventory();
		}
		else
		{
			// notify new listener about inventory
			onInventoryListener.onInventory(mInventoryCache);
		}
	}


	private void queryIfPurchased()
	{
		if (mIabHelper != null && mIabHelperReady)
		{
			mIabHelper.queryInventoryAsync(true, mMoreItemSkus, this);
		}
	}


	@Override
	public void onQueryInventoryFinished(IabResult result, Inventory inv)
	{
		if (result.isSuccess())
		{
			mInventoryCache = inv;
			mInventoryTime = System.currentTimeMillis();

			notifyInventoryListeners();
		}
		else
		{
			Iterator<WeakReference<OnInventoryListener>> iterator = mBillingCallbacks.iterator();
			OnInventoryListener callback = iterator.next().get();
			if (callback != null)
			{
				callback.onError();
			}
			else
			{
				// remove garbage collected callback
				iterator.remove();
			}

		}
	}


	/**
	 * Notify all listeners about a new inventory.
	 */
	private synchronized void notifyInventoryListeners()
	{
		Inventory inventory = mInventoryCache;
		List<WeakReference<OnInventoryListener>> listenerRefs = new ArrayList<WeakReference<OnInventoryListener>>(mBillingCallbacks);

		for (WeakReference<OnInventoryListener> listenerRef : listenerRefs)
		{
			OnInventoryListener callback = listenerRef.get();

			if (callback != null)
			{
				callback.onInventory(inventory);
			}
			else
			{
				mBillingCallbacks.remove(listenerRef);
			}
		}
	}


	/**
	 * Notify all listeners about an error.
	 */
	private synchronized void notifyInventoryListenersAboutError()
	{
		List<WeakReference<OnInventoryListener>> listenerRefs = new ArrayList<WeakReference<OnInventoryListener>>(mBillingCallbacks);

		for (WeakReference<OnInventoryListener> listenerRef : listenerRefs)
		{
			OnInventoryListener callback = listenerRef.get();

			if (callback != null)
			{
				callback.onError();
			}
			else
			{
				mBillingCallbacks.remove(listenerRef);
			}
		}
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
