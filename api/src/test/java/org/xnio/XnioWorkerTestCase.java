/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2012 Red Hat, Inc. and/or its affiliates, and individual
 * contributors as indicated by the @author tags.
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

package org.xnio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.Channel;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xnio.channels.AcceptingChannel;
import org.xnio.channels.BoundChannel;
import org.xnio.channels.CloseableChannel;
import org.xnio.channels.ConnectedMessageChannel;
import org.xnio.channels.ConnectedStreamChannel;
import org.xnio.channels.FramedMessageChannel;
import org.xnio.channels.MulticastMessageChannel;
import org.xnio.mock.AcceptingChannelMock;
import org.xnio.mock.ChannelMock;
import org.xnio.mock.ConnectedStreamChannelMock;
import org.xnio.mock.MulticastMessageChannelMock;
import org.xnio.mock.XnioWorkerMock;

/**
 * Test for {@link XnioWorker}.
 * 
 * @author <a href="mailto:flavia.rainone@jboss.com">Flavia Rainone</a>
 *
 */
public class XnioWorkerTestCase {

    private static final SocketAddress unknownSocketAddress = new SocketAddress() {private static final long serialVersionUID = 1L;};
    private XnioWorker xnioWorker;
    

    @Before
    public void init() throws IllegalArgumentException, IOException {
        final Xnio xnio = Xnio.getInstance();
        xnioWorker = xnio.createWorker(OptionMap.create(Options.WORKER_NAME, "worker for tests"));
        assertNotNull(xnioWorker);
        assertEquals("worker for tests", xnioWorker.getName());
    }

    private void checkCreateStreamServer(SocketAddress socketAddress, String channelInfo) throws IOException {
        final ChannelListener<AcceptingChannel<ConnectedStreamChannel>> acceptingChannelListener = new TestChannelListener<AcceptingChannel<ConnectedStreamChannel>>();;
        final OptionMap optionMap = OptionMap.create(Options.BROADCAST, true);
        final AcceptingChannel<? extends ConnectedStreamChannel> streamServer = xnioWorker.createStreamServer(
                socketAddress, acceptingChannelListener, optionMap);
        assertNotNull(streamServer);
        assertEquals(socketAddress, streamServer.getLocalAddress());
        assertSame(xnioWorker, streamServer.getWorker());
        assertTrue(streamServer instanceof AcceptingChannelMock);
        AcceptingChannelMock acceptingChannelMock = (AcceptingChannelMock) streamServer;
        assertSame(acceptingChannelListener, acceptingChannelMock.getAcceptListener());
        assertEquals(optionMap, acceptingChannelMock.getOptionMap());
        assertEquals(channelInfo, acceptingChannelMock.getInfo());
    }

    @Test
    public void createTcpStreamServer() throws IOException {
        checkCreateStreamServer(new InetSocketAddress(1000), XnioWorkerMock.TCP_CHANNEL_INFO);
    }

    @Test
    public void createLocalStreamServer() throws IOException {
        checkCreateStreamServer(new LocalSocketAddress("server"), XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    @Test
    public void createStreamServerWithInvalidAddress() throws IOException {
        final ChannelListener<? super AcceptingChannel<ConnectedStreamChannel>> acceptingChannelListener = new ChannelListener<AcceptingChannel<ConnectedStreamChannel>>() {
            @Override
            public void handleEvent(AcceptingChannel<ConnectedStreamChannel> channel) {}
           
        };
        Exception expected = null;
        try {
            xnioWorker.createStreamServer(null, acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.createStreamServer(new SocketAddress() {private static final long serialVersionUID = 1L;},
                    acceptingChannelListener, OptionMap.create(Options.BROADCAST, true));
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        
        assertNotNull(expected);
    }

    private void checkConnectStream(SocketAddress socketAddress, SocketAddress localSocketAddress, String channelInfo) throws CancellationException, IOException {
        final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
        final OptionMap optionMap = OptionMap.create(Options.MAX_INBOUND_MESSAGE_SIZE, 50000);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel = xnioWorker.connectStream(socketAddress, channelListener, optionMap);
        assertNotNull(connectedStreamChannel);

        final ConnectedStreamChannelMock channelMock = (ConnectedStreamChannelMock) connectedStreamChannel.get();
        assertNotNull(channelMock);
        assertTrue(channelListener.isInvoked());
        assertSame(channelMock, channelListener.getChannel());
        assertEquals(localSocketAddress, channelMock.getLocalAddress());
        assertEquals(socketAddress, channelMock.getPeerAddress());
        assertEquals(optionMap, channelMock.getOptionMap());
        assertEquals(channelInfo, channelMock.getInfo());
    }

    @Test
    public void connectTcpStream() throws CancellationException, IOException {
        checkConnectStream(new InetSocketAddress(1000), Xnio.ANY_INET_ADDRESS, XnioWorkerMock.TCP_CHANNEL_INFO);
    }

    @Test
    public void connectLocalStream() throws CancellationException, IOException {
        checkConnectStream(new LocalSocketAddress("server for test"), Xnio.ANY_LOCAL_ADDRESS, XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    private void checkConnectStreamWithBindListener(SocketAddress socketAddress, SocketAddress localSocketAddress, String channelInfo) throws CancellationException, IOException {
        final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.MAX_OUTBOUND_MESSAGE_SIZE, 50000);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel = xnioWorker.connectStream(socketAddress, channelListener, bindListener, optionMap);
        assertNotNull(connectedStreamChannel);

        final ConnectedStreamChannelMock channelMock = (ConnectedStreamChannelMock) connectedStreamChannel.get();
        assertNotNull(channelMock);
        assertTrue(channelListener.isInvoked());
        assertSame(channelMock, channelListener.getChannel());
        assertTrue(bindListener.isInvoked());
        assertSame(channelMock, bindListener.getChannel());
        assertEquals(localSocketAddress, channelMock.getLocalAddress());
        assertEquals(socketAddress, channelMock.getPeerAddress());
        assertEquals(optionMap, channelMock.getOptionMap());
        assertEquals(channelInfo, channelMock.getInfo());
    }

    @Test
    public void connectTcpStreamWithBindListener() throws CancellationException, IOException {
        checkConnectStreamWithBindListener(new InetSocketAddress(1500), Xnio.ANY_INET_ADDRESS, XnioWorkerMock.TCP_CHANNEL_INFO);
    }

    @Test
    public void connectLocalStreamWithBindListener() throws CancellationException, IOException {
        checkConnectStreamWithBindListener(new LocalSocketAddress("server for test"), Xnio.ANY_LOCAL_ADDRESS, XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    @Test
    public void connectStreamWithInvalidAddress() {
        final ChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
        final ChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        Exception expected = null;
        try {
            xnioWorker.connectStream(null, channelListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(unknownSocketAddress, channelListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(null, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(unknownSocketAddress, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(null, new LocalSocketAddress("local"), channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(new LocalSocketAddress("local"), null, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(null, null, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(new InetSocketAddress(800), new LocalSocketAddress("local"), channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(new LocalSocketAddress("local"), new InetSocketAddress(800), channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectStream(unknownSocketAddress, unknownSocketAddress, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private void checkConnectStreamWithBindListenerAndDestination(SocketAddress socketAddress, SocketAddress localSocketAddress, String channelInfo) throws CancellationException, IOException {
        final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.MAX_OUTBOUND_MESSAGE_SIZE, 50000);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel = xnioWorker.connectStream(localSocketAddress, socketAddress, channelListener, bindListener, optionMap);
        assertNotNull(connectedStreamChannel);

        final ConnectedStreamChannelMock channelMock = (ConnectedStreamChannelMock) connectedStreamChannel.get();
        assertNotNull(channelMock);
        assertTrue(channelListener.isInvoked());
        assertSame(channelMock, channelListener.getChannel());
        assertTrue(bindListener.isInvoked());
        assertSame(channelMock, bindListener.getChannel());
        assertEquals(localSocketAddress, channelMock.getLocalAddress());
        assertEquals(socketAddress, channelMock.getPeerAddress());
        assertEquals(optionMap, channelMock.getOptionMap());
        assertEquals(channelInfo, channelMock.getInfo());
    }

    @Test
    public void connectTcpStreamWithBindListenerAndDestination() throws CancellationException, IOException {
        checkConnectStreamWithBindListenerAndDestination(new InetSocketAddress(1500), new InetSocketAddress(500), XnioWorkerMock.TCP_CHANNEL_INFO);
    }

    @Test
    public void connectLocalStreamWithBindListenerAndDestination() throws CancellationException, IOException {
        checkConnectStreamWithBindListenerAndDestination(new LocalSocketAddress("server for test..."), new LocalSocketAddress("one more address"), XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    private void checkAcceptStream(SocketAddress socketAddress, String channelInfo) throws CancellationException, IOException {
        final TestChannelListener<ConnectedStreamChannel> channelListener = new TestChannelListener<ConnectedStreamChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.READ_TIMEOUT, 800000);
        final IoFuture<ConnectedStreamChannel> connectedStreamChannel = xnioWorker.acceptStream(socketAddress, channelListener, bindListener, optionMap);
        assertNotNull(connectedStreamChannel);

        final ConnectedStreamChannelMock channelMock = (ConnectedStreamChannelMock) connectedStreamChannel.get();
        assertNotNull(channelMock);
        assertTrue(channelListener.isInvoked());
        assertSame(channelMock, channelListener.getChannel());
        assertTrue(bindListener.isInvoked());
        assertSame(channelMock, bindListener.getChannel());
        assertEquals(socketAddress, channelMock.getPeerAddress());
        assertEquals(optionMap, channelMock.getOptionMap());
        assertEquals(channelInfo, channelMock.getInfo());
    }

    @Test
    public void acceptTcpStream() throws CancellationException, IOException {
        checkAcceptStream(new InetSocketAddress(1567), XnioWorkerMock.TCP_CHANNEL_INFO);
    }

    @Test
    public void acceptLocalStream() throws CancellationException, IOException {
        checkAcceptStream(new LocalSocketAddress("local address"), XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    @Test
    public void acceptStreamWithInvalidAddress() {
        final ChannelListener<ConnectedStreamChannel> openListener = new TestChannelListener<ConnectedStreamChannel>();
        final ChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        Exception expected = null;
        try {
            xnioWorker.acceptStream(null, openListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.acceptStream(unknownSocketAddress, openListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private void checkConnectDatagram(SocketAddress socketAddress, SocketAddress localAddress, String channelInfo) throws CancellationException, IOException {
        final TestChannelListener<ConnectedMessageChannel> channelListener = new TestChannelListener<ConnectedMessageChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.WRITE_TIMEOUT, 800000);
        final IoFuture<ConnectedMessageChannel> connectedDatagramFuture = xnioWorker.connectDatagram(socketAddress, channelListener, bindListener, optionMap);
        assertNotNull(connectedDatagramFuture);

        final FramedMessageChannel channel = (FramedMessageChannel) connectedDatagramFuture.get();

        assertTrue(channelListener.isInvoked());
        assertSame(channel, channelListener.getChannel());
        assertTrue(bindListener.isInvoked());
        assertSame(channel, bindListener.getChannel());
        assertEquals(localAddress, channel.getLocalAddress());
        assertEquals(socketAddress, channel.getPeerAddress());
        assertEquals(800000, (int) channel.getOption(Options.WRITE_TIMEOUT));
        final ChannelMock channelMock = (ChannelMock) channel.getChannel();
        assertEquals(optionMap, channelMock.getOptionMap());
        assertEquals(channelInfo, channelMock.getInfo());
    }

    @Test
    public void connectTcpDatagram() throws CancellationException, IOException {
        checkConnectDatagram(new InetSocketAddress(1050), Xnio.ANY_INET_ADDRESS, XnioWorkerMock.UDP_CHANNEL_INFO);
    }

    @Test
    public void connectLocalDatagram() throws CancellationException, IOException {
        checkConnectDatagram(new LocalSocketAddress("local"), Xnio.ANY_LOCAL_ADDRESS, XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    private void checkConnectDatagramWithBindAddress(SocketAddress socketAddress, SocketAddress bindAddress, String channelInfo) throws CancellationException, IOException {
        final TestChannelListener<ConnectedMessageChannel> channelListener = new TestChannelListener<ConnectedMessageChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final OptionMap optionMap = OptionMap.create(Options.STACK_SIZE, 9000000l);
        final IoFuture<ConnectedMessageChannel> connectedDatagramFuture = xnioWorker.connectDatagram(bindAddress, socketAddress, channelListener, bindListener, optionMap);
        assertNotNull(connectedDatagramFuture);

        final FramedMessageChannel channel = (FramedMessageChannel) connectedDatagramFuture.get();

        assertTrue(channelListener.isInvoked());
        assertSame(channel, channelListener.getChannel());
        assertTrue(bindListener.isInvoked());
        assertSame(channel, bindListener.getChannel());
        assertEquals(bindAddress, channel.getLocalAddress());
        assertEquals(socketAddress, channel.getPeerAddress());
        assertEquals(9000000l, (long) channel.getOption(Options.STACK_SIZE));
        final ChannelMock channelMock = (ChannelMock) channel.getChannel();
        assertEquals(optionMap, channelMock.getOptionMap());
        assertEquals(channelInfo, channelMock.getInfo());
    }

    @Test
    public void connectTcpDatagramWithBindAddress() throws CancellationException, IOException {
        checkConnectDatagramWithBindAddress(new InetSocketAddress(1050), new InetSocketAddress(2050), XnioWorkerMock.UDP_CHANNEL_INFO);
    }

    @Test
    public void connectLocalDatagramWithBindAddress() throws CancellationException, IOException {
        checkConnectDatagramWithBindAddress(new LocalSocketAddress("local1"), new LocalSocketAddress("local2"), XnioWorkerMock.LOCAL_CHANNEL_INFO);
    }

    @Test
    public void connectDatagramWithInvalidAddress() {
        final ChannelListener<ConnectedMessageChannel> channelListener = new TestChannelListener<ConnectedMessageChannel>();
        final ChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        Exception expected = null;
        try {
            xnioWorker.connectDatagram(null, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectDatagram(unknownSocketAddress, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectDatagram(null, new LocalSocketAddress("local"), channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectDatagram(new LocalSocketAddress("local"), null, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectDatagram(null, null, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectDatagram(new InetSocketAddress(800), new LocalSocketAddress("local"), channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectDatagram(new LocalSocketAddress("local"), new InetSocketAddress(800), channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectDatagram(unknownSocketAddress, unknownSocketAddress, channelListener, bindListener, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void acceptDatagram() throws CancellationException, IOException {
        final TestChannelListener<ConnectedMessageChannel> channelListener = new TestChannelListener<ConnectedMessageChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        final SocketAddress localAddress = new LocalSocketAddress("here");
        final OptionMap optionMap = OptionMap.create(Options.STACK_SIZE, 990000l);
        final IoFuture<ConnectedMessageChannel> connectedDatagramFuture = xnioWorker.acceptDatagram(localAddress, channelListener, bindListener, optionMap);
        assertNotNull(connectedDatagramFuture);

        final FramedMessageChannel channel = (FramedMessageChannel) connectedDatagramFuture.get();

        assertTrue(channelListener.isInvoked());
        assertSame(channel, channelListener.getChannel());
        assertTrue(bindListener.isInvoked());
        assertSame(channel, bindListener.getChannel());
        assertEquals(localAddress, channel.getPeerAddress());
        assertEquals(990000l, (long) channel.getOption(Options.STACK_SIZE));
        final ChannelMock channelMock = (ChannelMock) channel.getChannel();
        assertEquals(optionMap, channelMock.getOptionMap());
    }

    @Test
    public void acceptDatagramWithInvalidAddress() {
        final TestChannelListener<ConnectedMessageChannel> channelListener = new TestChannelListener<ConnectedMessageChannel>();
        final TestChannelListener<BoundChannel> bindListener = new TestChannelListener<BoundChannel>();
        Exception expected = null;
        try {
            xnioWorker.acceptDatagram(null, channelListener, bindListener, OptionMap.EMPTY);
        } catch (IllegalArgumentException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.acceptDatagram(new InetSocketAddress(10), channelListener, bindListener, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.acceptDatagram(unknownSocketAddress, channelListener, bindListener, OptionMap.EMPTY);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void createUdpServer() throws IOException {
        final InetSocketAddress address = new InetSocketAddress(0);
        final OptionMap optionMap = OptionMap.create(Options.MULTICAST, true, Options.SECURE, false);
        MulticastMessageChannel channel = xnioWorker.createUdpServer(address, optionMap);
        assertNotNull(channel);
        // check address
        assertEquals(address, channel.getLocalAddress());
        // check optionMap
        assertTrue(channel instanceof MulticastMessageChannelMock);
        ChannelMock channelMock = (ChannelMock) channel;
        assertEquals(optionMap, channelMock.getOptionMap());
    }

    @Test
    public void createUdpServerWithListener() throws IOException {
        final InetSocketAddress address = new InetSocketAddress(0);
        final TestChannelListener<MulticastMessageChannel> listener = new TestChannelListener<MulticastMessageChannel>();
        final OptionMap optionMap = OptionMap.create(Options.MULTICAST, true, Options.SECURE, true);
        MulticastMessageChannel channel = xnioWorker.createUdpServer(address, listener, optionMap);
        assertNotNull(channel);
        // check address
        assertEquals(address, channel.getLocalAddress());
        // check listener
        assertTrue(listener.isInvoked());
        assertSame(channel, listener.getChannel());
        // check optionMap
        assertTrue(channel instanceof MulticastMessageChannelMock);
        ChannelMock channelMock = (ChannelMock) channel;
        assertEquals(optionMap, channelMock.getOptionMap());
    }

    @Test
    public void executeCommandsAndShutdownTaskPool() throws InterruptedException {
        final TestCommand command1 = new TestCommand();
        final TestCommand command2 = new TestCommand();
        final TestCommand command3 = new TestCommand();
        final TestCommand command4 = new TestCommand();
        final TestCommand command5 = new TestCommand();

        xnioWorker.execute(command1);
        xnioWorker.execute(command2);
        xnioWorker.execute(command3);

        xnioWorker.shutDownTaskPool();

        RejectedExecutionException expected = null;
        try {
            xnioWorker.execute(command4);
        } catch (RejectedExecutionException e) {
            expected = e;
        }
        assertNotNull(expected);

        command1.waitCompletion();
        command2.waitCompletion();
        command3.waitCompletion();

        xnioWorker.shutDownTaskPoolNow();

        expected = null;
        try {
            xnioWorker.execute(command5);
        } catch (RejectedExecutionException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void shutdownTaskPoolNow() throws InterruptedException {
        final TestCommand command = new TestCommand();
        xnioWorker.shutDownTaskPoolNow();
        RejectedExecutionException expected = null;
        try {
            xnioWorker.execute(command);
        } catch (RejectedExecutionException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    @Test
    public void optionsSupported() {
        assertTrue(xnioWorker.supportsOption(Options.WORKER_TASK_CORE_THREADS));
        assertTrue(xnioWorker.supportsOption(Options.WORKER_TASK_MAX_THREADS));
        assertTrue(xnioWorker.supportsOption(Options.WORKER_TASK_KEEPALIVE));
        
        assertFalse(xnioWorker.supportsOption(Options.ALLOW_BLOCKING));
        assertFalse(xnioWorker.supportsOption(Options.MULTICAST));
        assertFalse(xnioWorker.supportsOption(Options.BROADCAST));
        assertFalse(xnioWorker.supportsOption(Options.CLOSE_ABORT));
        assertFalse(xnioWorker.supportsOption(Options.RECEIVE_BUFFER));
        assertFalse(xnioWorker.supportsOption(Options.REUSE_ADDRESSES));
        assertFalse(xnioWorker.supportsOption(Options.SEND_BUFFER));
        assertFalse(xnioWorker.supportsOption(Options.TCP_NODELAY));
        assertFalse(xnioWorker.supportsOption(Options.MULTICAST_TTL));
        assertFalse(xnioWorker.supportsOption(Options.IP_TRAFFIC_CLASS));
        assertFalse(xnioWorker.supportsOption(Options.TCP_OOB_INLINE));
        assertFalse(xnioWorker.supportsOption(Options.KEEP_ALIVE));
        assertFalse(xnioWorker.supportsOption(Options.BACKLOG));
        assertFalse(xnioWorker.supportsOption(Options.READ_TIMEOUT));
        assertFalse(xnioWorker.supportsOption(Options.WRITE_TIMEOUT));
        assertFalse(xnioWorker.supportsOption(Options.MAX_INBOUND_MESSAGE_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.MAX_OUTBOUND_MESSAGE_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_ENABLED));
        assertFalse(xnioWorker.supportsOption(Options.SSL_CLIENT_AUTH_MODE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_ENABLED_CIPHER_SUITES));
        assertFalse(xnioWorker.supportsOption(Options.SSL_SUPPORTED_CIPHER_SUITES));
        assertFalse(xnioWorker.supportsOption(Options.SSL_ENABLED_PROTOCOLS));
        assertFalse(xnioWorker.supportsOption(Options.SSL_SUPPORTED_PROTOCOLS));
        assertFalse(xnioWorker.supportsOption(Options.SSL_PROVIDER));
        assertFalse(xnioWorker.supportsOption(Options.SSL_PROTOCOL));
        assertFalse(xnioWorker.supportsOption(Options.SSL_ENABLE_SESSION_CREATION));
        assertFalse(xnioWorker.supportsOption(Options.SSL_USE_CLIENT_MODE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_CLIENT_SESSION_CACHE_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_CLIENT_SESSION_TIMEOUT));
        assertFalse(xnioWorker.supportsOption(Options.SSL_SERVER_SESSION_CACHE_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_SERVER_SESSION_TIMEOUT));
        assertFalse(xnioWorker.supportsOption(Options.SSL_JSSE_KEY_MANAGER_CLASSES));
        assertFalse(xnioWorker.supportsOption(Options.SSL_JSSE_TRUST_MANAGER_CLASSES));
        assertFalse(xnioWorker.supportsOption(Options.SSL_RNG_OPTIONS));
        assertFalse(xnioWorker.supportsOption(Options.SSL_PACKET_BUFFER_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_APPLICATION_BUFFER_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_PACKET_BUFFER_REGION_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_APPLICATION_BUFFER_REGION_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.SSL_STARTTLS));
        assertFalse(xnioWorker.supportsOption(Options.SSL_PEER_HOST_NAME));
        assertFalse(xnioWorker.supportsOption(Options.SSL_PEER_PORT));
        assertFalse(xnioWorker.supportsOption(Options.USE_DIRECT_BUFFERS));
        assertFalse(xnioWorker.supportsOption(Options.SECURE));
        assertFalse(xnioWorker.supportsOption(Options.SASL_POLICY_FORWARD_SECRECY));
        assertFalse(xnioWorker.supportsOption(Options.SASL_POLICY_NOACTIVE));
        assertFalse(xnioWorker.supportsOption(Options.SASL_POLICY_NOANONYMOUS));
        assertFalse(xnioWorker.supportsOption(Options.SASL_POLICY_NODICTIONARY));
        assertFalse(xnioWorker.supportsOption(Options.SASL_POLICY_NOPLAINTEXT));
        assertFalse(xnioWorker.supportsOption(Options.SASL_POLICY_PASS_CREDENTIALS));
        assertFalse(xnioWorker.supportsOption(Options.SASL_QOP));
        assertFalse(xnioWorker.supportsOption(Options.SASL_STRENGTH));
        assertFalse(xnioWorker.supportsOption(Options.SASL_SERVER_AUTH));
        assertFalse(xnioWorker.supportsOption(Options.SASL_REUSE));
        assertFalse(xnioWorker.supportsOption(Options.SASL_MECHANISMS));
        assertFalse(xnioWorker.supportsOption(Options.SASL_DISALLOWED_MECHANISMS));
        assertFalse(xnioWorker.supportsOption(Options.SASL_PROPERTIES));
        assertFalse(xnioWorker.supportsOption(Options.FILE_ACCESS));
        assertFalse(xnioWorker.supportsOption(Options.STACK_SIZE));
        assertFalse(xnioWorker.supportsOption(Options.WORKER_NAME));
        assertFalse(xnioWorker.supportsOption(Options.THREAD_PRIORITY));
        assertFalse(xnioWorker.supportsOption(Options.THREAD_DAEMON));
        assertFalse(xnioWorker.supportsOption(Options.WORKER_READ_THREADS));
        assertFalse(xnioWorker.supportsOption(Options.WORKER_WRITE_THREADS));
        assertFalse(xnioWorker.supportsOption(Options.WORKER_ESTABLISH_WRITING));
        assertFalse(xnioWorker.supportsOption(Options.WORKER_ACCEPT_THREADS));
        assertFalse(xnioWorker.supportsOption(Options.WORKER_TASK_LIMIT));
        assertFalse(xnioWorker.supportsOption(Options.CORK));
        assertFalse(xnioWorker.supportsOption(Options.CONNECTION_HIGH_WATER));
        assertFalse(xnioWorker.supportsOption(Options.CONNECTION_LOW_WATER));
    }

    @Test
    public void setAndGetOption() throws IllegalArgumentException, IOException {
        xnioWorker.setOption(Options.WORKER_TASK_CORE_THREADS, 3);
        assertEquals(3, (int) xnioWorker.setOption(Options.WORKER_TASK_CORE_THREADS, 5));
        assertEquals(5, (int) xnioWorker.getOption(Options.WORKER_TASK_CORE_THREADS));

        xnioWorker.setOption(Options.WORKER_TASK_MAX_THREADS, 15);
        assertEquals(15, (int) xnioWorker.setOption(Options.WORKER_TASK_MAX_THREADS, 8));
        assertEquals(8, (int) xnioWorker.getOption(Options.WORKER_TASK_MAX_THREADS));

        xnioWorker.setOption(Options.WORKER_TASK_KEEPALIVE, 500);
        assertEquals(500l, (int) xnioWorker.setOption(Options.WORKER_TASK_KEEPALIVE, 50));
        assertEquals(50, (int) xnioWorker.getOption(Options.WORKER_TASK_KEEPALIVE));

        assertNull(xnioWorker.setOption(Options.KEEP_ALIVE, true));
        assertNull(xnioWorker.getOption(Options.KEEP_ALIVE));
    }

    @Test
    public void optionRetrieval() throws IllegalArgumentException, IOException {
        final Xnio xnio = Xnio.getInstance();
        final OptionMap.Builder builder = OptionMap.builder();
        builder.set(Options.WORKER_NAME, "**WoRkEr**");
        builder.set(Options.WORKER_TASK_LIMIT, 0x8000);
        builder.set(Options.WORKER_TASK_CORE_THREADS, 10);
        builder.set(Options.WORKER_TASK_MAX_THREADS, 20);
        builder.set(Options.WORKER_TASK_KEEPALIVE, 300);
        builder.set(Options.THREAD_DAEMON, true);

        xnioWorker = xnio.createWorker(builder.getMap());
        assertNotNull(xnioWorker);
        assertEquals("**WoRkEr**", xnioWorker.getName());
        assertNull(xnioWorker.getOption(Options.WORKER_NAME));
        assertNull(xnioWorker.getOption(Options.WORKER_TASK_LIMIT));
        assertEquals(10, (int) xnioWorker.getOption(Options.WORKER_TASK_CORE_THREADS));
        assertEquals(20, (int) xnioWorker.getOption(Options.WORKER_TASK_MAX_THREADS));
        assertEquals(300, (int) xnioWorker.getOption(Options.WORKER_TASK_KEEPALIVE));
    }

    @Test
    public void implementationMustOverrideMethods() throws IOException {
        final XnioWorker xnioWorker = new XnioWorker(Xnio.getInstance(), Thread.currentThread().getThreadGroup(),
                OptionMap.EMPTY, new Runnable() {public void run() {}}) {

            @Override
            public void shutdown() {}

            @Override
            public List<Runnable> shutdownNow() {return null;}

            @Override
            public boolean isShutdown() {return false;}

            @Override
            public boolean isTerminated() {return false;}

            @Override
            public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
                return false;
            }

            @Override
            public void awaitTermination() throws InterruptedException {
                
            }

            @Override
            protected void doMigration(final CloseableChannel channel) throws IOException {
            }
        };
        UnsupportedOperationException expected = null;
        try {
            xnioWorker.createTcpServer(null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.createLocalStreamServer(null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectTcpStream(null, null, null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectLocalStream(null, null, null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.acceptTcpStream(null, null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.acceptLocalStream(null, null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectUdpDatagram(null, null, null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.connectLocalDatagram(null, null, null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.acceptLocalDatagram(null, null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.createUdpServer(null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.createPipe(null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);

        expected = null;
        try {
            xnioWorker.createOneWayPipe(null, null, null);
        } catch (UnsupportedOperationException e) {
            expected = e;
        }
        assertNotNull(expected);
    }

    private static class TestChannelListener<C extends Channel> implements ChannelListener<C> {

        private boolean invoked = false;
        private C channel;

        @Override
        public void handleEvent(C c) {
            invoked = true;
            channel = c;
        }

        public boolean isInvoked() {
            return invoked;
        }

        public C getChannel() {
            return channel;
        }
    }

    private static class TestCommand implements Runnable {

        private CountDownLatch latch = new CountDownLatch(1);
        public boolean executed = false;

        @Override
        public void run() {
            executed = true;
            latch.countDown();
        }

        public void waitCompletion() throws InterruptedException {
            latch.await();
        }

        public boolean waitCompletion(long timeout) throws InterruptedException {
            latch.await(timeout, TimeUnit.MILLISECONDS);
            return executed;
        }
    }
}
