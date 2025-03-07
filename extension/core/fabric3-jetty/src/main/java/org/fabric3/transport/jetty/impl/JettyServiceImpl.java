/*
 * Fabric3
 * Copyright (c) 2009-2015 Metaform Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Portions originally based on Apache Tuscany 2007
 * licensed under the Apache 2.0 license.
 */
package org.fabric3.transport.jetty.impl;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.NCSARequestLog;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.ContextHandlerCollection;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.ServletMapping;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.fabric3.api.annotation.monitor.Monitor;
import org.fabric3.api.host.Fabric3Exception;
import org.fabric3.api.host.runtime.HostInfo;
import org.fabric3.api.model.type.RuntimeMode;
import org.fabric3.spi.discovery.DiscoveryAgent;
import org.fabric3.spi.discovery.ServiceEntry;
import org.fabric3.spi.host.Port;
import org.fabric3.spi.host.PortAllocator;
import org.fabric3.spi.management.ManagementService;
import org.fabric3.spi.runtime.event.EventService;
import org.fabric3.spi.runtime.event.JoinDomainCompleted;
import org.fabric3.spi.security.AuthenticationService;
import org.fabric3.spi.security.KeyStoreManager;
import org.fabric3.spi.transport.Transport;
import org.fabric3.transport.jetty.JettyService;
import org.fabric3.transport.jetty.management.ManagedHashSessionManager;
import org.fabric3.transport.jetty.management.ManagedServletHandler;
import org.fabric3.transport.jetty.management.ManagedServletHolder;
import org.fabric3.transport.jetty.management.ManagedStatisticsHandler;
import org.oasisopen.sca.annotation.Constructor;
import org.oasisopen.sca.annotation.Destroy;
import org.oasisopen.sca.annotation.EagerInit;
import org.oasisopen.sca.annotation.Init;
import org.oasisopen.sca.annotation.Property;
import org.oasisopen.sca.annotation.Reference;
import org.oasisopen.sca.annotation.Service;

/**
 * Implements an HTTP transport service using Jetty.
 */
@EagerInit
@Service({JettyService.class, Transport.class})
public class JettyServiceImpl implements JettyService, Transport {
    private static final String STATISTICS = "transports/http/container/statistics";
    private static final String MAPPINGS = "transports/http/container/mappings";
    private static final String SERVLETS = "transports/http/container/servlets";
    private static final String SESSIONS = "transports/http/container/sessions";
    private static final String ORG_ECLIPSE_JETTY_UTIL_LOG_CLASS = "org.eclipse.jetty.util.log.class";
    private static final String HTTP_SERVLETS = "HTTP/servlets";
    private static final int DEFAULT_HTTP_PORT = 8080;
    private static final int DEFAULT_HTTPS_PORT = 8484;

    private static final String ROOT = "/";

    private ExecutorService executorService;
    private ManagementService managementService;
    private PortAllocator portAllocator;
    private EventService eventService;
    private HostInfo hostInfo;
    private TransportMonitor monitor;

    private DiscoveryAgent discoveryAgent;

    private KeyStoreManager keyStoreManager;
    private AuthenticationService authenticationService;

    private final Object joinLock = new Object();
    private boolean enableHttps;
    private boolean redirectHttp;
    // port to redirect to when redirectHttp is enabled. This may be different than the HTTPS port if port forwarding is used, e.g. 443--->8182
    private int redirectHttpsPort = -1;

    private int configuredHttpPort = -1;
    private String configuredHttpHost;
    private Port selectedHttp;
    private int configuredHttpsPort = -1;
    private Port selectedHttps;
    private String configuredHttpsHost;

    // log file attributes
    private String logFilename;
    private boolean logExtended;
    private boolean logAppend;
    private int logRetainDays;
    private boolean logPreferProxiedForAddress;
    private String logDateFormat = "dd/MMM/yyyy:HH:mm:ss Z";
    private String logFilenameDateFormat;
    private Locale logLocale = Locale.getDefault();
    private String logTimeZone = "GMT";
    private boolean logLatency;
    private boolean logCookies;
    private boolean logServer;
    private boolean logDispatch;

    private boolean sendServerVersion;
    private boolean debug;
    private Server server;
    private ManagedServletHandler servletHandler;
    private ServerConnector httpConnector;
    private ServerConnector sslConnector;

    private ContextHandlerCollection rootHandler;
    private ManagedStatisticsHandler statisticsHandler;
    private ManagedHashSessionManager sessionManager;

    static {
        // replace the static Jetty logger
        System.setProperty(ORG_ECLIPSE_JETTY_UTIL_LOG_CLASS, JettyLogger.class.getName());
    }

    @Constructor
    public JettyServiceImpl(@Reference(name = "executorService") ExecutorService executorService,
                            @Reference ManagementService managementService,
                            @Reference PortAllocator portAllocator,
                            @Reference EventService eventService,
                            @Reference HostInfo hostInfo,
                            @Monitor TransportMonitor monitor) {
        this.executorService = executorService;
        this.managementService = managementService;
        this.portAllocator = portAllocator;
        this.eventService = eventService;
        this.hostInfo = hostInfo;
        this.monitor = monitor;
        // Re-route the Jetty logger to use a monitor
        JettyLogger.setMonitor(monitor);
        if (debug) {
            JettyLogger.enableDebug();
        }
    }

    public JettyServiceImpl(PortAllocator portAllocator, TransportMonitor monitor, EventService eventService, HostInfo hostInfo) {
        this.portAllocator = portAllocator;
        this.monitor = monitor;
        this.eventService = eventService;
        this.hostInfo = hostInfo;
    }

    @Reference(required = false)
    public void setKeyStoreManager(KeyStoreManager keyStoreManager) {
        this.keyStoreManager = keyStoreManager;
    }

    @Reference(required = false)
    public void setAuthenticationService(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
    }

    @Reference(required = false)
    public void setAgent(DiscoveryAgent discoveryAgent) {
        this.discoveryAgent = discoveryAgent;
    }

    @Property(required = false)
    public void setEnableHttps(boolean enableHttps) {
        this.enableHttps = enableHttps;
    }

    @Property(required = false)
    public void setRedirectHttp(boolean redirectHttp) {
        this.redirectHttp = redirectHttp;
    }

    @Property(required = false)
    public void setRedirectHttpsPort(int redirectHttpsPort) {
        this.redirectHttpsPort = redirectHttpsPort;
    }

    @Property(required = false)
    public void setHttpPort(String httpPort) {
        String[] tokens = httpPort.split("-");
        if (tokens.length == 1) {
            // port specified
            configuredHttpPort = parsePortNumber(tokens[0], "HTTP");
        } else if (tokens.length == 2) {
            throw new IllegalArgumentException("Port ranges no longer supported via HTTP configuration. Use the runtime port.range attribute");
        }
    }

    @Property(required = false)
    public void setHttpHost(String host) {
        configuredHttpHost = host;
    }

    @Property(required = false)
    public void setHttpsPort(String httpsPort) {
        String[] tokens = httpsPort.split("-");
        if (tokens.length == 1) {
            // port specified
            configuredHttpsPort = parsePortNumber(tokens[0], "HTTPS");

        } else if (tokens.length == 2) {
            throw new IllegalArgumentException("Port ranges no longer supported via HTTP configuration. Use the runtime port.range attribute");
        }
    }

    @Property(required = false)
    public void setHttpsHost(String host) {
        configuredHttpsHost = host;
    }

    @Property(required = false)
    public void setSendServerVersion(boolean sendServerVersion) {
        this.sendServerVersion = sendServerVersion;
    }

    @Property(required = false)
    public void setDebug(boolean val) {
        debug = val;
    }

    @Property(required = false)
    public void setLogFilename(String logFilename) {
        File logDir = new File(hostInfo.getDataDir(), "log");
        logDir.mkdirs();
        this.logFilename = new File(logDir, logFilename).getAbsolutePath();
    }

    @Property(required = false)
    public void setLogExtended(boolean logExtended) {
        this.logExtended = logExtended;
    }

    @Property(required = false)
    public void setLogAppend(boolean logAppend) {
        this.logAppend = logAppend;
    }

    @Property(required = false)
    public void setLogRetainDays(int logRetainDays) {
        this.logRetainDays = logRetainDays;
    }

    @Property(required = false)
    public void setLogPreferProxiedForAddress(boolean logPreferProxiedForAddress) {
        this.logPreferProxiedForAddress = logPreferProxiedForAddress;
    }

    @Property(required = false)
    public void setLogDateFormat(String logDateFormat) {
        this.logDateFormat = logDateFormat;
    }

    @Property(required = false)
    public void setLogFilenameDateFormat(String logFilenameDateFormat) {
        this.logFilenameDateFormat = logFilenameDateFormat;
    }

    @Property(required = false)
    public void setLogLocale(String logLocale) {
        this.logLocale = new Locale(logLocale);
    }

    @Property(required = false)
    public void setLogTimeZone(String logTimeZone) {
        this.logTimeZone = logTimeZone;
    }

    @Property(required = false)
    public void setLogLatency(boolean logLatency) {
        this.logLatency = logLatency;
    }

    @Property(required = false)
    public void setLogCookies(boolean logCookies) {
        this.logCookies = logCookies;
    }

    @Property(required = false)
    public void setLogServer(boolean logServer) {
        this.logServer = logServer;
    }

    @Property(required = false)
    public void setLogDispatch(boolean logDispatch) {
        this.logDispatch = logDispatch;
    }

    @Init
    public void init() throws Fabric3Exception {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            server = new Server(createThreadPool());
            if (authenticationService != null) {
                // setup authentication if the authentication service is available 
                Fabric3LoginService loginService = new Fabric3LoginService(authenticationService);
                server.addBean(loginService);
            }

            initializeConnectors();
            initializeHandlers();
            server.setStopAtShutdown(true);

            monitor.startHttpListener(selectedHttp.getNumber());
            if (enableHttps) {
                monitor.startHttpsListener(selectedHttps.getNumber());
            }
            server.start();
            if (managementService != null) {
                managementService.export(STATISTICS, "HTTP", "HTTP transport statistics", statisticsHandler);
                managementService.export(MAPPINGS, "HTTP", "Servlet management beans", servletHandler);
                managementService.export(SESSIONS, "HTTP", "Servlet session manager", sessionManager);
            }
            eventService.subscribe(JoinDomainCompleted.class, event -> registerSockets());
        } catch (Exception e) {
            throw new Fabric3Exception("Error starting Jetty service", e);
        } finally {
            Thread.currentThread().setContextClassLoader(old);
        }
    }

    @Destroy
    public void destroy() throws Exception {
        synchronized (joinLock) {
            joinLock.notifyAll();
        }
        if (managementService != null) {
            managementService.remove(STATISTICS, "HTTP");
            managementService.remove(MAPPINGS, "HTTP");
            managementService.remove(SESSIONS, "HTTP");
        }
        if (servletHandler != null && servletHandler.getServlets() != null) {
            for (ServletHolder holder : servletHandler.getServlets()) {
                if (managementService != null) {
                    managementService.remove(holder.getName(), HTTP_SERVLETS);
                }
            }
        }
        server.stop();
        portAllocator.release("HTTP");
        if (enableHttps) {
            portAllocator.release("HTTPS");
        }
    }

    public String getHostType() {
        return "Jetty";
    }

    public URL getBaseHttpUrl() {
        if (httpConnector != null) {
            try {
                String host = httpConnector.getHost();
                if (host == null) {
                    host = InetAddress.getLocalHost().getHostAddress();
                }
                return new URL("http://" + host + ":" + getHttpPort());
            } catch (UnknownHostException | MalformedURLException e) {
                throw new IllegalStateException(e);
            }

        }
        return null;
    }

    public URL getBaseHttpsUrl() {
        if (sslConnector != null) {
            try {
                String host = sslConnector.getHost();
                if (host == null) {
                    host = InetAddress.getLocalHost().getHostAddress();
                }
                return new URL("https://" + host + ":" + getHttpPort());
            } catch (UnknownHostException | MalformedURLException e) {
                throw new IllegalStateException(e);
            }

        }
        return null;
    }

    public void suspend() {
        if (httpConnector != null && httpConnector.isRunning()) {
            try {
                httpConnector.stop();
            } catch (Exception e) {
                monitor.exception("Error suspending HTTP connector", e);
            }
        }
        if (sslConnector != null && sslConnector.isRunning()) {
            try {
                sslConnector.stop();
            } catch (Exception e) {
                monitor.exception("Error suspending SSL connector", e);
            }
        }
    }

    public void resume() {
        if (httpConnector != null && httpConnector.isStopped()) {
            try {
                httpConnector.start();
            } catch (Exception e) {
                monitor.exception("Error resuming HTTP connector", e);
            }
        }
        if (sslConnector != null && sslConnector.isStopped()) {
            try {
                sslConnector.start();
            } catch (Exception e) {
                monitor.exception("Error resuming SSL connector", e);
            }
        }
    }

    public int getHttpPort() {
        return selectedHttp.getNumber();
    }

    public int getHttpsPort() {
        if (selectedHttps == null) {
            return -1;
        }
        return selectedHttps.getNumber();
    }

    public boolean isHttpsEnabled() {
        return selectedHttps != null;
    }

    public synchronized void registerMapping(String path, Servlet servlet) {
        ServletHolder holder = new ManagedServletHolder(servlet);
        servletHandler.addServlet(holder);
        ServletMapping mapping = new ServletMapping();
        mapping.setServletName(holder.getName());
        mapping.setPathSpec(path);
        servletHandler.addServletWithMapping(holder, path);
        if (managementService != null) {
            try {
                ServletManager manager = new ServletManager(holder);
                managementService.export(encode(path), HTTP_SERVLETS, "Registered transport servlets", manager);
            } catch (Fabric3Exception e) {
                monitor.exception("Exception exporting servlet management object:" + holder.getContextPath(), e);
            }
        }
    }

    private String encode(String path) {
        if (path.endsWith("/*")) {
            path = path.substring(0, path.length() - 2);
        } else if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.startsWith("/")) {
            return SERVLETS + "/" + path.substring(1).replace("/", "-");
        } else {
            return SERVLETS + "/" + path.replace("/", "-");
        }
    }

    public synchronized Servlet unregisterMapping(String path) {
        List<ServletMapping> mappings = new ArrayList<>();
        List<String> names = new ArrayList<>();
        for (ServletMapping mapping : servletHandler.getServletMappings()) {
            for (String spec : mapping.getPathSpecs()) {
                if (spec.equals(path)) {
                    // ok even though a servlet can be mapped to multiple paths in Jetty since ServletHost only allows one path per registration
                    names.add(mapping.getServletName());
                    continue;
                }
                mappings.add(mapping);
            }
        }
        Servlet servlet = null;
        List<ServletHolder> holders = new ArrayList<>();
        for (ServletHolder holder : servletHandler.getServlets()) {
            if (!names.contains(holder.getName())) {
                holders.add(holder);
            } else {
                try {
                    servlet = holder.getServlet();
                    if (managementService != null) {
                        managementService.remove(encode(path), HTTP_SERVLETS);
                    }
                } catch (ServletException e) {
                    monitor.exception("Exception getting servlet:" + holder.getContextPath(), e);
                } catch (Fabric3Exception e) {
                    monitor.exception("Exception removing servlet management object:" + holder.getContextPath(), e);
                }
            }
        }
        servletHandler.setServlets(holders.toArray(new ServletHolder[holders.size()]));
        servletHandler.setServletMappings(mappings.toArray(new ServletMapping[mappings.size()]));
        return servlet;
    }

    public synchronized boolean isMappingRegistered(String path) {
        ServletMapping[] mappings = servletHandler.getServletMappings();
        if (mappings == null) {
            return false;
        }
        for (ServletMapping mapping : mappings) {
            for (String spec : mapping.getPathSpecs()) {
                if (spec.equals(path)) {
                    return true;
                }
            }
        }
        return false;
    }

    public Server getServer() {
        return server;
    }

    public void registerHandler(Handler handler) {
        rootHandler.addHandler(handler);
    }

    public void removeHandler(Handler handler) {
        rootHandler.removeHandler(handler);
    }

    private void initializeConnectors() throws IOException, Fabric3Exception {
        selectHttpPort();
        selectHttpsPort();
        selectedHttp.bind(Port.TYPE.TCP);
        if (enableHttps) {
            if (keyStoreManager == null) {
                throw new Fabric3Exception("Key store manager not found - a security extension must be installed");
            }
            selectedHttps.bind(Port.TYPE.TCP);
            // setup HTTP and HTTPS
            String keyStore = keyStoreManager.getKeyStoreLocation().getAbsolutePath();
            String keyPassword = keyStoreManager.getKeyStorePassword();
            String trustStore = keyStoreManager.getTrustStoreLocation().getAbsolutePath();
            String trustPassword = keyStoreManager.getTrustStorePassword();
            String certPassword = keyStoreManager.getCertPassword();

            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConfig.setSendServerVersion(sendServerVersion);
            httpConfig.setSecurePort(selectedHttps.getNumber());
            httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(selectedHttp.getNumber());

            SslContextFactory sslContextFactory = new SslContextFactory();
            sslContextFactory.setRenegotiationAllowed(true);
            sslContextFactory.setKeyStorePath(keyStore);
            sslContextFactory.setKeyStorePassword(keyPassword);
            sslContextFactory.setKeyManagerPassword(certPassword);
            sslContextFactory.setTrustStorePath(trustStore);
            sslContextFactory.setTrustStorePassword(trustPassword);
            sslContextFactory.setExcludeCipherSuites("SSL_RSA_WITH_DES_CBC_SHA",
                                                     "SSL_DHE_RSA_WITH_DES_CBC_SHA",
                                                     "SSL_DHE_DSS_WITH_DES_CBC_SHA",
                                                     "SSL_RSA_EXPORT_WITH_RC4_40_MD5",
                                                     "SSL_RSA_EXPORT_WITH_DES40_CBC_SHA",
                                                     "SSL_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
                                                     "SSL_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA");

            HttpConfiguration httpsConfig = new HttpConfiguration();
            httpsConfig.setSendServerVersion(sendServerVersion);
            httpsConfig.addCustomizer(new SecureRequestCustomizer());
            HttpConnectionFactory factory = new HttpConnectionFactory(httpsConfig);
            sslConnector = new ServerConnector(server, new SslConnectionFactory(sslContextFactory, "http/1.1"), factory);

            sslConnector.setPort(selectedHttps.getNumber());
            sslConnector.setHost(configuredHttpsHost);

            server.setConnectors(new Connector[]{httpConnector, sslConnector});
        } else {
            // setup HTTP
            HttpConfiguration httpConfig = new HttpConfiguration();
            httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
            httpConnector.setPort(selectedHttp.getNumber());
            httpConnector.setSoLingerTime(-1);
            httpConnector.setHost(configuredHttpHost);
            server.setConnectors(new Connector[]{httpConnector});
        }
    }

    /**
     * Registers HTTP and HTTPS socket information with the topology service if it is available.
     */
    private void registerSockets() {
        if (RuntimeMode.VM != hostInfo.getRuntimeMode() && discoveryAgent != null) {
            try {
                String host = httpConnector.getHost();
                if (host == null) {
                    host = InetAddress.getLocalHost().getHostAddress();
                }

                ServiceEntry httpEntry = new ServiceEntry();
                httpEntry.setAddress(host);
                httpEntry.setPort(selectedHttp.getNumber());
                httpEntry.setName("f3.HTTP");
                httpEntry.setTransport("http");

                discoveryAgent.register(httpEntry);

                if (isHttpsEnabled()) {
                    ServiceEntry httpsEntry = new ServiceEntry();
                    httpsEntry.setAddress(host);
                    httpsEntry.setPort(selectedHttps.getNumber());
                    httpsEntry.setName("f3.HTTPS");
                    httpsEntry.setTransport("https");

                    discoveryAgent.register(httpsEntry);
                }
            } catch (UnknownHostException e) {
                monitor.exception("Error registering sockets", e);
            }
        }
    }

    private void selectHttpPort() throws IOException, Fabric3Exception {
        if (configuredHttpPort == -1) {
            if (portAllocator.isPoolEnabled()) {
                selectedHttp = portAllocator.allocate("HTTP", "HTTP");
            } else {
                selectedHttp = portAllocator.reserve("HTTP", "HTTP", DEFAULT_HTTP_PORT);
            }
        } else {
            // port is explicitly assigned
            selectedHttp = portAllocator.reserve("HTTP", "HTTP", configuredHttpPort);
        }
    }

    private void selectHttpsPort() throws IOException, Fabric3Exception {
        if (!enableHttps) {
            return;
        }
        if (configuredHttpsPort == -1) {
            if (portAllocator.isPoolEnabled()) {
                selectedHttps = portAllocator.allocate("HTTPS", "HTTPS");
            } else {
                selectedHttps = portAllocator.reserve("HTTPS", "HTTPS", DEFAULT_HTTPS_PORT);
            }
        } else {
            // port is explicitly assigned
            selectedHttps = portAllocator.reserve("HTTPS", "HTTPS", configuredHttpsPort);
        }
    }

    private ThreadPool createThreadPool() {
        return executorService == null ? new ExecutorThreadPool(100) : new Fabric3ThreadPool();
    }

    private void initializeHandlers() {
        statisticsHandler = new ManagedStatisticsHandler();
        if (logFilename != null) {
            RequestLogHandler requestLogHandler = createLogHandler();
            server.setHandler(requestLogHandler);
        } else {
            server.setHandler(statisticsHandler);
        }

        ExecutionContextHandler executionHandler = new ExecutionContextHandler();
        if (redirectHttp) {
            int port = redirectHttpsPort > 0 ? redirectHttpsPort : selectedHttps.getNumber();
            SecurityRedirectHandler securityHandler = new SecurityRedirectHandler(port);
            statisticsHandler.setHandler(securityHandler);
            securityHandler.setHandler(executionHandler);
        } else {
            statisticsHandler.setHandler(executionHandler);
        }

        rootHandler = new ContextHandlerCollection();
        executionHandler.setHandler(rootHandler);

        ServletContextHandler contextHandler = new ServletContextHandler(rootHandler, ROOT);
        sessionManager = new ManagedHashSessionManager();
        HashSessionIdManager sessionIdManager = new HashSessionIdManager();
        String workerName = hostInfo.getRuntimeName().replace(".", "_"); // Jetty does not accept names with '.' characters
        sessionIdManager.setWorkerName(workerName);
        server.setSessionIdManager(sessionIdManager);
        sessionManager.setIdManager(sessionIdManager);
        SessionHandler sessionHandler = new SessionHandler(sessionManager);
        servletHandler = new ManagedServletHandler();
        sessionHandler.setHandler(servletHandler);
        contextHandler.setHandler(sessionHandler);

        try {
            statisticsHandler.start();
            statisticsHandler.startStatisticsCollection();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private RequestLogHandler createLogHandler() {
        NCSARequestLog requestLog = new NCSARequestLog(logFilename);
        requestLog.setAppend(logAppend);
        requestLog.setExtended(logExtended);
        requestLog.setFilenameDateFormat(logFilenameDateFormat);
        requestLog.setLogCookies(logCookies);
        requestLog.setLogDateFormat(logDateFormat);
        requestLog.setLogDispatch(logDispatch);
        requestLog.setLogLatency(logLatency);
        requestLog.setLogLocale(logLocale);
        requestLog.setLogTimeZone(logTimeZone);
        requestLog.setPreferProxiedForAddress(logPreferProxiedForAddress);
        requestLog.setRetainDays(logRetainDays);
        requestLog.setLogServer(logServer);
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        requestLogHandler.setRequestLog(requestLog);
        requestLogHandler.setHandler(statisticsHandler);
        return requestLogHandler;
    }

    private int parsePortNumber(String portVal, String portType) {
        int port;
        try {
            port = Integer.parseInt(portVal);
            if (port < 0) {
                throw new IllegalArgumentException("Invalid " + portType + " port number specified in the system configuration" + port);
            }
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + portType + " port number specified in the system configuration", e);
        }
        return port;
    }

    /**
     * An integration wrapper to enable use of a {@link ExecutorService} with Jetty
     */
    private class Fabric3ThreadPool implements ThreadPool {

        public boolean dispatch(Runnable work) {
            executorService.execute(work);
            return true;
        }

        public void join() throws InterruptedException {
            synchronized (joinLock) {
                joinLock.wait();
            }
        }

        public int getThreads() {
            throw new UnsupportedOperationException();
        }

        public int getIdleThreads() {
            throw new UnsupportedOperationException();
        }

        public boolean isLowOnThreads() {
            return false;
        }

        public void execute(Runnable work) {
            executorService.execute(work);
        }
    }

}
