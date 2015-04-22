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

import org.dmfs.webcal.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;


public class TabBar extends LinearLayout
{
	private static final int BOTTOM_DIVIDER_HEIGHT = 0;
	private static final byte BOTTOM_DIVIDER_TRANSPARENCY = 0x26;
	private static final int INDICATOR_HEIGHT = 3;
	private static final int DIVIDER_WIDTH = 8;
	private static final byte DIVIDER_TRANSPARENCY = 0x20;
	private static final float DIVIDER_HEIGHT = 0;

	private final int mBottomDividerHeight;
	private final Paint mBottomDividerPaint;

	private final int mSelectedIndicatorThickness;
	private final Paint mSelectedIndicatorPaint;

	private final int mBottomDividerColor;

	private final Paint mDividerPaint;
	private final float mDividerHeight;

	private int mSelectedPosition;
	private float mSelectionOffset;

	private final ColorizerImpl mColorizer;


	TabBar(Context context)
	{
		this(context, null);
	}


	TabBar(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		setWillNotDraw(false);

		final float density = getResources().getDisplayMetrics().density;

		TypedValue outValue = new TypedValue();
		context.getTheme().resolveAttribute(R.attr.color, outValue, true);
		final int themeColorPrimary = outValue.data;

		context.getTheme().resolveAttribute(R.attr.colorAccent, outValue, true);
		final int themeColorAccent = outValue.data;

		mBottomDividerColor = setColorAlpha(themeColorPrimary, BOTTOM_DIVIDER_TRANSPARENCY);

		mColorizer = new ColorizerImpl();
		mColorizer.setIndicatorColors(themeColorAccent);
		mColorizer.setDividerColors(setColorAlpha(themeColorAccent, DIVIDER_TRANSPARENCY));

		mBottomDividerHeight = (int) (BOTTOM_DIVIDER_HEIGHT * density);
		mBottomDividerPaint = new Paint();
		mBottomDividerPaint.setColor(mBottomDividerColor);

		mSelectedIndicatorThickness = (int) (INDICATOR_HEIGHT * density);
		mSelectedIndicatorPaint = new Paint();

		mDividerHeight = DIVIDER_HEIGHT;
		mDividerPaint = new Paint();
		mDividerPaint.setStrokeWidth((int) (DIVIDER_WIDTH * density));
	}


	void setSelectedIndicatorColors(int... colors)
	{
		mColorizer.setIndicatorColors(colors);
		invalidate();
	}


	void setDividerColors(int... colors)
	{
		mColorizer.setDividerColors(colors);
		invalidate();
	}


	void onViewPagerPageChanged(int position, float positionOffset)
	{
		mSelectedPosition = position;
		mSelectionOffset = positionOffset;
		invalidate();
	}


	@Override
	protected void onDraw(Canvas canvas)
	{
		final int height = getHeight();
		final int childCount = getChildCount();
		final int dividerHeightPx = (int) (Math.min(Math.max(0f, mDividerHeight), 1f) * height);

		// Thick colored underline below the current selection
		if (childCount > 0)
		{
			View selectedTitle = getChildAt(mSelectedPosition);
			int left = selectedTitle.getLeft();
			int right = selectedTitle.getRight();
			int color = mColorizer.getIndicatorColor(mSelectedPosition);

			if (mSelectionOffset > 0f && mSelectedPosition < (getChildCount() - 1))
			{
				int nextColor = mColorizer.getIndicatorColor(mSelectedPosition + 1);
				if (color != nextColor)
				{
					color = blendColors(nextColor, color, mSelectionOffset);
				}

				// Draw the selection partway between the tabs
				View nextTitle = getChildAt(mSelectedPosition + 1);
				left = (int) (mSelectionOffset * nextTitle.getLeft() + (1.0f - mSelectionOffset) * left);
				right = (int) (mSelectionOffset * nextTitle.getRight() + (1.0f - mSelectionOffset) * right);
			}

			mSelectedIndicatorPaint.setColor(color);

			canvas.drawRect(left, height - mSelectedIndicatorThickness, right, height, mSelectedIndicatorPaint);
		}

		// Thin underline along the entire bottom edge
		canvas.drawRect(0, height - mBottomDividerHeight, getWidth(), height, mBottomDividerPaint);

		// Vertical separators between the titles && set title alpha
		int separatorTop = (height - dividerHeightPx) / 2;
		for (int i = 0; i < childCount; i++)
		{
			View child = getChildAt(i);

			if (child == getChildAt(mSelectedPosition) && mSelectionOffset == 0)
			{
				child.setAlpha(1);
			}
			else
			{
				child.setAlpha(0.6f);
			}

			// don't draw separator for the last element
			if (i != childCount - 1)
			{
				mDividerPaint.setColor(mColorizer.getDividerColor(i));
				canvas.drawLine(child.getRight(), separatorTop, child.getRight(), separatorTop + dividerHeightPx, mDividerPaint);
			}

		}
	}


	/**
	 * Set the alpha value of the {@code color} to be the given {@code alpha} value.
	 */
	private static int setColorAlpha(int color, byte alpha)
	{
		return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
	}


	/**
	 * Blend {@code color1} and {@code color2} using the given ratio.
	 * 
	 * @param ratio
	 *            of which to blend. 1.0 will return {@code color1}, 0.5 will give an even blend, 0.0 will return {@code color2}.
	 */
	private static int blendColors(int color1, int color2, float ratio)
	{
		final float inverseRation = 1f - ratio;
		float r = (Color.red(color1) * ratio) + (Color.red(color2) * inverseRation);
		float g = (Color.green(color1) * ratio) + (Color.green(color2) * inverseRation);
		float b = (Color.blue(color1) * ratio) + (Color.blue(color2) * inverseRation);
		return Color.rgb((int) r, (int) g, (int) b);
	}

	private static class ColorizerImpl implements TabBarLayout.Colorizer
	{
		private int[] mIndicatorColors;
		private int[] mDividerColors;


		@Override
		public final int getIndicatorColor(int position)
		{
			return mIndicatorColors[position % mIndicatorColors.length];
		}


		@Override
		public final int getDividerColor(int position)
		{
			return mDividerColors[position % mDividerColors.length];
		}


		void setIndicatorColors(int... colors)
		{
			mIndicatorColors = colors;
		}


		void setDividerColors(int... colors)
		{
			mDividerColors = colors;
		}
	}

}
