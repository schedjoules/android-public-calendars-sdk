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

package org.dmfs.webcal.utils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.util.Log;


/**
 * Maintains a LIFO image download job queue. Download jobs added last will be started next.
 * 
 * @author Arjun Naik
 * @author Marten Gajda
 */
public class ImageLoaderQueue
{
	private static final String TAG = "ImageLoaderQueue";
	private ArrayList<Long> mDownloadJobQueue = new ArrayList<Long>(64);
	private ImageDownloader mDownloader = null;
	private ImageProxy mImageProxy;
	private Context mContext;


	public ImageLoaderQueue(Context context, ImageProxy imageProxy)
	{
		mContext = context;
		mImageProxy = imageProxy;
	}


	/**
	 * Append a job for the given iconId at the end of the queue to ensure it will be loaded next.
	 * 
	 * @param iconId
	 *            The id of the image to load.
	 */
	public void addJob(long iconId)
	{
		synchronized (mDownloadJobQueue)
		{
			// remove any existing job
			mDownloadJobQueue.remove(iconId);

			// re-queue as the next job
			mDownloadJobQueue.add(iconId);

			if (mDownloader == null)
			{
				scheduleJob();
			}
		}
	}


	/**
	 * Schedule a download job. The last job in the queue will be downloaded next.
	 */
	private void scheduleJob()
	{
		synchronized (mDownloadJobQueue)
		{
			if (mDownloadJobQueue.size() > 0)
			{
				int last = mDownloadJobQueue.size() - 1;
				Long iconId = mDownloadJobQueue.get(last);
				mDownloadJobQueue.remove(last);
				mDownloader = new ImageDownloader(iconId);
				mDownloader.execute();
			}
			else
			{
				mDownloader = null;
			}
		}
	}

	/**
	 * An AsyncTask that loads an image from an content provider.
	 */
	private class ImageDownloader extends AsyncTask<Void, Integer, Drawable>
	{
		/**
		 * The id of the image to load.
		 */
		private long mIconId;


		public ImageDownloader(long iconId)
		{
			mIconId = iconId;
		}


		@Override
		protected Drawable doInBackground(Void... params)
		{
			try
			{
				AssetFileDescriptor afd = CalendarContentContract.Icon.getIcon(mContext, mIconId, true);
				FileInputStream inputStream = afd.createInputStream();
				return Drawable.createFromStream(inputStream, null);
			}
			catch (FileNotFoundException e)
			{
				Log.e(TAG, "could not load image with id " + mIconId);
				return null;
			}
			catch (IOException e)
			{
				Log.e(TAG, "could not load image with id " + mIconId);
				return null;
			}
		}


		@Override
		protected void onPostExecute(Drawable result)
		{
			mImageProxy.imageReady(mIconId, result);
			scheduleJob();
		}

	}

}
