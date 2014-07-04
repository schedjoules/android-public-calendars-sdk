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

package org.dmfs.webcal.views;

import org.dmfs.webcal.utils.ImageProxy;
import org.dmfs.webcal.utils.ImageProxy.ImageAvailableListener;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageView;


/**
 * An {@link ImageView} that can load its content asynchronously.
 * 
 * @author Arjun Naik
 * @author Marten Gajda <marten@dmfs.org>
 */
public class RemoteImageView extends ImageView implements ImageAvailableListener
{
	/**
	 * The duration of the fade-in when the image pops up the first time.
	 */
	private final static int ANIMATION_DURATION = 250; // ms

	private ImageProxy mImageProxy;
	private long mSource;


	public RemoteImageView(Context context)
	{
		super(context);
		mImageProxy = ImageProxy.getInstance(context);
	}


	public RemoteImageView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		mImageProxy = ImageProxy.getInstance(context);
	}


	public RemoteImageView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		mImageProxy = ImageProxy.getInstance(context);
	}


	/**
	 * Set the id of the image.
	 * 
	 * @param iconId
	 *            The image id.
	 */
	public void setRemoteSource(long iconId)
	{
		setRemoteSource(iconId, false);
	}


	/**
	 * Set the id of the image.
	 * 
	 * @param iconId
	 *            The image id.
	 * @param useSpaceIfNoImage
	 *            Whether the view should be <code>GONE</code> if <code>iconId</code> is <code>-1</code>.
	 */
	public void setRemoteSource(long iconId, boolean useSpaceIfNoImage)
	{
		if (iconId == -1 && !useSpaceIfNoImage)
		{
			// no icon
			setVisibility(GONE);
			return;
		}
		setVisibility(VISIBLE);

		Drawable image = mImageProxy.getImage(iconId, this);

		if (image != null)
		{
			setImageDrawable(image);
		}
		else
		{
			setImageDrawable(null);
		}

		requestLayout();
		mSource = iconId;
	}


	@Override
	public void imageAvailable(long iconId, Drawable drawable)
	{
		if (iconId == mSource)
		{
			// ensure we fade in the icon softly if the icon was not visible before

			boolean animate = getDrawable() == null;

			if (animate)
			{
				setAlpha(0f);
			}

			setImageDrawable(drawable);
			setVisibility(VISIBLE);

			if (animate)
			{
				animate().alpha(1).setDuration(ANIMATION_DURATION).start();
			}
		}
	}
}
