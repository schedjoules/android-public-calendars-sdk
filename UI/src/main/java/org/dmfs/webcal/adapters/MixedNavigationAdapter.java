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

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.TextView;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.ContentItem;
import org.dmfs.android.calendarcontent.provider.CalendarContentContract.SubscribedCalendars;
import org.dmfs.webcal.R;
import org.dmfs.webcal.utils.TintedDrawable;
import org.dmfs.webcal.utils.color.AccentColor;
import org.dmfs.webcal.views.RemoteImageView;

import androidx.cursoradapter.widget.CursorAdapter;


/**
 * A {@link CursorAdapter} that is used for the page & calendar listings. This is also used for "my calendars", which makes it a bit awkward because this
 * section uses a different content URI with slightly different columns.
 * <p>
 * TOOD: find a generic way to handle the issue above.
 * </p>
 *
 * @author Arjun Naik <arjun@arjunnaik.in>
 * @author Marten Gajda <marten@dmfs.org>
 */
public class MixedNavigationAdapter extends CursorAdapter
{

    public final static String[] PROJECTION = new String[] {
            CalendarContentContract.ContentItem._ID, CalendarContentContract.ContentItem.TITLE,
            CalendarContentContract.ContentItem.TYPE, CalendarContentContract.ContentItem.ICON_ID, ContentItem.SEASON, ContentItem.STARRED };

    private LayoutInflater mInflater;
    private boolean mShowMissingIcons = false;
    private final boolean mShowStars;


    public MixedNavigationAdapter(Context context, Cursor c, int flags, boolean showStars)
    {
        super(context, c, flags);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mShowStars = showStars;
    }


    public void setShowMissingIcons(boolean showMissingIcons)
    {
        mShowMissingIcons = showMissingIcons;
    }


    @Override
    public int getViewTypeCount()
    {
        return 2;
    }


    @Override
    public int getItemViewType(int position)
    {
        Cursor cursor = (Cursor) getItem(position);
        if (cursor != null)
        {
            if (CalendarContentContract.ContentItem.TYPE_CALENDAR.equals(cursor.getString(2)))
            {
                return 0;
            }
            else
            {
                return 1;
            }
        }
        return 0;
    }


    @Override
    public void bindView(View view, final Context context, final Cursor cursor)
    {
        final long id = cursor.getLong(cursor.getColumnIndex(SubscribedCalendars.ITEM_ID) >= 0 ? cursor.getColumnIndex(SubscribedCalendars.ITEM_ID) : cursor
                .getColumnIndex(ContentItem._ID));
        String itemType = cursor.getString(2);
        String season = cursor.getString(4);
        long img = cursor.getLong(3);
        if (CalendarContentContract.ContentItem.TYPE_PAGE.equals(itemType))
        {
            TextView title = (TextView) view.findViewById(android.R.id.title);
            TextView subtitle = (TextView) view.findViewById(android.R.id.text1);
            title.setText(cursor.getString(1));
            subtitle.setText(TextUtils.isEmpty(season) ? null : context.getString(R.string.season, season));
            title.setSelected(true);
            RemoteImageView icon = (RemoteImageView) view.findViewById(android.R.id.icon);
            icon.setRemoteSource(img, mShowMissingIcons);
        }
        else
        {
            TextView title = (TextView) view.findViewById(android.R.id.title);
            TextView subtitle = (TextView) view.findViewById(android.R.id.text1);
            RemoteImageView image = (RemoteImageView) view.findViewById(android.R.id.icon);
            subtitle.setText(TextUtils.isEmpty(season) ? null : context.getString(R.string.season, season));
            title.setText(cursor.getString(1));
            image.setRemoteSource(img, mShowMissingIcons);
        }

        TextView status = (TextView) view.findViewById(android.R.id.text2);
        // if (status != null)
        // {
        // if (!TextUtils.isEmpty(cursor.getString(4)) || !TextUtils.isEmpty(cursor.getString(8)))
        // {
        // status.setText(R.string.status_unlocked);
        // }
        // else if (TextUtils.isEmpty(cursor.getString(6)) && !CalendarContentContract.ContentItem.TYPE_PAGE.equals(itemType))
        // {
        // status.setText(R.string.status_free);
        // }
        // else if ("".equals(cursor.getString(5)))
        // {
        // status.setText(R.string.status_free);
        // }
        // else if (cursor.getLong(7) > System.currentTimeMillis())
        // {
        // status.setText(R.string.status_free_trial);
        // }
        // else
        // {
        // status.setText("");
        // TextView price = (TextView) view.findViewById(R.id.content_item_price);
        // if (price != null && price.getVisibility() == View.VISIBLE)
        // {
        // price.setText(cursor.getString(9));
        // }
        // }
        // }

        CheckBox starred = (CheckBox) view.findViewById(R.id.menu_starred);
        if (starred != null)
        {
            if (mShowStars)
            {
                starred.setVisibility(View.VISIBLE);
                starred.setOnCheckedChangeListener(null);
                starred.setChecked(cursor.getInt(5) > 0);
                starred.setButtonDrawable(new TintedDrawable(context, R.drawable.star_selector, new AccentColor(context)).value());
                starred.setOnCheckedChangeListener(new OnCheckedChangeListener()
                {

                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
                    {
                        ContentItem.setStarred(context, id, isChecked);
                    }
                });
            }
            else
            {
                starred.setVisibility(View.GONE);
            }

        }
    }


    @Override
    public View newView(Context context, Cursor cursor, ViewGroup vg)
    {
        String itemType = cursor.getString(2);
        if (CalendarContentContract.ContentItem.TYPE_PAGE.equals(itemType))
        {
            return mInflater.inflate(R.layout.page_entry_item, vg, false);
        }
        else
        {
            return mInflater.inflate(R.layout.calendar_entry_item, vg, false);
        }
    }
};
