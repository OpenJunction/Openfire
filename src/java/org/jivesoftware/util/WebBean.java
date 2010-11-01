/**
 * $RCSfile$
 * $Revision: 10204 $
 * $Date: 2008-04-11 15:44:25 -0700 (Fri, 11 Apr 2008) $
 *
 * Copyright (C) 2004-2008 Jive Software. All rights reserved.
 *
 * This software is published under the terms of the GNU Public License (GPL),
 * a copy of which is included in this distribution, or a commercial license
 * agreement with Jive.
 */

package org.jivesoftware.util;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.jsp.JspWriter;
import javax.servlet.jsp.PageContext;

public abstract class WebBean {

    public HttpSession session;
    public HttpServletRequest request;
    public HttpServletResponse response;
    public ServletContext application;
    public JspWriter out;

    public void init(HttpServletRequest request, HttpServletResponse response,
            HttpSession session, ServletContext app, JspWriter out)
    {
        this.request = request;
        this.response = response;
        this.session = session;
        this.application = app;
        this.out = out;
    }

    public void init(HttpServletRequest request, HttpServletResponse response,
                     HttpSession session, ServletContext app) {

        this.request = request;
        this.response = response;
        this.session = session;
        this.application = app;
    }

    public void init(PageContext pageContext){
        this.request = (HttpServletRequest)pageContext.getRequest();
        this.response = (HttpServletResponse)pageContext.getResponse();
        this.session = (HttpSession)pageContext.getSession();
        this.application = (ServletContext)pageContext.getServletContext();
        this.out = (JspWriter)pageContext.getOut();
    }
}