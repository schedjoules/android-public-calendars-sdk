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

package org.dmfs.webcal.fragments;

import java.net.URI;
import java.util.TimeZone;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.Products;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscribedCalendars;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.webcalreader.provider.WebCalReaderContract;
import org.dmfs.asynctools.PetriNet;
import org.dmfs.asynctools.PetriNet.Place;
import org.dmfs.asynctools.PetriNet.Transition;
import org.dmfs.webcal.EventsPreviewActivity;
import org.dmfs.webcal.IBillingActivity;
import org.dmfs.webcal.IBillingActivity.OnInventoryListener;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.EventListAdapter;
import org.dmfs.webcal.adapters.SectionTitlesAdapter;
import org.dmfs.webcal.adapters.SectionTitlesAdapter.SectionIndexer;
import org.dmfs.webcal.fragments.CalendarTitleFragment.SwitchStatusListener;
import org.dmfs.webcal.utils.BitmapUtils;
import org.dmfs.webcal.utils.Event;
import org.dmfs.webcal.utils.ImageProxy;
import org.dmfs.webcal.utils.ImageProxy.ImageAvailableListener;
import org.dmfs.webcal.utils.ProtectedBackgroundJob;
import org.dmfs.webcal.utils.billing.IabHelper.OnIabPurchaseFinishedListener;
import org.dmfs.webcal.utils.billing.Inventory;
import org.dmfs.webcal.utils.billing.Purchase;

import android.app.ActionBar;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.ProgressBar;


public class CalendarItemFragment extends PurchasableItemFragment implements OnInventoryListener, LoaderManager.LoaderCallbacks<Cursor>, SwitchStatusListener,
	OnIabPurchaseFinishedListener, OnItemClickListener, ImageAvailableListener
{
	/**
	 * The time to wait before showing a progress indicator. Often the list loads much faster and we don't want the indicator to flash up.
	 */
	private final static int PROGRESS_INDICATOR_DELAY = 50;

	private static final String TAG = "CalendarItemFragment";

	private static final String ARG_CONTENT_URI = "content_uri";
	private static final String ARG_TITLE = "title";
	private static final String ARG_ICON = "icon";

	private static final int LOADER_CALENDAR_ITEM = 24234;
	private static final int LOADER_SUBSCRIBED_CALENDAR = LOADER_CALENDAR_ITEM + 1;
	private static final int LOADER_PREVIEW = -2;

	private final static String[] PROJECTION = new String[] { CalendarContentContract.ContentItem.GOOGLE_PLAY_PRODUCT_ID,
		CalendarContentContract.ContentItem.TITLE, CalendarContentContract.ContentItem.ICON_ID, CalendarContentContract.ContentItem.URL,
		CalendarContentContract.ContentItem.GOOGLE_PLAY_ORDER_ID, CalendarContentContract.ContentItem.PRODUCT_TITLE,
		CalendarContentContract.Products.FREE_TRIAL_END, CalendarContentContract.ContentItem.PRODUCT_PRICE,
		CalendarContentContract.ContentItem.APPTIVATE_ACTIVATION_RESPONSE, ContentItem.STARRED, ContentItem._ID };

	private interface COLUMNS
	{
		int IDENTIFIER = 0;
		int TITLE = 1;
		int ICON = 2;
		int URL = 3;
		int ORDER_ID = 4;
		int PRODUCT_TITLE = 5;
		int TRIAL_END = 6;
		int PRODUCT_PRICE = 7;
		int UNLOCK_CODE = 8;
		int STARRED = 9;
		int ID = 10;
	}

	private CalendarTitleFragment mTitleFragment;

	private final Place mLoadingItem = new Place(1);
	private final Place mLoadingSubscription = new Place(1);
	private final Place mLoadingCalendarInfo = new Place();
	private final Place mItemChanged = new Place();
	private final Place mSubscriptionChanged = new Place();
	private final Place mInventoryReady = new Place();
	private final Place mLoadingDone = new Place();

	private Inventory mInventory;
	private Uri mSubscriptionUri;
	private String mCalendarName;
	private URI mCalendarUrl;
	private String mOrderId;
	private String mProductId;

	/**
	 * The icon is initialized with the icon passed to {@link #newInstance(Uri, String, long)} or {@link #newInstance(Context, long, String, long)}. If the
	 * calendar has it's own icon, it replaces the initial icon later on.
	 */
	@Parameter(key = ARG_ICON)
	private long mIcon;

	private String mUnlockCode;
	private Long mTrialPeriodEnd;
	private boolean mSynced;
	private Drawable mBackground = null;
	private boolean mStarred;
	private long mId;
	private ProgressBar mProgressBar;
	private EventListAdapter mListAdapter;
	private ActionBar mActionBar;

	private ImageProxy mImageProxy;
	private SectionTitlesAdapter mSectionAdapter;
	private ListView mListView;

	private Transition<Inventory> mInventoryLoaded = new Transition<Inventory>(1, mInventoryReady)
	{
		@Override
		protected void execute(Inventory inventory)
		{
			mInventory = inventory;
		}
	};

	private Transition<Cursor> mItemLoaded = new Transition<Cursor>(mLoadingItem, mLoadingItem, mInventoryReady, mLoadingCalendarInfo)
	{

		@Override
		protected void execute(Cursor cursor)
		{
			if (cursor != null && cursor.moveToFirst())
			{
				long iconId = cursor.getLong(COLUMNS.ICON);
				if (iconId > 0)
				{
					mIcon = iconId;
				}
				mCalendarName = cursor.getString(COLUMNS.TITLE);
				mCalendarUrl = URI.create(cursor.getString(COLUMNS.URL));
				mOrderId = cursor.getString(COLUMNS.ORDER_ID);
				mProductId = cursor.getString(COLUMNS.IDENTIFIER);
				mTrialPeriodEnd = cursor.getLong(COLUMNS.TRIAL_END);
				mUnlockCode = cursor.getString(COLUMNS.UNLOCK_CODE);
				mId = cursor.getLong(COLUMNS.ID);
				mStarred = cursor.getInt(COLUMNS.STARRED) > 0;

				mTitleFragment.setTitle(mCalendarName);
				mTitleFragment.setIcon(mIcon);
				mTitleFragment.setId(mId);
				mTitleFragment.setStarred(mStarred);

				if (mActionBar != null)
				{
					mActionBar.setTitle(mCalendarName);
					mActionBar.setSubtitle(mTitle);

					Drawable icon = mImageProxy.getImage(mIcon, CalendarItemFragment.this);
					if (icon != null)
					{
						mActionBar.setIcon(BitmapUtils.scaleDrawable(getResources(), (BitmapDrawable) icon, 36, 36));
					}
				}

				// mPurchaseHeaderFragment.onUpdateCursor(cursor);
				setPurchaseableItem(cursor);

				if (mListAdapter.getCursor() == null)
				{
					// the the adapter has no cursor yet
					LoaderManager loaderManager = getLoaderManager();
					if (loaderManager != null)
					{
						if (loaderManager.getLoader(LOADER_PREVIEW) == null)
						{
							Loader<Cursor> initLoader = loaderManager.initLoader(LOADER_PREVIEW, null, CalendarItemFragment.this);
							if (initLoader != null)
							{
								initLoader.forceLoad();
							}
							else
							{
								Log.e(TAG, "Newly created Loader is null");
							}
						}
						else
						{
							loaderManager.restartLoader(LOADER_PREVIEW, null, CalendarItemFragment.this);
						}
					}
					else
					{
						Log.e(TAG, "Loader Manager is null");
					}
				}
			}
		}
	};

	private Transition<Cursor> mSubscriptionLoaded = new Transition<Cursor>(1, mLoadingSubscription, 1, mLoadingDone, mLoadingCalendarInfo,
		mLoadingSubscription)
	{

		@Override
		protected void execute(Cursor cursor)
		{
			mSynced = false;
			if (cursor != null && cursor.moveToFirst())
			{
				long id = cursor.getLong(cursor.getColumnIndex(SubscribedCalendars._ID));
				mSubscriptionUri = SubscribedCalendars.getItemContentUri(getActivity(), id);
				// mSettingsFragment.setCalendarInfo(cursor, mSubscriptionUri);
				mSynced = cursor.getInt(cursor.getColumnIndex(SubscribedCalendars.SYNC_ENABLED)) > 0;
			}
			mTitleFragment.setSwitchChecked(mSynced);
			mTitleFragment.enableSwitch(mSynced);

			// invalidate options to show/hide settings option
			getActivity().invalidateOptionsMenu();
		}
	};

	private Transition<Void> mUpdateItem = new Transition<Void>(2, mInventoryReady, 2, mLoadingDone)
	{

		@Override
		protected void execute(Void data)
		{
			Activity activity = getActivity();

			if (mInventory != null)
			{
				Purchase purchase = mInventory.getPurchase(mProductId);
				if (purchase != null)
				{
					String orderId = purchase.getOrderId();
					if (!TextUtils.equals(mOrderId, orderId))
					{
						Products.updateProduct(activity, mProductId, orderId, purchase.getToken());
						mOrderId = orderId;
					}
				}
				else
				{
					if (mOrderId != null)
					{
						Products.updateProduct(activity, mProductId, null, null);
						mOrderId = null;
					}
				}
			}
		}
	}.setAutoFire(true);

	private Transition<Void> mEnableSwitch = new Transition<Void>(3, mLoadingDone, 2, mLoadingDone)
	{
		@Override
		protected void execute(Void data)
		{
			mTitleFragment.enableSwitch(mOrderId != null || TextUtils.isEmpty(mProductId) || !TextUtils.isEmpty(mUnlockCode)
				|| (mTrialPeriodEnd != null && mTrialPeriodEnd > System.currentTimeMillis()) || mSynced);
		}
	}.setAutoFire(true);

	private PetriNet mPetriNet = new PetriNet(mInventoryLoaded, mItemLoaded, mSubscriptionLoaded, mUpdateItem, mEnableSwitch);
	private final Handler mHandler = new Handler();

	@Parameter(key = ARG_CONTENT_URI)
	private Uri mContentUri;
	@Parameter(key = ARG_TITLE)
	private String mTitle;


	public static CalendarItemFragment newInstance(Uri contentUri, String title, long icon)
	{
		CalendarItemFragment result = new CalendarItemFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_CONTENT_URI, contentUri);
		args.putString(ARG_TITLE, title);
		args.putLong(ARG_ICON, icon);
		result.setArguments(args);
		return result;
	}


	public static CalendarItemFragment newInstance(Context context, long id, String title, long icon)
	{
		return newInstance(ContentItem.getItemContentUri(context, id), title, icon);
	}


	public CalendarItemFragment()
	{
		// Obligatory unparameterized constructor
	}


	@Override
	public void onAttach(final Activity activity)
	{
		super.onAttach(activity);
		((IBillingActivity) activity).addOnInventoryListener(this);

		mActionBar = activity.getActionBar();
		mActionBar.removeAllTabs();
		mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
		mActionBar.setTitle(mCalendarName);
		mActionBar.setSubtitle(mTitle);

		mImageProxy = ImageProxy.getInstance(activity);
	}


	@Override
	public View onCreateItemView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_calendar_item, container, false);

		View progressView = inflater.inflate(R.layout.progress_indicator, null, false);

		mProgressBar = (ProgressBar) progressView.findViewById(android.R.id.progress);

		mListView = (ListView) returnView.findViewById(android.R.id.list);

		mListView.addHeaderView(progressView);
		mListView.setOnItemClickListener(this);
		mListView.setHeaderDividersEnabled(false);
		mListAdapter = new EventListAdapter(inflater.getContext(), null);
		mListView.setAdapter(mSectionAdapter = new SectionTitlesAdapter(inflater.getContext(), mListAdapter, new SectionIndexer()
		{

			@Override
			public String getSectionTitle(int index)
			{
				Time start = new Time(TimeZone.getDefault().getID());
				start.set(index & 0x00ff, (index >> 8) & 0x00ff, (index >> 16) & 0x0ffff);

				return DateUtils.formatDateTime(getActivity(), start.toMillis(true), DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
					| DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY);
			}


			@Override
			public int getSectionIndex(Object object)
			{
				Cursor cursor = (Cursor) object;

				Time start = new Time(cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TIMZONE)));
				start.set(cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTSTART)));
				boolean allday = cursor.getInt(cursor.getColumnIndex(WebCalReaderContract.Events.IS_ALLDAY)) == 1;
				start.allDay = allday;

				// we return an encoded date as index
				return (start.year << 16) + (start.month << 8) + start.monthDay;

			}
		}, R.layout.events_preview_list_section_header));

		FragmentManager fm = getChildFragmentManager();

		mTitleFragment = (CalendarTitleFragment) fm.findFragmentById(R.id.calendar_title_fragment_container);

		FragmentTransaction ft = fm.beginTransaction();

		if (mTitleFragment == null)
		{
			mTitleFragment = CalendarTitleFragment.newInstance();
			ft.replace(R.id.calendar_title_fragment_container, mTitleFragment);
		}

		if (!ft.isEmpty())
		{
			ft.commit();
		}

		LoaderManager lm = getLoaderManager();
		lm.initLoader(LOADER_CALENDAR_ITEM, null, this);
		lm.initLoader(LOADER_SUBSCRIBED_CALENDAR, null, this);

		// set this to true, so the menu is cleared automatically when leaving the fragment, otherwise the star icon will stay visible
		setHasOptionsMenu(true);

		return returnView;
	}


	@Override
	public void onDetach()
	{
		super.onDetach();
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.calendar_item, menu);
		menu.findItem(R.id.menu_settings).setVisible(mSynced);
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		if (id == R.id.menu_settings)
		{
			CalendarSettingsFragment settings = CalendarSettingsFragment.newInstance(mSubscriptionUri);
			settings.show(getChildFragmentManager(), null);
		}
		return super.onOptionsItemSelected(item);
	}


	@Override
	public Loader<Cursor> onCreateLoader(int loaderId, Bundle params)
	{
		Activity activity = getActivity();

		switch (loaderId)
		{
			case LOADER_CALENDAR_ITEM:
				return new CursorLoader(activity, mContentUri, PROJECTION, null, null, null);
			case LOADER_SUBSCRIBED_CALENDAR:
				return new CursorLoader(activity, SubscribedCalendars.getContentUri(activity), null, SubscribedCalendars.ITEM_ID + "="
					+ ContentUris.parseId(mContentUri), null, null);
			case LOADER_PREVIEW:
				mHandler.postDelayed(mProgressIndicator, PROGRESS_INDICATOR_DELAY);
				return new CursorLoader(getActivity(), WebCalReaderContract.Events.getEventsUri(getActivity(), mCalendarUrl, 60 * 1000), null, null, null, null);
			default:
				return null;
		}

	}


	@Override
	public void onLoadFinished(Loader<Cursor> loader, final Cursor cursor)
	{
		switch (loader.getId())
		{
			case LOADER_CALENDAR_ITEM:
				mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						mPetriNet.fire(mItemLoaded, cursor);
					}
				});
				break;

			case LOADER_SUBSCRIBED_CALENDAR:
				mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						mPetriNet.fire(mSubscriptionLoaded, cursor);
					}
				});
				break;
			case LOADER_PREVIEW:
				if (cursor != null && cursor.getCount() > 0)
				{
					mHandler.removeCallbacks(mProgressIndicator);
					mProgressBar.setVisibility(View.GONE);
				}

				if (cursor == null)
				{
					Log.e(TAG, "No events received");
					// mMessage.setVisibility(View.VISIBLE);
					mHandler.removeCallbacks(mProgressIndicator);
					mProgressBar.setVisibility(View.GONE);
					return;
				}
				Cursor oldCursor = mListAdapter.swapCursor(cursor);
				if (oldCursor == null || oldCursor.getCount() == 0)
				{
					goToToday();
				}

				if (oldCursor != null)
				{
					oldCursor.close();
				}
		}
	}


	private void goToToday()
	{
		Time now = new Time(TimeZone.getDefault().getID());
		now.setToNow();
		int nowIdx = (now.year << 16) + (now.month << 8) + now.monthDay;

		if (mSectionAdapter != null)
		{
			for (int i = 0, count = mSectionAdapter.getCount(); i < count; ++i)
			{
				long id = mSectionAdapter.getItemId(i);
				if (SectionTitlesAdapter.itemPos(id) == SectionTitlesAdapter.HEADER_ID)
				{
					if (SectionTitlesAdapter.sectionId(id) >= nowIdx)
					{
						mListView.setSelectionFromTop(Math.min(mSectionAdapter.getCount() - 1, i + 1), 0);
						return;
					}
				}
			}
		}
	}


	@Override
	public void onLoaderReset(Loader<Cursor> loader)
	{
		// at present we don't do anything here
	}


	@Override
	public void onInventory(Inventory inventory)
	{
		super.onInventory(inventory);
		mPetriNet.fire(mInventoryLoaded, inventory);
	}


	@Override
	public void onError()
	{
		super.onError();
		mPetriNet.fire(mInventoryLoaded, null);
	}


	@Override
	public boolean onSyncSwitchToggle(boolean status)
	{
		if (isResumed() && status != mSynced)
		{
			if (mOrderId != null || mProductId == null || !TextUtils.isEmpty(mUnlockCode) || mTrialPeriodEnd != null
				&& mTrialPeriodEnd > System.currentTimeMillis())
			{
				setCalendarSynced(status);
			}
			else if (status && mInventory != null)
			{
				((IBillingActivity) getActivity()).purchase(mProductId, this);
			}
			else if (!status)
			{
				setCalendarSynced(false);
				mTitleFragment.enableSwitch(mOrderId != null || !TextUtils.isEmpty(mUnlockCode));
				setEnforceHeaderHidden(false);
			}
		}
		return false;
	};


	@Override
	public void onPurchase(boolean success, boolean freeTrial)
	{
		if (success)
		{
			if (!freeTrial)
			{
				mInventory = null;
			}
			setCalendarSynced(true);
		}
		else
		{
			// mTitleFragment.setSwitchChecked(false);
		}
	}


	@Override
	public void imageAvailable(long mIconId, Drawable drawable)
	{
		if (isAdded())
		{
			mActionBar.setIcon(BitmapUtils.scaleDrawable(getResources(), (BitmapDrawable) drawable, 36, 36));
		}
	}


	private void setCalendarSynced(final boolean status)
	{
		final Activity activity = getActivity();
		if (mSynced != status)
		{
			new ProtectedBackgroundJob<Void, Uri>(activity)
			{
				@Override
				protected void doPostExecute(Uri result)
				{
					mSubscriptionUri = result;
				}


				@Override
				protected Uri doInBackground(Void... params)
				{
					if (status)
					{
						if (mSubscriptionUri != null)
						{
							// calendar already exists, we just have to enable sync
							ContentValues values = new ContentValues();
							values.put(SubscribedCalendars.SYNC_ENABLED, 1);
							SubscribedCalendars.updateCalendar(activity, mSubscriptionUri, values);
							return mSubscriptionUri;
						}
						else
						{
							return SubscribedCalendars.addCalendar(activity, ContentUris.parseId(mContentUri), mCalendarName,
								(int) (Math.random() * 0x1000000) + 0xff000000);
						}
					}
					else
					{
						if (mSubscriptionUri != null)
						{
							SubscribedCalendars.disableCalendar(activity, mSubscriptionUri);
						}
						return null;
					}
				}

			}.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
		}

	}


	@Override
	public void onItemClick(AdapterView<?> listView, View view, int position, long id)
	{
		Cursor cursor = (Cursor) listView.getAdapter().getItem(position);

		Time start = new Time(cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TIMZONE)));
		start.set(cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTSTART)));
		start.allDay = cursor.getInt(cursor.getColumnIndex(WebCalReaderContract.Events.IS_ALLDAY)) != 0;

		Time end = new Time(cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TIMZONE)));
		end.set(cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTEND)));
		end.allDay = start.allDay;

		Event event = new Event(start, end, cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TITLE)), cursor.getString(cursor
			.getColumnIndex(WebCalReaderContract.Events.DESCRIPTION)), cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.LOCATION)));

		EventsPreviewActivity.show(getActivity(), event, mCalendarName, mIcon, mTitle);
	}

	/**
	 * Runnable that shows the progress indicator and clears any pager tabs when loading of the page takes longer than {@value #PROGRESS_INDICATOR_DELAY}
	 * milliseconds.
	 * 
	 * @see #PROGRESS_INDICATOR_DELAY
	 */
	private final Runnable mProgressIndicator = new Runnable()
	{
		@Override
		public void run()
		{
			mProgressBar.setVisibility(View.VISIBLE);
		}
	};


	@Override
	public String getItemTitle()
	{
		return mCalendarName;
	}


	@Override
	public long getItemIcon()
	{
		return mIcon;
	}

}
