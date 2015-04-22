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

import android.view.View;
import android.view.View.MeasureSpec;
import android.view.animation.Animation;
import android.view.animation.Transformation;


/**
 * An {@link Animation} that expands or collapses a view vertically.
 * 
 * @author Marten Gajda <marten@dmfs.org>
 */
public class ExpandAnimation extends Animation
{
	private final View mView;
	private final boolean mExpand;
	private final int mInitialHeight;


	public ExpandAnimation(View view, boolean expand, long durationMillis)
	{
		view.measure(MeasureSpec.makeMeasureSpec(((View) view.getParent()).getMeasuredWidth(), MeasureSpec.AT_MOST),
			MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));

		mInitialHeight = view.getMeasuredHeight();

		if (expand)
		{
			view.getLayoutParams().height = 0;
		}
		else
		{
			view.getLayoutParams().height = mInitialHeight;
		}

		view.setVisibility(View.VISIBLE);

		mView = view;
		mExpand = expand;
		setDuration(durationMillis);
	}


	@Override
	protected void applyTransformation(float interpolatedTime, Transformation t)
	{
		int newHeight = 0;
		if (mExpand)
		{
			newHeight = (int) (mInitialHeight * interpolatedTime);
		}
		else
		{
			newHeight = (int) (mInitialHeight * (1 - interpolatedTime));
		}
		mView.getLayoutParams().height = newHeight;
		// v.setAlpha(expand ? interpolatedTime : 1 - interpolatedTime);
		mView.requestLayout();

		if (interpolatedTime == 1 && !mExpand)
		{
			mView.setVisibility(View.GONE);
		}
	}


	@Override
	public boolean willChangeBounds()
	{
		return true;
	}
}
