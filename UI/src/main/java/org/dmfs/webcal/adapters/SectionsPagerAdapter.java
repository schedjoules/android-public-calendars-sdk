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

package org.dmfs.webcal.adapters;

import android.database.Cursor;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.webcal.fragments.CategoriesListFragment;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;


/**
 * A pager adapter for the sections of a specific page.
 *
 * @author Arjun Naik <arjun@arjunnaik.in>
 * @author Marten Gajda <marten@dmfs.org>
 */
public class SectionsPagerAdapter extends FragmentStatePagerAdapter
{
    /**
     * The projection this class expects when it loads the values from the cursor.
     */
    public final static String[] PROJECTION = new String[] {
            CalendarContentContract.Section._ID, CalendarContentContract.Section.TITLE,
            CalendarContentContract.Section.PARENT_ITEM };

    private Cursor mSectionsCursor;
    private long mPageIcon;


    public SectionsPagerAdapter(FragmentManager fm, long pageIcon)
    {
        super(fm);
        mPageIcon = pageIcon;
    }


    @Override
    public int getItemPosition(Object object)
    {
        // check if the fragment is still in the right position
        if (object instanceof CategoriesListFragment)
        {
            CategoriesListFragment fragment = (CategoriesListFragment) object;
            if (mSectionsCursor != null && mSectionsCursor.moveToPosition(fragment.getSectionPos()))
            {
                // check if the ids are still the same
                return fragment.getSectionId() == mSectionsCursor.getLong(0) ? POSITION_UNCHANGED : POSITION_NONE;
            }
        }
        return POSITION_NONE;
    }


    @Override
    public Fragment getItem(int position)
    {
        if (mSectionsCursor != null && mSectionsCursor.moveToPosition(position))
        {
            // return a new CategoriesListFragment for this section
            long sectionId = mSectionsCursor.getLong(0);
            long parentItemId = mSectionsCursor.getLong(2);
            return CategoriesListFragment.newInstance(sectionId, parentItemId, position, mPageIcon);
        }
        return null;
    }


    @Override
    public CharSequence getPageTitle(int position)
    {
        if (mSectionsCursor != null && mSectionsCursor.moveToPosition(position))
        {
            return mSectionsCursor.getString(1);
        }
        else
        {
            return null;
        }
    }


    @Override
    public int getCount()
    {
        if (mSectionsCursor != null)
        {
            return mSectionsCursor.getCount();
        }
        return 0;
    }


    /**
     * Replace the current cursor with a new one and update the pages.
     *
     * @param cursor
     *         The new Cursor or <code>null</code>.
     */
    public void swapCursor(Cursor cursor)
    {
        mSectionsCursor = cursor;

        if (cursor != null)
        {
            notifyDataSetChanged();
        }
    }

}
