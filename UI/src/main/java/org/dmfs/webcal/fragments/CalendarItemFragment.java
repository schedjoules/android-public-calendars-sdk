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

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.format.DateUtils;
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

import com.schedjoules.analytics.Analytics;

import org.apache.http.message.BasicHeader;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.PaymentStatus;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscribedCalendars;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscriptionId;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.webcalreader.provider.WebCalReaderContract;
import org.dmfs.asynctools.PetriNet;
import org.dmfs.asynctools.PetriNet.Place;
import org.dmfs.asynctools.PetriNet.Transition;
import org.dmfs.rfc5545.DateTime;
import org.dmfs.webcal.EventsPreviewActivity;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.EventListAdapter;
import org.dmfs.webcal.adapters.SectionTitlesAdapter;
import org.dmfs.webcal.adapters.SectionTitlesAdapter.SectionIndexer;
import org.dmfs.webcal.fragments.CalendarTitleFragment.SwitchStatusListener;
import org.dmfs.webcal.utils.Event;
import org.dmfs.webcal.utils.ProtectedBackgroundJob;

import java.net.URI;
import java.util.TimeZone;


public class CalendarItemFragment extends SubscribeableItemFragment implements LoaderManager.LoaderCallbacks<Cursor>, SwitchStatusListener, OnItemClickListener
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
	private static final int LOADER_SUBSCRIPTION = LOADER_CALENDAR_ITEM + 2;
	private static final int LOADER_PREVIEW = LOADER_CALENDAR_ITEM + 3;

	/**
	 * The projection we use when loading the calendar item.
	 */
	private final static String[] PROJECTION = new String[] { CalendarContentContract.ContentItem.TITLE, CalendarContentContract.ContentItem.ICON_ID,
		CalendarContentContract.ContentItem.URL, ContentItem.STARRED, ContentItem._ID };
	private final Place mWaitingForCalendarItem = new Place(1);
	private final Place mWaitingForCalendarSubscription = new Place(1);
	private final Place mWaitingForPayment = new Place(1);
	private final Place mDone = new Place();
	/**
	 * This {@link Transition} is fired whenever the payment status changes or when a free trial is started or ends.
	 */
	private Transition<Void> mPaymentStatusUpdated = new Transition<Void>(mWaitingForPayment, mWaitingForPayment, mDone)
	{
		@Override
		protected void execute(Void data)
		{
			// nothing to do, this just enables the mDone place to update the UI
		}
	};
	private final Handler mHandler = new Handler();
	/**
	 * Runnable that shows the progress indicator loading of the calendar takes longer than {@value #PROGRESS_INDICATOR_DELAY} milliseconds.
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
	/**
	 * The id of the calendar item.
	 */
	private long mId;
	/**
	 * The content {@link Uri} of the calendar item.
	 */
	@Parameter(key = ARG_CONTENT_URI)
	private Uri mContentUri;
	/**
	 * The title of the calendar item.
	 */
	@Parameter(key = ARG_TITLE)
	private String mTitle;
	/**
	 * The icon is initialized with the icon passed to {@link #newInstance(Uri, String, long)} or {@link #newInstance(Context, long, String, long)}. If the
	 * calendar has it's own icon, it replaces the initial icon later on.
	 */
	@Parameter(key = ARG_ICON)
	private long mIcon;
	/**
	 * The current sync status of the calendar.
	 */
	private boolean mSynced = false;
	/**
	 * The current favorite status of the calendar.
	 */
	private boolean mStarred;
	/**
	 * The {@link Uri} of the calendar subscription, if there is any.
	 */
	private Uri mSubscriptionUri;
	/**
	 * A {@link Transition} that's fired whenever the calendar subscription has been loaded.
	 */
	private Transition<Cursor> mSubscriptionLoaded = new Transition<Cursor>(mWaitingForCalendarSubscription, mWaitingForCalendarSubscription, mDone)
	{

		@Override
		protected void execute(Cursor cursor)
		{
			// the subscription has been loaded (or not)
			if (cursor != null && cursor.moveToFirst())
			{
				// we have a subscription

				// get the id
				long id = cursor.getLong(cursor.getColumnIndex(SubscribedCalendars._ID));

				// build the Uri
				mSubscriptionUri = SubscribedCalendars.getItemContentUri(getActivity(), id);

				// get the sync status
				mSynced = cursor.getInt(cursor.getColumnIndex(SubscribedCalendars.SYNC_ENABLED)) > 0;
			}

			// set the sync switch accordingly
			mTitleFragment.setSwitchChecked(mSynced);

			// invalidate options to show/hide settings option
			getActivity().invalidateOptionsMenu();
		}
	};
	/**
	 * The name of the calendar.
	 */
	private String mCalendarName;
	/**
	 * The actual URL of the calendar data.
	 */
	private URI mCalendarUrl;
	private CalendarTitleFragment mTitleFragment;
	private ProgressBar mProgressBar;
	private EventListAdapter mListAdapter;
	private ActionBar mActionBar;
	/**
	 * This {@link Transition} is fired whenever the calendar item is (re-) loaded.
	 */
	private Transition<Cursor> mItemLoaded = new Transition<Cursor>(mWaitingForCalendarItem, mWaitingForCalendarItem)
	{

		@Override
		protected void execute(Cursor cursor)
		{
			if (cursor != null && cursor.moveToFirst())
			{
				/*
				 * We have an item.
				 * 
				 * Get the values and update the UI as good as we can.
				 */

				// load the icon if there is any
				long iconId = cursor.getLong(COLUMNS.ICON);
				if (iconId > 0)
				{
					mIcon = iconId;
				}

				// load the calendar title
				mCalendarName = cursor.getString(COLUMNS.TITLE);

				// get the calendar URL
				try
				{
					mCalendarUrl = URI.create(cursor.getString(COLUMNS.URL));
				}
				catch (IllegalArgumentException e)
				{
					// TODO: not a valid URI, we shouldn't continue
				}

				mStarred = cursor.getInt(COLUMNS.STARRED) > 0;

				// update the UI
				mTitleFragment.setTitle(mCalendarName);
				mTitleFragment.setIcon(mIcon);
				mTitleFragment.setId(mId);
				mTitleFragment.setStarred(mStarred);

				// update action bar
				if (mActionBar != null)
				{
					mActionBar.setTitle(mCalendarName);
				}

				// update the adapter if necessary
				if (mListAdapter.getCursor() == null)
				{
					loadCalendar();
				}
			}
		}
	};
	private SectionTitlesAdapter mSectionAdapter;
	private ListView mListView;
	/**
	 * This {@link Transition} is fired after {@link #mPaymentStatusUpdated} and {@link #mSubscriptionLoaded} are fired.
	 */
	private Transition<Void> mEnableSwitch = new Transition<Void>(2, mDone, 1, mDone)
	{
		@Override
		protected void execute(Void data)
		{
			mTitleFragment.enableSwitch(isPurchased() || inFreeTrial());
		}
	}.setAutoFire(true);
	/**
	 * The {@link PetriNet} engine.
	 */
	private PetriNet mPetriNet = new PetriNet(mPaymentStatusUpdated, mItemLoaded, mSubscriptionLoaded, mEnableSwitch);


	public CalendarItemFragment()
	{
		// Obligatory unparameterized constructor
	}


	/**
	 * Create a new CalendarItemFragment.
	 *
	 * @param contentUri
	 *            The content {@link Uri} of the calendar item.
	 * @param title
	 *            The title of the page.
	 * @param icon
	 *            The icon id of the calendar.
	 * @return The {@link CalendarItemFragment}.
	 */
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


	/**
	 * Create a new CalendarItemFragment.
	 *
	 * @param context
	 *            A {@link Context}.
	 * @param id
	 *            The calendar item id.
	 * @param title
	 *            The title of the page.
	 * @param icon
	 *            The icon id of the calendar.
	 * @return The {@link CalendarItemFragment}.
	 */
	public static CalendarItemFragment newInstance(Context context, long id, String title, long icon)
	{
		return newInstance(ContentItem.getItemContentUri(context, id), title, icon);
	}


	@Override
	public View onCreateItemView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View returnView = inflater.inflate(R.layout.fragment_calendar_item, container, false);

		// TODO: don't put the progress indicator into the header view
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
				DateTime start = new DateTime(TimeZone.getDefault(), (index >> 16) & 0x0ffff, (index >> 8) & 0x00ff, index & 0x00ff, 0, 0, 0);

				return DateUtils.formatDateTime(getActivity(), start.getTimestamp(),
					DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_WEEKDAY);
			}


			@Override
			public int getSectionIndex(Object object)
			{
				Cursor cursor = (Cursor) object;

				DateTime start = new DateTime(TimeZone.getTimeZone(cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TIMZONE))),
					cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTSTART)));
				// we return an encoded date as index
				return (start.getYear() << 16) + (start.getMonth() << 8) + start.getDayOfMonth();

			}
		}, R.layout.events_preview_list_section_header));

		FragmentManager fm = getChildFragmentManager();
		FragmentTransaction ft = fm.beginTransaction();

		mTitleFragment = (CalendarTitleFragment) fm.findFragmentById(R.id.calendar_title_fragment_container);
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
		lm.initLoader(LOADER_SUBSCRIPTION, null, this);

		// set this to true, so the menu is cleared automatically when leaving the fragment, otherwise the star icon will stay visible
		setHasOptionsMenu(true);

		return returnView;
	}


	@Override
	public void onStart()
	{
		super.onStart();
		mId = ContentUris.parseId(mContentUri);

		Analytics.screen(mTitle + "/calendar_preview", null, String.valueOf(ContentItem.getApiId(mId)));
	}


	@Override
	public void onResume()
	{
		super.onResume();

		// setup action bar, we do that here to be sure that it already has been created
		mActionBar = ((ActionBarActivity) getActivity()).getSupportActionBar();
		mActionBar.setTitle(mCalendarName);
	}


	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);
		inflater.inflate(R.menu.calendar_item, menu);

		// only show options if the calendar is synced
		menu.findItem(R.id.menu_settings).setVisible(mSynced);
	}


	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		int id = item.getItemId();
		if (id == R.id.menu_settings)
		{
			Analytics.screen("calendar-settings", null, String.valueOf(ContentItem.getApiId(mId)));

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
				return new CursorLoader(activity, SubscribedCalendars.getContentUri(activity), null,
					SubscribedCalendars.ITEM_ID + "=" + ContentUris.parseId(mContentUri), null, null);

			case LOADER_PREVIEW:
				// show the loader indicator delayed
				mHandler.postDelayed(mProgressIndicator, PROGRESS_INDICATOR_DELAY);
				if (mCalendarUrl != null)
				{
					return new CursorLoader(getActivity(),
						WebCalReaderContract.Events.getEventsUri(getActivity(), mCalendarUrl, 60 * 1000, new BasicHeader("X-Context", "preview")), null, null,
						null, null);
				}
				else
				{
					return null;
				}

			case LOADER_SUBSCRIPTION:
				return new CursorLoader(activity, PaymentStatus.getContentUri(activity), null, null, null, null);

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

				// this appears to be necessary for some reason, even though the adapter should know that the data set has changed
				mListAdapter.notifyDataSetChanged();

				if (oldCursor == null || oldCursor.getCount() == 0)
				{
					goToToday();
				}
				break;

			case LOADER_SUBSCRIPTION:

				mHandler.post(new Runnable()
				{
					@Override
					public void run()
					{
						CalendarItemFragment.this.setPurchaseableItem(cursor);
					}
				});
				break;
		}
	}


	private void goToToday()
	{
		DateTime now = DateTime.nowAndHere();
		int nowIdx = (now.getYear() << 16) + (now.getMonth() << 8) + now.getDayOfMonth();

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

		// all events in the past, go to the end of the list
		mListView.setSelectionFromTop(mSectionAdapter.getCount() - 1, 0);
	}


	@Override
	public void onLoaderReset(Loader<Cursor> loader)
	{
		// at present we don't do anything here
	}


	@Override
	public boolean onSyncSwitchToggle(boolean status)
	{
		if (isResumed() && status != mSynced)
		{
			if (isPurchased() || inFreeTrial())
			{
				Analytics.event("sync-enabled", "calendar-action", Boolean.toString(status), null, String.valueOf(ContentItem.getApiId(mId)), null);
				setCalendarSynced(status);
			}
			else if (!status)
			{
				Analytics.event("sync-enabled", "calendar-action", Boolean.toString(status), null, String.valueOf(ContentItem.getApiId(mId)), null);
				setCalendarSynced(false);
				mTitleFragment.enableSwitch(isPurchased());
			}
		}
		return false;
	};


	@Override
	public void onPurchase(boolean success, boolean freeTrial)
	{
		if (success)
		{
			setCalendarSynced(true);
		}
		else
		{
			mTitleFragment.setSwitchChecked(mSynced && inFreeTrial());
		}
	}


	private void setCalendarSynced(final boolean status)
	{
		if (status && (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED)
			|| ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED
			|| ContextCompat.checkSelfPermission(getContext(), Manifest.permission.GET_ACCOUNTS) != PackageManager.PERMISSION_GRANTED)
		{
			ActivityCompat.requestPermissions(getActivity(),
				new String[] { Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR, Manifest.permission.GET_ACCOUNTS }, 1);
			return;
		}

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
					mSynced = status;
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
	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if (requestCode == 1 && resultCode == Activity.RESULT_OK)
		{
			setCalendarSynced(true);
		}
		super.onActivityResult(requestCode, resultCode, data);
	}


	@Override
	public void onItemClick(AdapterView<?> listView, View view, int position, long id)
	{
		Cursor cursor = (Cursor) listView.getAdapter().getItem(position);

		DateTime start = new DateTime(TimeZone.getTimeZone(cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TIMZONE))),
			cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTSTART)));

		DateTime end = new DateTime(TimeZone.getTimeZone(cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TIMZONE))),
			cursor.getLong(cursor.getColumnIndex(WebCalReaderContract.Events.DTEND)));

		if (cursor.getInt(cursor.getColumnIndex(WebCalReaderContract.Events.IS_ALLDAY)) != 0)
		{
			start = start.toAllDay();
			end = end.toAllDay();
		}

		Event event = new Event(start, end, cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.TITLE)),
			cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.DESCRIPTION)),
			cursor.getString(cursor.getColumnIndex(WebCalReaderContract.Events.LOCATION)));

		Context context = getActivity();
		EventsPreviewActivity.show(context, event, mCalendarName, mIcon, mTitle, mContentUri);
	}


	@Override
	public String getItemTitle()
	{
		return mCalendarName;
	}


	public void loadCalendar()
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


	@Override
	public String getGoogleSubscriptionId()
	{
		return SubscriptionId.getSubscriptionId(getActivity());
	}


	@Override
	public void onPaymentStatusChange()
	{
		mSynced &= (isPurchased() || inFreeTrial());
		mPetriNet.fire(mPaymentStatusUpdated, null);
		getActivity().invalidateOptionsMenu();
	}

	/**
	 * Column indices of the {@link CalendarItemFragment#PROJECTION} we use.
	 */
	private interface COLUMNS
	{
		int TITLE = 0;
		int ICON = 1;
		int URL = 2;
		int STARRED = 3;
		@SuppressWarnings("unused")
		int ID = 4;
	}
}
