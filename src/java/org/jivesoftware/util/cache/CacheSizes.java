/**
 * $RCSfile$
 * $Revision: 11565 $
 * $Date: 2010-01-27 09:06:15 -0800 (Wed, 27 Jan 2010) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
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
 */

package org.jivesoftware.util.cache;

import org.jivesoftware.util.cache.Cacheable;

import java.util.Map;
import java.util.Collection;

/**
 * Utility class for determining the sizes in bytes of commonly used objects.
 * Classes implementing the Cacheable interface should use this class to
 * determine their size.
 *
 * @author Matt Tucker
 */
public class CacheSizes {

    /**
     * Returns the size in bytes of a basic Object. This method should only
     * be used for actual Object objects and not classes that extend Object.
     *
     * @return the size of an Object.
     */
    public static int sizeOfObject() {
        return 4;
    }

    /**
     * Returns the size in bytes of a String.
     *
     * @param string the String to determine the size of.
     * @return the size of a String.
     */
    public static int sizeOfString(String string) {
        if (string == null) {
            return 0;
        }
        return 4 + string.getBytes().length;
    }

    /**
     * Returns the size in bytes of a primitive int.
     *
     * @return the size of a primitive int.
     */
    public static int sizeOfInt() {
        return 4;
    }

    /**
     * Returns the size in bytes of a primitive char.
     *
     * @return the size of a primitive char.
     */
    public static int sizeOfChar() {
        return 2;
    }

    /**
     * Returns the size in bytes of a primitive boolean.
     *
     * @return the size of a primitive boolean.
     */
    public static int sizeOfBoolean() {
        return 1;
    }

    /**
     * Returns the size in bytes of a primitive long.
     *
     * @return the size of a primitive long.
     */
    public static int sizeOfLong() {
        return 8;
    }

    /**
     * Returns the size in bytes of a primitive double.
     *
     * @return the size of a primitive double.
     */
    public static int sizeOfDouble() {
        return 8;
    }

    /**
     * Returns the size in bytes of a Date.
     *
     * @return the size of a Date.
     */
    public static int sizeOfDate() {
        return 12;
    }

    /**
     * Returns the size in bytes of a Map object. All keys and
     * values <b>must be Strings</b>.
     *
     * @param map the Map object to determine the size of.
     * @return the size of the Map object.
     */
    public static int sizeOfMap(Map<String, String> map) {
        if (map == null) {
            return 0;
        }
        // Base map object -- should be something around this size.
        int size = 36;
        
        // Add in size of each value
        for (Map.Entry<String, String> entry : map.entrySet()) {
			size += sizeOfString(entry.getKey());
            size += sizeOfString(entry.getValue());
        }
        return size;
    }

    /**
     * Returns the size in bytes of a Collection object. Elements are assumed to be
     * <tt>String</tt>s, <tt>Long</tt>s or <tt>Cacheable</tt> objects.
     *
     * @param list the Collection object to determine the size of.
     * @return the size of the Collection object.
     */
    public static int sizeOfCollection(Collection list) {
        if (list == null) {
            return 0;
        }
        // Base list object (approximate)
        int size = 36;
        // Add in size of each value
        Object[] values = list.toArray();
        for (int i = 0; i < values.length; i++) {
            Object obj = values[i];
            if (obj instanceof String) {
                size += sizeOfString((String)obj);
            }
            else if (obj instanceof Long) {
                size += sizeOfLong() + sizeOfObject();
            }
            else {
                size += ((Cacheable)obj).getCachedSize();
            }
        }
        return size;
    }
}