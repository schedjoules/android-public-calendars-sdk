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

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscribedCalendars;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.MixedNavigationAdapter;
import org.dmfs.webcal.fragments.CategoriesListFragment.CategoryNavigator;

import android.app.ActionBar;
import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;


/**
 * A fragment that shows pages and items for the given URI. At present this is used for "All calendars" and "My calendars".
 * <p>
 * TODO: the synced calendars in "My Calendars" use a different id column. We need a better way to deal with that.
 * </p>
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class GenericListFragment extends ActionBarFragment implements OnItemClickListener, LoaderCallbacks<Cursor>
{
	private static final String TAG = "GenericListFragment";
	public static final String ARG_URI = "uri";
	public static final String ARG_TITLE = "title";
	public static final String ARG_EMPTY_MESSAGE = "empty_message";
	public static final String ARG_PROJECTION = "projection";
	public static final String ARG_SHOW_STARS = "show_stars";

	public final static String[] PROJECTION = new String[] { CalendarContentContract.ContentItem._ID,
		CalendarContentContract.SubscribedCalendars.CALENDAR_NAME, CalendarContentContract.ContentItem.TYPE, CalendarContentContract.ContentItem.ICON_ID,
		ContentItem.SEASON, ContentItem.STARRED, CalendarContentContract.SubscribedCalendars.ITEM_ID };

	public final static String[] PROJECTION2 = new String[] { CalendarContentContract.ContentItem._ID, CalendarContentContract.ContentItem.TITLE,
		CalendarContentContract.ContentItem.TYPE, CalendarContentContract.ContentItem.ICON_ID, ContentItem.SEASON, ContentItem.STARRED };

	@Parameter(key = ARG_URI)
	private Uri mUri;

	@Parameter(key = ARG_TITLE)
	private String mTitle;

	@Parameter(key = ARG_EMPTY_MESSAGE)
	private int mMessage;

	@Parameter(key = ARG_PROJECTION)
	private String[] mProjection;

	@Parameter(key = ARG_SHOW_STARS)
	private boolean mShowStars;

	private MixedNavigationAdapter mAdapter;
	private int mFirstItem;
	private int mPosFromTop;
	private ListView mListView;
	private TextView mMessageView;


	public static GenericListFragment newInstance(Uri uri, String title, int emptyMessage, String[] projection, boolean showStars)
	{
		GenericListFragment result = new GenericListFragment();
		Bundle args = new Bundle();
		args.putParcelable(ARG_URI, uri);
		args.putString(ARG_TITLE, title);
		args.putInt(ARG_EMPTY_MESSAGE, emptyMessage);
		args.putStringArray(ARG_PROJECTION, projection);
		args.putBoolean(ARG_SHOW_STARS, showStars);
		result.setArguments(args);
		return result;
	}


	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
	}


	@Override
	public void onPause()
	{
		super.onPause();
		mFirstItem = mListView.getFirstVisiblePosition();
		if (mFirstItem >= 0)
		{
			View firstChild = mListView.getChildAt(0);
			mPosFromTop = firstChild == null ? 0 : firstChild.getTop();
		}
	}


	@Override
	public void onResume()
	{
		super.onResume();
		if (mFirstItem >= 0)
		{
			mListView.setSelectionFromTop(mFirstItem, mPosFromTop);
		}
		setupActionBar();
	}


	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{

		View result = inflater.inflate(R.layout.generic_list, container, false);
		mListView = (ListView) result.findViewById(android.R.id.list);
		mMessageView = (TextView) result.findViewById(android.R.id.message);
		mAdapter = new MixedNavigationAdapter(getActivity(), null, 0, mShowStars);
		mAdapter.setShowMissingIcons(true);
		mListView.setAdapter(mAdapter);
		mListView.setOnItemClickListener(this);

		FragmentActivity activity = getActivity();

		/*
		 * FIXME: Using a random loader id is a hack. We just need to ensure that the loader id doesn't collide with another load id that might be visible.
		 */
		if (getParentFragment() != null)
		{
			getParentFragment().getLoaderManager().initLoader((int) (Math.random() * Integer.MAX_VALUE), null, this);
		}
		else
		{
			activity.getSupportLoaderManager().initLoader((int) (Math.random() * Integer.MAX_VALUE), null, this);
		}

		return result;
	}


	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args)
	{
		CursorLoader loader = new CursorLoader(getActivity(), mUri, mProjection, null, null, ContentItem.TITLE);
		return loader;
	}


	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
	{
		if (cursor.getCount() == 0)
		{
			mMessageView.setVisibility(View.VISIBLE);
			mListView.setVisibility(View.GONE);
			mMessageView.setText(mMessage);
		}
		else
		{
			mMessageView.setVisibility(View.GONE);
			mListView.setVisibility(View.VISIBLE);
			mAdapter.swapCursor(cursor);
		}
	}


	@Override
	public void onLoaderReset(Loader<Cursor> cursor)
	{
		mAdapter.swapCursor(null);
	}


	@Override
	public void onItemClick(AdapterView<?> adpView, View view, int position, long id)
	{
		Cursor cursor = (Cursor) adpView.getAdapter().getItem(position);
		String itemType = cursor.getString(2);
		String itemTitle = cursor.getString(1);
		long itemIcon = cursor.getLong(cursor.getColumnIndex(ContentItem.ICON_ID));
		if (CalendarContentContract.ContentItem.TYPE_PAGE.equals(itemType))
		{
			long selectedId = cursor.getLong(0);
			Activity activity = getActivity();
			if (activity instanceof CategoryNavigator)
			{
				((CategoryNavigator) activity).openCategory(selectedId, itemTitle, itemIcon);
			}

		}
		else if (CalendarContentContract.ContentItem.TYPE_CALENDAR.equals(itemType))
		{
			long selectedId = cursor.getLong(0);
			if (cursor.getColumnIndex(CalendarContentContract.SubscribedCalendars.ITEM_ID) >= 0)
			{
				selectedId = cursor.getLong(cursor.getColumnIndex(SubscribedCalendars.ITEM_ID));
			}
			Activity activity = getActivity();
			if (activity instanceof CategoryNavigator)
			{
				((CategoryNavigator) activity).openCalendar(selectedId, -1);
			}
		}
		else
		{
			Log.e(TAG, "Unknown type of entry");
		}
	}


	@Override
	public void setupActionBar()
	{
		if (getParentFragment() == null) // the topmost fragment owns the action bar
		{
			ActionBar ab = getActivity().getActionBar();
			ab.removeAllTabs();
			ab.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
			FragmentActivity activity = getActivity();
			activity.setTitle(mTitle);
			activity.getActionBar().setTitle(mTitle);
		}
	}

}
