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

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.support.v4.widget.CursorAdapter;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ListAdapter;
import android.widget.TextView;


/**
 * A wrapper for a {@link ListAdapter} that adds section titles. When you instantiate this wrapper you need to provide the wrapped {@link ListAdapter} and a
 * {@link SectionIndexer} to identify the sections.
 * <p>
 * TODO: at present this class indexes all elements of the wrapped adapter, which might take some time. We probably can improve that by indexing the elements
 * lazily (when they are accessed).
 * </p>
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
/*
 * Implementation detail: This adapter builds own ids for each element. The ids are made up by the section index and the original position of the elements. That
 * implies that the ids are not stable, but we can identify the elements quickly without maintaining another index.
 */
public class SectionTitlesAdapter implements ListAdapter
{
	/**
	 * The position value of section headers.
	 */
	public final static int HEADER_ID = 0xffffffff;

	/**
	 * An interface to index the elements of the wrapped adapter.
	 */
	public interface SectionIndexer
	{
		/**
		 * Returns a section index for the given list element.
		 * 
		 * @param object
		 *            The object as returned by the original adapter.
		 * 
		 * @return An index that identifies the section.
		 */
		public int getSectionIndex(Object object);


		/**
		 * Get the title of the section identified by the given index value.
		 * 
		 * @param index
		 *            The index (as returned by {@link #getSectionIndex(Object)}) that identifies the section.
		 * @return A title for this section.
		 */
		public String getSectionTitle(int index);
	}

	private final ListAdapter mAdaptedAdapter;
	private final SectionIndexer mIndexer;
	private final int mSectionHeaderViewId;
	private List<Long> mIndex = new ArrayList<Long>(64);
	private LayoutInflater mInflater;
	private boolean mHideEmptySectionTitle = true;


	/**
	 * Build an id value from the given section identifier and item position.
	 * 
	 * @param sectionId
	 *            The identifier for the section this element belongs to (or the seaction header itself).
	 * @param itemPos
	 *            The position in the original adapter of this element or {@link #HEADER_ID} for section headers.
	 * @return The packed id.
	 */
	private static long packedId(long sectionId, int itemPos)
	{
		return (long) (sectionId << 32) + (((long) itemPos) & 0x0ffffffffL);
	}


	/**
	 * Extract the section id from a packed id as returned by {@link #packedId(long, int)}.
	 * 
	 * @param packedId
	 *            The packed id.
	 * @return The encoded section id.
	 */
	public static long sectionId(long packedId)
	{
		return ((packedId >> 32) & 0x0ffffffffL);
	}


	/**
	 * Extract the item position from a packed id as returned by {@link #packedId(long, int)}.
	 * 
	 * @param packedId
	 *            The packed id.
	 * @return The encoded item position or {@link #HEADER_ID} if this id belongs to a section header.
	 */
	public static int itemPos(long packedId)
	{
		return (int) (packedId & 0x0ffffffffL);
	}


	public SectionTitlesAdapter(Context context, ListAdapter adapter, SectionIndexer indexer, int sectionHeaderViewid)
	{
		mAdaptedAdapter = adapter;
		mIndexer = indexer;
		mSectionHeaderViewId = sectionHeaderViewid;
		adapter.registerDataSetObserver(new DataSetObserver()
		{
			@Override
			public void onChanged()
			{
				buildIndex();
			}
		});
		mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		buildIndex();
	}


	@Override
	public int getCount()
	{
		return mIndex.size();
	}


	@Override
	public Object getItem(int position)
	{
		int itemPos = itemPos(mIndex.get(position));
		if (itemPos == HEADER_ID)
		{
			// TODO: return an object that represents the section header
			return null;
		}
		else
		{
			return mAdaptedAdapter.getItem(itemPos);
		}
	}


	@Override
	public long getItemId(int position)
	{
		return mIndex.get(position);
	}


	@Override
	public int getItemViewType(int position)
	{
		long packedId = getItemId(position);
		int itemPos = itemPos(packedId);

		if (itemPos == HEADER_ID)
		{
			return mAdaptedAdapter.getViewTypeCount();
		}
		else
		{
			return mAdaptedAdapter.getItemViewType(itemPos);
		}
	}


	@Override
	public View getView(int position, View convertView, ViewGroup parent)
	{
		int itemPos = itemPos(mIndex.get(position));

		if (itemPos == HEADER_ID)
		{
			String title = mIndexer.getSectionTitle((int) sectionId(mIndex.get(position)));

			// this is a section header
			if (convertView == null || convertView instanceof FrameLayout && !TextUtils.isEmpty(title))
			{
				convertView = mInflater.inflate(mSectionHeaderViewId, parent, false);
			}

			if (mHideEmptySectionTitle && TextUtils.isEmpty(title))
			{
				// setting visibility of convertView doesn't work, we need to return an empty view
				return new FrameLayout(mInflater.getContext());
			}
			else
			{
				((TextView) convertView.findViewById(android.R.id.title)).setText(title);
				return convertView;
			}
		}
		else
		{
			// just forward the request to the wrapped adapter
			return mAdaptedAdapter.getView(itemPos, convertView, parent);
		}
	}


	@Override
	public int getViewTypeCount()
	{
		// we inject one more view type
		return mAdaptedAdapter.getViewTypeCount() + 1;
	}


	@Override
	public boolean hasStableIds()
	{
		// we don't have stable ids because the ids encode the position of the item in the wrapped adapter.
		return false;
	}


	@Override
	public boolean isEmpty()
	{
		return mAdaptedAdapter.isEmpty();
	}


	@Override
	public void registerDataSetObserver(DataSetObserver observer)
	{
		mAdaptedAdapter.registerDataSetObserver(observer);
	}


	@Override
	public void unregisterDataSetObserver(DataSetObserver observer)
	{
		mAdaptedAdapter.unregisterDataSetObserver(observer);
	}


	@Override
	public boolean areAllItemsEnabled()
	{
		// no, because headers are disabled.
		return false;
	}


	@Override
	public boolean isEnabled(int pos)
	{
		// just forward the request if the item is not a header. Headers are always disabled.
		long packedId = getItemId(pos);
		int itemPos = itemPos(packedId);
		return itemPos != HEADER_ID && mAdaptedAdapter.isEnabled(itemPos);
	}


	/**
	 * Replace the current cursor of the wrapped adapter. This has no effect if the wrapped adapter is not a {@link CursorAdapter}.
	 * 
	 * @param newCursor
	 *            The new cursor.
	 */
	public void swapCursor(Cursor newCursor)
	{
		if (mAdaptedAdapter instanceof CursorAdapter)
		{
			((CursorAdapter) mAdaptedAdapter).swapCursor(newCursor);
		}
	}


	/**
	 * Build the index. This adds one entry for each element of the wrapped adapter. It also inserts a section header every time the {@link SectionIndexer}
	 * returns a new section value.
	 */
	private void buildIndex()
	{
		mIndex.clear();
		int oldGroupIndex = Integer.MAX_VALUE;
		for (int i = 0, count = mAdaptedAdapter.getCount(); i < count; ++i)
		{
			int groupIndex = mIndexer.getSectionIndex(mAdaptedAdapter.getItem(i));
			if (groupIndex != oldGroupIndex)
			{
				// we have a new group, add a header
				mIndex.add(packedId(groupIndex, HEADER_ID));
				oldGroupIndex = groupIndex;
			}
			mIndex.add(packedId(groupIndex, i));
		}
	}

}
