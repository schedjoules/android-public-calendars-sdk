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

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.Toast;

import com.schedjoules.analytics.Analytics;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.retentionmagic.SupportFragment;
import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.android.retentionmagic.annotations.Retain;
import org.dmfs.webcal.R;
import org.dmfs.webcal.adapters.MixedNavigationAdapter;


/**
 * A fragment that shows all items of a specific section of a page. These items represent either pages or calendars.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class CategoriesListFragment extends SupportFragment implements OnItemClickListener, LoaderCallbacks<Cursor>
{
    public static final String ARG_SECTION_ID = "section_id";
    public static final String ARG_ITEM_ID = "item_id";
    public static final String ARG_SECTION_POS = "section_pos";
    public static final String ARG_ICON_ID = "icon_id";

    @Parameter(key = ARG_SECTION_ID)
    private long mSectionId;

    @Parameter(key = ARG_ITEM_ID)
    private long mItemId;

    @Parameter(key = ARG_SECTION_POS)
    private int mSectionPos;

    @Parameter(key = ARG_ICON_ID)
    private long mCategoryIconId;

    private MixedNavigationAdapter mAdapter;
    private int mFirstItem;
    private int mPosFromTop;
    private ListView mListView;

    @Retain
    private Uri mPurchasedItem = null;


    public static CategoriesListFragment newInstance(long sectionId, long parentItemId, int sectionPos, long iconId)
    {
        CategoriesListFragment fragment = new CategoriesListFragment();

        Bundle args = new Bundle();
        args.putLong(CategoriesListFragment.ARG_SECTION_ID, sectionId);
        args.putLong(CategoriesListFragment.ARG_ITEM_ID, parentItemId);
        args.putInt(CategoriesListFragment.ARG_SECTION_POS, sectionPos);
        args.putLong(ARG_ICON_ID, iconId);
        fragment.setArguments(args);

        return fragment;
    }


    @Override
    public void onResume()
    {
        super.onResume();
        if (mFirstItem >= 0)
        {
            mListView.setSelectionFromTop(mFirstItem, mPosFromTop);
        }
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {

        mListView = (ListView) inflater.inflate(R.layout.categories_list, container, false);
        mAdapter = new MixedNavigationAdapter(getActivity(), null, 0, false);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(this);

		/*
         * Apparently we have to use the parent loader manager.
		 * 
		 * - using just getLoaderManager() doesn't work, because it doesn't seem to start before the fragemnt becomes visible, which is bad when you swipe
		 * 
		 * - using getActivity().getSupportLoaderManager() doesn't work because it leaks the loaders and fragments
		 * 
		 * For now we keep it that way until we find a proper solution
		 */
        LoaderManager loaderManager = getParentFragment().getLoaderManager();
        loaderManager.initLoader((int) (mSectionId), null, this);
        return mListView;
    }


    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args)
    {
        Uri uri = CalendarContentContract.Section.getItemContentUri(getActivity(), mSectionId);
        return new CursorLoader(getActivity(), uri, MixedNavigationAdapter.PROJECTION, null, null, null);
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
    public void onLoadFinished(Loader<Cursor> loader, Cursor cursor)
    {
        mAdapter.swapCursor(cursor);
    }


    @Override
    public void onLoaderReset(Loader<Cursor> cursor)
    {
        mAdapter.swapCursor(null);
    }


    @Override
    public void onItemClick(AdapterView<?> adpView, View view, int position, long id)
    {
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        String itemType = cursor.getString(2);
        String itemTitle = cursor.getString(1);
        long itemIcon = cursor.getLong(cursor.getColumnIndex(ContentItem.ICON_ID));
        long selectedId = cursor.getLong(0);
        Analytics
                .event("item-clicked", "navigate", null, String.valueOf(ContentItem.getApiId(mItemId)), String.valueOf(ContentItem.getApiId(selectedId)), null);
        if (itemType.equals(CalendarContentContract.ContentItem.TYPE_PAGE))
        {
            Activity activity = getActivity();
            if (activity instanceof CategoryNavigator)
            {
                ((CategoryNavigator) activity).openCategory(selectedId, itemTitle, itemIcon);
            }

        }
        else if (itemType.equals(CalendarContentContract.ContentItem.TYPE_CALENDAR))
        {
            Activity activity = getActivity();
            if (activity instanceof CategoryNavigator)
            {
                ((CategoryNavigator) activity).openCalendar(selectedId, itemIcon > 0 ? itemIcon : mCategoryIconId);
            }
        }
        else
        {
            Toast.makeText(getActivity(), "Unknown type of entry", Toast.LENGTH_SHORT).show();
        }
    }


    public long getSectionId()
    {
        return mSectionId;
    }


    public int getSectionPos()
    {
        return mSectionPos;
    }


    /**
     * An interface to be implemented by the parent activity of this fragment. It provides methods to navigate to a specific page or calendar.
     */
    public interface CategoryNavigator
    {
        public void openCategory(long id, String title, long icon);

        public void openCalendar(long id, long icon);
    }
}
