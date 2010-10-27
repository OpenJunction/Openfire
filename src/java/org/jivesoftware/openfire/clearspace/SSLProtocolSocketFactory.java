/**
 * $RCSfile$
 * $Revision$
 * $Date$
 *
 * Copyright (C) 2005-2008 Jive Software. All rights reserved.
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

package org.jivesoftware.openfire.clearspace;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.UnknownHostException;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.apache.commons.httpclient.ConnectTimeoutException;
import org.apache.commons.httpclient.HttpClientError;
import org.apache.commons.httpclient.params.HttpConnectionParams;
import org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory;
import org.jivesoftware.openfire.net.SSLConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of SecureProtocolSocketFactory that will use a custom trust manager for
 * certification validation.
 *
 * @author Gaston Dombiak
 */
public class SSLProtocolSocketFactory implements SecureProtocolSocketFactory {

	private static final Logger Log = LoggerFactory.getLogger(SSLProtocolSocketFactory.class);

    private SSLContext sslcontext = null;
    private ClearspaceManager manager;

    /**
     * Constructor for EasySSLProtocolSocketFactory.
     */
    public SSLProtocolSocketFactory(ClearspaceManager manager) {
        super();
        this.manager = manager;
    }

    private SSLContext createSSLContext(String host) {
        try {
            SSLContext context = SSLContext.getInstance("SSL");
            context.init(
                    null,
                    new TrustManager[]{new ClearspaceX509TrustManager(host, manager.getProperties(), SSLConfig.gets2sTrustStore())},
                    null);
            return context;
        } catch (Exception e) {
            Log.error(e.getMessage(), e);
            throw new HttpClientError(e.toString());
        }
    }

    private SSLContext getSSLContext(String host) {
        if (this.sslcontext == null) {
            this.sslcontext = createSSLContext(host);
        }
        return this.sslcontext;
    }

    /**
     * @see SecureProtocolSocketFactory#createSocket(java.lang.String,int,java.net.InetAddress,int)
     */
    public Socket createSocket(
            String host,
            int port,
            InetAddress clientHost,
            int clientPort)
            throws IOException, UnknownHostException {

        return getSSLContext(host).getSocketFactory().createSocket(
                host,
                port,
                clientHost,
                clientPort
        );
    }

    /**
     * Attempts to get a new socket connection to the given host within the given time limit.
     * <p>
     * To circumvent the limitations of older JREs that do not support connect timeout a
     * controller thread is executed. The controller thread attempts to create a new socket
     * within the given limit of time. If socket constructor does not return until the
     * timeout expires, the controller terminates and throws an {@link ConnectTimeoutException}
     * </p>
     *
     * @param host         the host name/IP
     * @param port         the port on the host
     * @param localAddress the local host name/IP to bind the socket to
     * @param localPort    the port on the local machine
     * @param params       {@link HttpConnectionParams Http connection parameters}
     * @return Socket a new socket
     * @throws IOException          if an I/O error occurs while creating the socket
     * @throws UnknownHostException if the IP address of the host cannot be
     *                              determined
     */
    public Socket createSocket(
            final String host,
            final int port,
            final InetAddress localAddress,
            final int localPort,
            final HttpConnectionParams params
    ) throws IOException, UnknownHostException, ConnectTimeoutException {
        if (params == null) {
            throw new IllegalArgumentException("Parameters may not be null");
        }
        int timeout = params.getConnectionTimeout();
        SocketFactory socketfactory = getSSLContext(host).getSocketFactory();
        if (timeout == 0) {
            return socketfactory.createSocket(host, port, localAddress, localPort);
        }
        else {
            Socket socket = socketfactory.createSocket();
            SocketAddress localaddr = new InetSocketAddress(localAddress, localPort);
            SocketAddress remoteaddr = new InetSocketAddress(host, port);
            socket.bind(localaddr);
            socket.connect(remoteaddr, timeout);
            return socket;
        }
    }

    /**
     * @see org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory#createSocket(java.lang.String,int)
     */
    public Socket createSocket(String host, int port)
            throws IOException, UnknownHostException {
        return getSSLContext(host).getSocketFactory().createSocket(
                host,
                port
        );
    }

    /**
     * @see org.apache.commons.httpclient.protocol.SecureProtocolSocketFactory#createSocket(java.net.Socket,java.lang.String,int,boolean)
     */
    public Socket createSocket(
            Socket socket,
            String host,
            int port,
            boolean autoClose)
            throws IOException, UnknownHostException {
        return getSSLContext(host).getSocketFactory().createSocket(
                socket,
                host,
                port,
                autoClose
        );
    }

    @Override
	public boolean equals(Object obj) {
        return ((obj != null) && obj.getClass().equals(SSLProtocolSocketFactory.class));
    }

    @Override
	public int hashCode() {
        return SSLProtocolSocketFactory.class.hashCode();
    }


}
