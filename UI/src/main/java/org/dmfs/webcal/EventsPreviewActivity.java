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

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.design.widget.CollapsingToolbarLayout;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;

import org.dmfs.android.retentionmagic.annotations.Parameter;
import org.dmfs.webcal.IBillingActivity.OnInventoryListener;
import org.dmfs.webcal.fragments.EventsPreviewDetailFragment;
import org.dmfs.webcal.utils.Event;
import org.dmfs.webcal.utils.billing.IabHelper;
import org.dmfs.webcal.utils.billing.Inventory;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


/**
 * An Activity that presents the details of an event to the user.
 *
 * @author Marten Gajda <marten@dmfs.org>
 */
public class EventsPreviewActivity extends ActionBarActivity
{
    private final static int REQUEST_CODE_LAUNCH_PURCHASE_FLOW = 10004;
    private final static long MAX_INVENTORY_AGE = 90L * 60L * 1000L; // 90 minutes

    private static final String EXTRA_CALENDAR_NAME = "org.dmfs.webcal.EventsPreviewActivity.CALENDAR_NAME";
    private static final String EXTRA_CALENDAR_IMAGE = "org.dmfs.webcal.EventsPreviewActivity.CALENDAR_IMAGE_URL";
    private static final String EXTRA_PREVIEW_EVENT = "org.dmfs.webcal.EventsPreviewActivity.PREVIEW_EVENT";
    private static final String EXTRA_PAGE_TITLE = "org.dmfs.webcal.EventsPreviewActivity.PAGE_TITLE";
    private static final String EXTRA_CONTENT_ITEM_URI = "org.dmfs.webcal.EventsPreviewActivity.CONTENT_ITEM_URI";

    private static final String CONTENT_TYPE_EVENT = "vnd.android.cursor.item/event";
    @Parameter(key = EXTRA_PREVIEW_EVENT)
    Event mEvent = null;
    @Parameter(key = EXTRA_CALENDAR_NAME)
    private String mCalendarName;
    @Parameter(key = EXTRA_CALENDAR_IMAGE)
    private long mCalendarIconId;
    @Parameter(key = EXTRA_PAGE_TITLE)
    private String mTitle = null;

    @Parameter(key = EXTRA_CONTENT_ITEM_URI)
    private Uri mContentItemUri;

    /*
     * The fields below have been copied from {@link MainActivity}. We should find a way to remove that duplicate code.
     */
    private List<WeakReference<OnInventoryListener>> mBillingCallbacks = null;
    private List<String> mMoreItemSkus = Collections.synchronizedList(new ArrayList<String>());
    private IabHelper mIabHelper;
    private boolean mIabHelperReady = false;
    private Inventory mInventoryCache = null;
    private long mInventoryTime = 0;


    /**
     * Shows a details view for the given event.
     *
     * @param context
     *         A {@link Context}.
     * @param event
     *         The event to present.
     * @param calendarName
     *         The name of the calendar.
     * @param mIconId
     *         An icon id.
     * @param title
     *         The title of the page.
     */
    public static void show(Context context, Event event, String calendarName, long mIconId, String title, Uri contentItemUri)
    {
        Intent intent = new Intent(context, EventsPreviewActivity.class);
        intent.putExtra(EXTRA_CALENDAR_NAME, calendarName);
        intent.putExtra(EXTRA_CALENDAR_IMAGE, mIconId);
        intent.putExtra(EXTRA_PREVIEW_EVENT, event);
        intent.putExtra(EXTRA_PAGE_TITLE, title);
        intent.putExtra(EXTRA_CONTENT_ITEM_URI, contentItemUri);
        context.startActivity(intent);
    }


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_events_preview);

        if (savedInstanceState == null)
        {
            FragmentManager fragmentManager = getSupportFragmentManager();
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.add(R.id.events_preview_fragment_container, EventsPreviewDetailFragment.newInstance(mEvent, mCalendarName, mCalendarIconId, mTitle));
            transaction.commit();
        }

        final Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();

        final AppBarLayout appBar = (AppBarLayout) findViewById(R.id.appbar);

        CollapsingToolbarLayout collapsingToolbarLayout = (CollapsingToolbarLayout) findViewById(R.id.collapsing_toolbar);
        collapsingToolbarLayout.setTitle(mEvent.title);
        actionBar.setDisplayHomeAsUpEnabled(true);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        switch (item.getItemId())
        {
            case android.R.id.home:
                // Respond to the action bar's Up/Home button imitating a back button press
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

}
