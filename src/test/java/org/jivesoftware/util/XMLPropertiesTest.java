/**
 * $RCSfile$
 * $Revision: 10535 $
 * $Date: 2008-06-16 10:47:21 -0700 (Mon, 16 Jun 2008) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

import junit.framework.TestCase;

import java.io.ByteArrayInputStream;

public class XMLPropertiesTest extends TestCase {

    public void testAttributes() throws Exception {
        String xml = "<root><foo></foo></root>";
        XMLProperties props = new XMLProperties(new ByteArrayInputStream(xml.getBytes()));
        assertNull(props.getAttribute("foo","bar"));
        xml = "<root><foo bar=\"test123\"></foo></root>";
        props = new XMLProperties(new ByteArrayInputStream(xml.getBytes()));
        assertEquals(props.getAttribute("foo","bar"), "test123");
    }

    public void testGetProperty() throws Exception {
        XMLProperties props = new XMLProperties(
                "./resources/org/jivesoftware/util/XMLProperties.test01.xml");
        assertEquals("123", props.getProperty("foo.bar"));
        assertEquals("456", props.getProperty("foo.bar.baz"));
        assertNull(props.getProperty("foo"));
        assertNull(props.getProperty("nothing.something"));
    }

    public void testGetChildPropertiesIterator() throws Exception {
        XMLProperties props = new XMLProperties(
                "./resources/org/jivesoftware/util/XMLProperties.test02.xml");
        String[] names = {"a","b","c","d"};
        String[] values = {"1","2","3","4"};
        String[] children = props.getChildrenProperties("foo.bar");
        for (int i=0; i<children.length; i++) {
            String prop = children[i];
            assertEquals(names[i], prop);
            String value = props.getProperty("foo.bar." + prop);
            assertEquals(values[i], value);
            i++;
        }
    }

    public void testGetPropertyWithXMLEntity() throws Exception {
        String xml = "<root><foo>foo&amp;bar</foo></root>";
        XMLProperties props = new XMLProperties(new ByteArrayInputStream(xml.getBytes()));
        assertEquals("foo&bar", props.getProperty("foo"));
    }
}
