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
import android.content.res.Configuration;
import android.database.DataSetObserver;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.widget.Toolbar;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.TextView;


public abstract class NavbarActivity extends ActionBarActivity implements ListView.OnItemClickListener
{
    protected static final String TAG = "NavbarActivity";
    protected DrawerLayout mDrawerLayout;
    private ListView mDrawerList;
    private ActionBarDrawerToggle mDrawerToggle;


    protected void setupNavbar(Toolbar toolbar)
    {
        mDrawerLayout = (DrawerLayout) findViewById(R.id.drawer_layout);
        mDrawerList = (ListView) findViewById(R.id.left_drawer);

        // this is a hack to get a Menu
        Menu menu = new PopupMenu(this, null).getMenu();
        getMenuInflater().inflate(R.menu.side_navigation, menu);

        // remove invisible menu items
        int itemCount = menu.size();
        int i = 0;
        while (i < itemCount)
        {
            MenuItem item = menu.getItem(i);
            if (!item.isVisible())
            {
                menu.removeItem(item.getItemId());
                --itemCount;
            }
            else
            {
                ++i;
            }
        }

        MenuAdapter menuAdapter = new MenuAdapter(this, R.layout.navdrawer_item, android.R.id.title, menu);
        mDrawerList.setAdapter(menuAdapter);

        mDrawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close)
        {
            @Override
            public void onDrawerClosed(View drawerView)
            {
                String activityTitle = getActivityTitle();
                setTitle(activityTitle);
                invalidateOptionsMenu();
                super.onDrawerClosed(drawerView);
            }


            @Override
            public void onDrawerOpened(View drawerView)
            {
                setTitle(R.string.app_name);
                invalidateOptionsMenu();
                super.onDrawerOpened(drawerView);
            }
        };
        mDrawerLayout.setDrawerShadow(R.drawable.drawer_shadow, GravityCompat.START);
        mDrawerLayout.setDrawerListener(mDrawerToggle);
        mDrawerList.setOnItemClickListener(this);

    }


    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        mDrawerToggle.syncState();
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        super.onConfigurationChanged(newConfig);
        mDrawerToggle.onConfigurationChanged(newConfig);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (mDrawerToggle.onOptionsItemSelected(item))
        {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)
    {
        if (selectItem(id))
        {
            setNavigationSelection(position);
        }
        else
        {
            setNavigationSelection(-1);
        }
    }


    protected boolean selectItem(long id)
    {
        mDrawerLayout.closeDrawer(mDrawerList);
        return false;
    }


    protected void setNavigationSelection(int pos)
    {
        mDrawerList.setItemChecked(pos, true);
    }


    protected void setNavigationSelectionById(long id)
    {
        int count = mDrawerList.getCount();
        for (int i = 0; i < count; ++i)
        {
            if (mDrawerList.getItemIdAtPosition(i) == id)
            {
                mDrawerList.setItemChecked(i, true);
                return;
            }
        }
    }


    protected void openDrawer()
    {
        mDrawerLayout.openDrawer(mDrawerList);
    }


    protected String getItemTitle(int pos)
    {
        return (String) ((MenuItem) mDrawerList.getAdapter().getItem(pos)).getTitle();
    }


    protected String getItemTitleById(long id)
    {
        int count = mDrawerList.getCount();
        for (int i = 0; i < count; ++i)
        {
            if (mDrawerList.getItemIdAtPosition(i) == id)
            {
                return (String) ((MenuItem) mDrawerList.getAdapter().getItem(i)).getTitle();
            }
        }
        return null;
    }


    protected abstract String getActivityTitle();


    private final class MenuAdapter implements ListAdapter
    {

        private final int mResource;
        private final int mTextViewResourceId;
        private final Context mContext;
        private final Menu mMenu;
        private final LayoutInflater mInflater;


        public MenuAdapter(Context context, int resource, int textViewResourceId, Menu menu)
        {
            mResource = resource;
            mTextViewResourceId = textViewResourceId;
            mContext = context.getApplicationContext();
            mMenu = menu;
            mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        }


        @Override
        public View getView(int position, View convertView, ViewGroup parent)
        {
            int category = mMenu.getItem(position).getOrder();

            View result = convertView;
            if (result == null)
            {

                if ((category & Menu.CATEGORY_SECONDARY) == 0)
                {
                    result = mInflater.inflate(mResource, parent, false);
                }
                else
                {
                    result = mInflater.inflate(R.layout.navdrawer_item_secondary, parent, false);
                    View divider = result.findViewById(R.id.optional_nav_divider);
                    if (divider != null && position > 0)
                    {
                        // enable divider if previous element is not a secondary item
                        divider.setVisibility((mMenu.getItem(position - 1).getOrder() & Menu.CATEGORY_SECONDARY) == 0 ? View.VISIBLE : View.GONE);
                    }
                }
            }

            TextView titleView = (TextView) result.findViewById(mTextViewResourceId);
            if (titleView != null)
            {
                MenuItem item = (MenuItem) getItem(position);
                if ((category & Menu.CATEGORY_SECONDARY) == 0)
                {
                    titleView.setTextAppearance(mContext, mDrawerList.getCheckedItemPosition() == position ? R.style.navigation_text_selected
                            : R.style.navigation_text_normal);
                }
                titleView.setText(item.getTitle());

                Drawable icon = item.getIcon();
                if (icon != null)
                {
                    DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
                    icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
                    titleView.setCompoundDrawables(icon, null, null, null);
                    titleView.setCompoundDrawablePadding((int) (displayMetrics.density * 8));
                }
            }

            return result;
        }


        @Override
        public int getCount()
        {
            return mMenu.size();
        }


        @Override
        public Object getItem(int pos)
        {
            return mMenu.getItem(pos);
        }


        @Override
        public long getItemId(int pos)
        {
            if (0 <= pos && pos < mMenu.size())
            {
                return mMenu.getItem(pos).getItemId();
            }
            else
            {
                return -1;
            }
        }


        @Override
        public int getItemViewType(int pos)
        {
            int category = mMenu.getItem(pos).getOrder();
            return (category & Menu.CATEGORY_SECONDARY) > 0 ? 1 : 0;
        }


        @Override
        public int getViewTypeCount()
        {
            return 2;
        }


        @Override
        public boolean hasStableIds()
        {
            return true;
        }


        @Override
        public boolean isEmpty()
        {
            return mMenu.size() == 0;
        }


        @Override
        public void registerDataSetObserver(DataSetObserver arg0)
        {
            // at present we don't support that
        }


        @Override
        public void unregisterDataSetObserver(DataSetObserver arg0)
        {
            // at present we don't support that
        }


        @Override
        public boolean areAllItemsEnabled()
        {
            return true;
        }


        @Override
        public boolean isEnabled(int arg0)
        {
            return true;
        }

    }
}
