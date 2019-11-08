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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.drawable.Drawable;

import org.dmfs.android.calendarcontent.provider.CalendarContentContract;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import androidx.collection.LruCache;


/**
 * A singleton that caches images and takes care of loading them from the content provider if necessary.
 *
 * @author Arjun Naik
 * @author Marten Gajda <marten@dmfs.org>
 */
public class ImageProxy
{
    /**
     * The maximum size taken by the image cache.
     */
    private final static int IMAGE_CACHE_SIZE = 1024 * 1024; // 1MB

    /**
     * The one and only instance of the {@link ImageProxy}.
     */
    private static ImageProxy mInstance;

    /**
     * The actual cache for the images.
     */
    private ImageCache mImageCache = new ImageCache(IMAGE_CACHE_SIZE);

    /**
     * The {@link ImageLoaderQueue} to load the images.
     */
    private ImageLoaderQueue mLoader;

    /**
     * {@link Map} of image ids to {@link Set}s of {@link ImageAvailableListener}s for that specific image.
     */
    private HashMap<Long, Set<WeakReference<ImageAvailableListener>>> mJobWaitQueue = new HashMap<Long, Set<WeakReference<ImageAvailableListener>>>();

    /**
     * The application {@link Context}.
     */
    private Context mAppContext;


    /**
     * A listener that get notified when an image has been loaded.
     */
    public interface ImageAvailableListener
    {
        public void imageAvailable(long mIconId, Drawable drawable);
    }


    /**
     * Get an {@link ImageProxy} instance.
     *
     * @param context
     *         A {@link Context}.
     *
     * @return The {@link ImageProxy} singleton.
     */
    public synchronized static ImageProxy getInstance(Context context)
    {
        if (mInstance == null)
        {
            return mInstance = new ImageProxy(context);
        }
        return mInstance;
    }


    private ImageProxy(Context c)
    {
        mAppContext = c.getApplicationContext();
        mLoader = new ImageLoaderQueue(c, this);
    }


    /**
     * Return the image with then given id. If the icon is not present in the memory or filesystem cache this method returns <code>null</code>. The caller is
     * notified via the given {@link ImageAvailableListener} when the image has been loaded.
     *
     * @param iconId
     *         The id of the icon to load.
     * @param callback
     *         The {@link ImageAvailableListener} to notify when the icon has been loaded.
     *
     * @return A {@link Drawable} or null of the icon is loaded asynchronously or the id is invalid.
     */
    public Drawable getImage(long iconId, ImageAvailableListener callback)
    {
        if (iconId == -1)
        {
            return null;
        }

        Drawable iconDrawable = mImageCache.get(iconId);
        if (iconDrawable == null)
        {
            try
            {
                AssetFileDescriptor afd = CalendarContentContract.Icon.getIcon(mAppContext, iconId, false);
                FileInputStream inputStream = afd.createInputStream();
                iconDrawable = Drawable.createFromStream(inputStream, null);
                if (iconDrawable != null)
                {
                    mImageCache.put(iconId, iconDrawable);
                }
            }
            catch (FileNotFoundException e)
            {
                registerImageRequest(iconId, callback);
            }
            catch (IOException e)
            {
                registerImageRequest(iconId, callback);
            }
        }
        return iconDrawable;
    }


    /**
     * Registers an image for asynchronous loading.
     *
     * @param iconId
     *         The icon id to load.
     * @param callback
     *         The {@link ImageAvailableListener} to notify when the image has been loaded.
     */
    private void registerImageRequest(long iconId, ImageAvailableListener callback)
    {
        synchronized (mJobWaitQueue)
        {
            if (mJobWaitQueue.containsKey(iconId))
            {
                Set<WeakReference<ImageAvailableListener>> listeners = mJobWaitQueue.get(iconId);
                listeners.add(new WeakReference<ImageProxy.ImageAvailableListener>(callback));
            }
            else
            {
                Set<WeakReference<ImageAvailableListener>> listeners = new HashSet<WeakReference<ImageAvailableListener>>();
                listeners.add(new WeakReference<ImageProxy.ImageAvailableListener>(callback));
                mJobWaitQueue.put(iconId, listeners);
                mLoader.addJob(iconId);
            }
        }
    }


    /**
     * Notify the {@link ImageProxy} that an image has been loaded.
     *
     * @param iconId
     *         The id of the image that has been loaded.
     * @param result
     *         The image.
     */
    void imageReady(long iconId, Drawable result)
    {
        if (result == null)
        {
            return;
        }

        synchronized (mImageCache)
        {
            // put the icon into the cache
            mImageCache.put(iconId, result);
        }

        Set<WeakReference<ImageAvailableListener>> listeners = null;
        synchronized (mJobWaitQueue)
        {
            listeners = mJobWaitQueue.get(iconId);
            // all listeners will be notified now, so remove them
            mJobWaitQueue.remove(iconId);
        }

        if (listeners != null)
        {
            // notify listeners
            for (WeakReference<ImageAvailableListener> listenerRef : listeners)
            {
                ImageAvailableListener listener = listenerRef.get();
                if (listener != null)
                {
                    listener.imageAvailable(iconId, result);
                }
            }
        }
    }


    /**
     * A cache for the images.
     */
    class ImageCache extends LruCache<Long, Drawable>
    {

        /**
         * Instantiate a new ImageCache with the given capacity.
         *
         * @param maxSize
         *         The maximum number of bytes the cache should use.
         */
        public ImageCache(int maxSize)
        {
            super(maxSize);
        }


        @Override
        protected int sizeOf(Long key, Drawable value)
        {
            // assume all images are 32 bit uncompressed - this is just a rough estimation
            return Math.abs(value.getIntrinsicHeight()) * Math.abs(value.getIntrinsicWidth()) * 4;
        }
    }
}
