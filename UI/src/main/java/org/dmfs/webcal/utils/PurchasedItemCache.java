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

import java.util.HashSet;
import java.util.Set;


public enum PurchasedItemCache
{
    INSTANCE;

    private Set<String> mPurchasedItems;


    public void addItem(String item)
    {
        synchronized (this)
        {
            Set<String> items = mPurchasedItems;
            if (items == null)
            {
                items = new HashSet<String>(16);
                mPurchasedItems = items;
            }
            mPurchasedItems.add(item);
        }
    }


    public boolean hasItem(String item)
    {
        synchronized (this)
        {
            return mPurchasedItems != null && mPurchasedItems.contains(item);
        }
    }


    public void removeItem(String item)
    {
        synchronized (this)
        {
            if (mPurchasedItems != null)
            {
                mPurchasedItems.remove(item);
            }
        }
    }
}
