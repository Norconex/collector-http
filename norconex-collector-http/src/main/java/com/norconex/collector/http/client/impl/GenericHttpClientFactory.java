/* Copyright 2010-2017 Norconex Inc.
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
package com.norconex.collector.http.client.impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmConstraints;
import java.security.AlgorithmParameters;
import java.security.CryptoPrimitive;
import java.security.Key;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.*;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.Consts;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.NTCredentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CookieStore;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.RedirectStrategy;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.config.ConnectionConfig;
import org.apache.http.conn.SchemePortResolver;
import org.apache.http.conn.UnsupportedSchemeException;
import org.apache.http.conn.socket.LayeredConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.LaxRedirectStrategy;
import org.apache.http.impl.conn.DefaultSchemePortResolver;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContexts;
import org.apache.http.util.Args;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.norconex.collector.core.CollectorException;
import com.norconex.collector.http.client.IHttpClientFactory;
import com.norconex.commons.lang.EqualsUtil;
import com.norconex.commons.lang.config.IXMLConfigurable;
import com.norconex.commons.lang.config.XMLConfigurationUtil;
import com.norconex.commons.lang.encrypt.EncryptionKey;
import com.norconex.commons.lang.encrypt.EncryptionUtil;
import com.norconex.commons.lang.time.DurationParser;
import com.norconex.commons.lang.xml.EnhancedXMLStreamWriter;

/**
 * <p>
 * Default implementation of {@link IHttpClientFactory}.
 * </p> 
 * <h3>Password encryption in XML configuration:</h3>
 * <p>
 * As of 2.4.0, <code>proxyPassword</code> and <code>authPassword</code> 
 * can take a password that has been encrypted using {@link EncryptionUtil}. 
 * In order for the password to be decrypted properly by the crawler, you need
 * to specify the encryption key used to encrypt it. The key can be stored
 * in a few supported locations and a combination of 
 * <code>[auth|proxy]PasswordKey</code>
 * and <code>[auth|proxy]PasswordKeySource</code> must be specified to properly
 * locate the key. The supported sources are:
 * </p> 
 * <table border="1" summary="">
 *   <tr>
 *     <th><code>[...]PasswordKeySource</code></th>
 *     <th><code>[...]PasswordKey</code></th>
 *   </tr>
 *   <tr>
 *     <td><code>key</code></td>
 *     <td>The actual encryption key.</td>
 *   </tr>
 *   <tr>
 *     <td><code>file</code></td>
 *     <td>Path to a file containing the encryption key.</td>
 *   </tr>
 *   <tr>
 *     <td><code>environment</code></td>
 *     <td>Name of an environment variable containing the key.</td>
 *   </tr>
 *   <tr>
 *     <td><code>property</code></td>
 *     <td>Name of a JVM system property containing the key.</td>
 *   </tr>
 * </table>
 *
 * <p>
 * As of 2.7.0, XML configuration entries expecting millisecond durations
 * can be provided in human-readable format (English only), as per 
 * {@link DurationParser} (e.g., "5 minutes and 30 seconds" or "5m30s").
 * </p>
 * 
 * <h3>XML configuration usage:</h3>
 * <pre>
 *  &lt;httpClientFactory class="com.norconex.collector.http.client.impl.GenericHttpClientFactory"&gt;
 *      &lt;cookiesDisabled&gt;[false|true]&lt;/cookiesDisabled&gt;
 *      &lt;connectionTimeout&gt;(milliseconds)&lt;/connectionTimeout&gt;
 *      &lt;socketTimeout&gt;(milliseconds)&lt;/socketTimeout&gt;
 *      &lt;connectionRequestTimeout&gt;(milliseconds)&lt;/connectionRequestTimeout&gt;
 *      &lt;connectionCharset&gt;...&lt;/connectionCharset&gt;
 *      &lt;expectContinueEnabled&gt;[false|true]&lt;/expectContinueEnabled&gt;
 *      &lt;maxRedirects&gt;...&lt;/maxRedirects&gt;
 *      &lt;localAddress&gt;...&lt;/localAddress&gt;
 *      &lt;maxConnections&gt;...&lt;/maxConnections&gt;
 *      &lt;maxConnectionsPerRoute&gt;...&lt;/maxConnectionsPerRoute&gt;
 *      &lt;maxConnectionIdleTime&gt;(milliseconds)&lt;/maxConnectionIdleTime&gt;
 *      &lt;maxConnectionInactiveTime&gt;(milliseconds)&lt;/maxConnectionInactiveTime&gt;
 *
 *      &lt;!-- Be warned: trusting all certificates is usually a bad idea. --&gt;
 *      &lt;trustAllSSLCertificates&gt;[false|true]&lt;/trustAllSSLCertificates&gt;
 *
 *      &lt;!-- Since 2.8.2, you can disable SNI --&gt;
 *      &lt;disableSNI&gt;[false|true]&lt;/disableSNI&gt;
 *
 *      &lt;!-- Since 2.6.2, you can specify SSL/TLS protocols to use --&gt;
 *      &lt;sslProtocols&gt;(coma-separated list)&lt;/sslProtocols&gt;
 *
 *      &lt;proxyHost&gt;...&lt;/proxyHost&gt;
 *      &lt;proxyPort&gt;...&lt;/proxyPort&gt;
 *      &lt;proxyRealm&gt;...&lt;/proxyRealm&gt;
 *      &lt;proxyScheme&gt;...&lt;/proxyScheme&gt;
 *      &lt;proxyUsername&gt;...&lt;/proxyUsername&gt;
 *      &lt;proxyPassword&gt;...&lt;/proxyPassword&gt;
 *      &lt;!-- Use the following if password is encrypted. --&gt;
 *      &lt;proxyPasswordKey&gt;(the encryption key or a reference to it)&lt;/proxyPasswordKey&gt;
 *      &lt;proxyPasswordKeySource&gt;[key|file|environment|property]&lt;/proxyPasswordKeySource&gt;
 *      
 *      &lt;!-- HTTP request headers passed on every HTTP requests --&gt;
 *      &lt;headers&gt;
 *          &lt;header name="(header name)"&gt;(header value)&lt;/header&gt;
 *          &lt;!-- You can repeat this header tag as needed. --&gt;
 *      &lt;/headers&gt;
 *      
 *      &lt;authMethod&gt;[form|basic|digest|ntlm|spnego|kerberos]&lt;/authMethod&gt;
 *      
 *      &lt;!-- These apply to any authentication mechanism --&gt;
 *      &lt;authUsername&gt;...&lt;/authUsername&gt;
 *      &lt;authPassword&gt;...&lt;/authPassword&gt;
 *      &lt;!-- Use the following if password is encrypted. --&gt;
 *      &lt;authPasswordKey&gt;(the encryption key or a reference to it)&lt;/authPasswordKey&gt;
 *      &lt;authPasswordKeySource&gt;[key|file|environment|property]&lt;/authPasswordKeySource&gt;
 *      
 *      &lt;!-- These apply to FORM authentication --&gt;
 *      &lt;authUsernameField&gt;...&lt;/authUsernameField&gt;
 *      &lt;authPasswordField&gt;...&lt;/authPasswordField&gt;
 *      &lt;authURL&gt;...&lt;/authURL&gt;
 *      &lt;authFormCharset&gt;...&lt;/authFormCharset&gt;
 *      &lt;!-- Extra form parameters required to authenticate (since 2.8.0) --&gt;
 *      &lt;authFormParams&gt;
 *          &lt;param name="(param name)"&gt;(param value)&lt;/param&gt;
 *          &lt;!-- You can repeat this param tag as needed. --&gt;
 *      &lt;/authFormParams&gt;
 *      
 *      &lt;!-- These apply to both BASIC and DIGEST authentication --&gt;
 *      &lt;authHostname&gt;...&lt;/authHostname&gt;
 *      &lt;authPort&gt;...&lt;/authPort&gt;
 *      &lt;authRealm&gt;...&lt;/authRealm&gt;
 *      
 *      &lt;!-- This applies to BASIC authentication --&gt;
 *      &lt;authPreemptive&gt;[false|true]&lt;/authPreemptive&gt;
 *      
 *      &lt;!-- These apply to NTLM authentication --&gt;
 *      &lt;authHostname&gt;...&lt;/authHostname&gt;
 *      &lt;authPort&gt;...&lt;/authPort&gt;
 *      &lt;authWorkstation&gt;...&lt;/authWorkstation&gt;
 *      &lt;authDomain&gt;...&lt;/authDomain&gt;
 *
 *  &lt;/httpClientFactory&gt;
 * </pre>
 * 
 * <h4>Usage example:</h4>
 * <p>
 * The following will authenticate the crawler to a web site before crawling.
 * The website uses an HTML form with a username and password fields called
 * "loginUser" and "loginPwd".  
 * </p> 
 * <pre>
 *  &lt;httpClientFactory class="com.norconex.collector.http.client.impl.GenericHttpClientFactory"&gt;
 *      &lt;authUsername&gt;joeUser&lt;/authUsername&gt;
 *      &lt;authPassword&gt;joePasword&lt;/authPassword&gt;
 *      &lt;authUsernameField&gt;loginUser&lt;/authUsernameField&gt;
 *      &lt;authPasswordField&gt;loginPwd&lt;/authPasswordField&gt;
 *      &lt;authURL&gt;http://www.example.com/login&lt;/authURL&gt;
 *  &lt;/httpClientFactory&gt;
 * </pre>
 * @author Pascal Essiembre
 * @since 1.3.0
 */
public class GenericHttpClientFactory 
        implements IHttpClientFactory, IXMLConfigurable {

    private static final Logger LOG = 
            LogManager.getLogger(GenericHttpClientFactory.class);
    
    /** Form-based authentication method. */
    public static final String AUTH_METHOD_FORM = "form";
    /** BASIC authentication method. */
    public static final String AUTH_METHOD_BASIC = "basic";
    /** DIGEST authentication method. */
    public static final String AUTH_METHOD_DIGEST = "digest";
    /** NTLM authentication method. */
    public static final String AUTH_METHOD_NTLM = "ntlm";
    /** Experimental: SPNEGO authentication method. */
    public static final String AUTH_METHOD_SPNEGO = "SPNEGO";
    /** Experimental: Kerberos authentication method. */
    public static final String AUTH_METHOD_KERBEROS = "Kerberos";

    public static final int DEFAULT_TIMEOUT = 30 * 1000;
    public static final int DEFAULT_MAX_REDIRECT = 50;
    public static final int DEFAULT_MAX_CONNECTIONS = 200;
    public static final int DEFAULT_MAX_CONNECTIONS_PER_ROUTE = 20;
    public static final int DEFAULT_MAX_IDLE_TIME = 10 * 1000;

    private static final int FTP_PORT = 80;
    
    private static final SchemePortResolver SCHEME_PORT_RESOLVER =
        new SchemePortResolver() {
            @Override
            public int resolve(HttpHost host) 
                    throws UnsupportedSchemeException {
                Args.notNull(host, "HTTP host");
                final int port = host.getPort();
                if (port > 0) {
                    return port;
                } else {
                    final String name = host.getSchemeName();
                    if (name.equalsIgnoreCase("ftp")) {
                        return FTP_PORT;
                    } else {
                        return DefaultSchemePortResolver.INSTANCE.resolve(host);
                    }
                }
            }
        };
    
    //--- Configurable arguments ---
    private String authMethod;
    private String authURL;
    private String authUsernameField;
    private String authUsername;
    private String authPasswordField;
    private String authPassword;
    private EncryptionKey authPasswordKey;
    private String authHostname;
    private int authPort = -1;
    private String authRealm;
    private String authFormCharset = StandardCharsets.UTF_8.toString();
    private String authWorkstation;
    private String authDomain;
    private boolean authPreemptive;
    private String cookieSpec = CookieSpecs.STANDARD;
    private boolean disableSNI = false;
    private boolean trustAllSSLCertificates;
    private String proxyHost;
    private int proxyPort;
    private String proxyScheme;
    private String proxyUsername;
    private String proxyPassword;
    private EncryptionKey proxyPasswordKey;
    private String proxyRealm;
    private int connectionTimeout = DEFAULT_TIMEOUT;
    private int socketTimeout = DEFAULT_TIMEOUT;
    private int connectionRequestTimeout = DEFAULT_TIMEOUT;
    private String connectionCharset;
    private String localAddress;
    private boolean expectContinueEnabled;
    private int maxRedirects = DEFAULT_MAX_REDIRECT;
    private int maxConnections = DEFAULT_MAX_CONNECTIONS;
    private int maxConnectionsPerRoute = DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
    private int maxConnectionIdleTime = DEFAULT_MAX_IDLE_TIME;
    private int maxConnectionInactiveTime;
    private String[] sslProtocols;
    private final Map<String, String> requestHeaders = new HashMap<>();
    private final Map<String, String> authFormParams = new HashMap<>();
    
    @Override
    public HttpClient createHTTPClient(String userAgent) {
        HttpClientBuilder builder = HttpClientBuilder.create();
        SSLContext sslContext = createSSLContext();
        builder.setSSLContext(sslContext);
        builder.setSSLSocketFactory(createSSLSocketFactory(sslContext));
        builder.setSchemePortResolver(createSchemePortResolver());
        builder.setDefaultRequestConfig(createRequestConfig());
        builder.setProxy(createProxy());
        builder.setDefaultCredentialsProvider(createCredentialsProvider());
        builder.setDefaultConnectionConfig(createConnectionConfig());
        builder.setUserAgent(userAgent);
        builder.setMaxConnTotal(maxConnections);
        builder.setMaxConnPerRoute(maxConnectionsPerRoute);
        builder.evictExpiredConnections();
        builder.evictIdleConnections(
                maxConnectionIdleTime, TimeUnit.MILLISECONDS);
        builder.setDefaultHeaders(createDefaultRequestHeaders());
        builder.setDefaultCookieStore(createDefaultCookieStore());
        
        buildCustomHttpClient(builder);
        
        HttpClient httpClient = builder.build();
        if (AUTH_METHOD_FORM.equalsIgnoreCase(authMethod)) {
            authenticateUsingForm(httpClient);
        }
        
        hackValidateAfterInactivity(httpClient);
        
        return httpClient;
    }

    /**
     * This is a hack to work around 
     * PoolingHttpClientConnectionManager#setValidateAfterInactivity(int)
     * not being exposed to the builder. 
     */
    //TODO get rid of this method in favor of setXXX method when available
    // in a future version of HttpClient (planned for 5.0.0).
    private void hackValidateAfterInactivity(HttpClient httpClient) {
        if (maxConnectionInactiveTime <= 0) {
            return;
        }
        try {
            Object connManager = 
                    FieldUtils.readField(httpClient, "connManager", true);
            if (connManager instanceof PoolingHttpClientConnectionManager) {
                ((PoolingHttpClientConnectionManager) connManager)
                        .setValidateAfterInactivity(maxConnectionInactiveTime);
            } else {
                LOG.warn("\"maxConnectionInactiveTime\" could not be set since "
                        + "internal connection manager does not support it.");
            }
        } catch (Exception e) {
            LOG.warn("\"maxConnectionInactiveTime\" could not be set since "
                    + "internal connection manager does not support it.");
        }
    }
    
    
    /**
     * For implementors to subclass.  Does nothing by default.
     * @param builder http client builder
     */
    protected void buildCustomHttpClient(HttpClientBuilder builder) {
        //do nothing by default
    }
    
    protected void authenticateUsingForm(HttpClient httpClient) {
        HttpPost post = new HttpPost(getAuthURL());

        List<NameValuePair> formparams = new ArrayList<>();
        formparams.add(new BasicNameValuePair(
                getAuthUsernameField(), getAuthUsername()));
        formparams.add(new BasicNameValuePair(
                getAuthPasswordField(), getAuthPassword()));

        for (Entry<String, String> en : authFormParams.entrySet()) {
            formparams.add(new BasicNameValuePair(
                  en.getKey(), en.getValue()));
        }
        
        LOG.info("Performing FORM authentication at \"" + getAuthURL()
                + "\" (username=" + getAuthUsername() + "; password=*****)");
        try {
            UrlEncodedFormEntity entity = 
                    new UrlEncodedFormEntity(formparams, authFormCharset);
            post.setEntity(entity);
            HttpResponse response = httpClient.execute(post);
            StatusLine statusLine = response.getStatusLine();
            LOG.info("Authentication status: " + statusLine);
            
            if (LOG.isDebugEnabled()) {
                LOG.debug("Authentication response:\n" + IOUtils.toString(
                        response.getEntity().getContent(), StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            throw new CollectorException(e);
        }
        post.releaseConnection();
        
    }

    /**
     * Creates the default cookie store to be added to each request context.
     * @return a cookie store
     * @since 2.4.0
     */
    protected CookieStore createDefaultCookieStore() {
        return new BasicCookieStore();
    }
    
    /**
     * <p>
     * Creates a list of HTTP headers previously set by 
     * {@link #setRequestHeader(String, String)}.
     * </p>
     * <p>
     * <b>Since 2.8.0</b>, this method will also add a "Basic" authentication
     * header if {@link #setAuthPreemptive(boolean)} is <code>true</code> and
     * credentials were supplied.
     * </p>
     * @return a list of HTTP request headers
     * @since 2.3.0
     */
    protected List<Header> createDefaultRequestHeaders() {
        //--- Configuration-defined headers
        List<Header> headers = new ArrayList<>();
        for (Entry<String, String> entry : requestHeaders.entrySet()) {
            headers.add(new BasicHeader(entry.getKey(), entry.getValue()));
        }
        
        //--- preemptive headers
        // preemptive authaurisation could be done by creating a HttpContext
        // passed to the HttpClient execute method, but since that method
        // is not invoked from this class, we want to keep things
        // together and we add the preemptive authentication directly
        // in the default HTTP headers. 
        if (authPreemptive) {
            if (StringUtils.isBlank(authUsername)) {
                LOG.warn("Preemptive authentication is enabled while no "
                        + "username was provided.");
                return headers;
            }
            if (!AUTH_METHOD_BASIC.equalsIgnoreCase(authMethod)) {
                LOG.warn("Using preemptive authentication with a "
                        + "method other than \"Basic\" may not produce the "
                        + "expected outcome.");
            }
            String password = EncryptionUtil.decrypt(
                    authPassword, authPasswordKey);
            String auth = getAuthUsername() + ":" + password;
            byte[] encodedAuth = Base64.encodeBase64(
              auth.getBytes(Charset.forName("ISO-8859-1")));
            String authHeader = "Basic " + new String(encodedAuth);
            headers.add(new BasicHeader(HttpHeaders.AUTHORIZATION, authHeader));
        }
        return headers;
    }
    
    protected RedirectStrategy createRedirectStrategy() {
        return LaxRedirectStrategy.INSTANCE;
    }
    
    protected SchemePortResolver createSchemePortResolver() {
        return SCHEME_PORT_RESOLVER;
    }
    protected RequestConfig createRequestConfig() {
        RequestConfig.Builder builder = RequestConfig.custom()
                .setConnectTimeout(connectionTimeout)
                .setSocketTimeout(socketTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setMaxRedirects(maxRedirects)
                .setExpectContinueEnabled(expectContinueEnabled)
                .setCookieSpec(cookieSpec);
        if (maxRedirects <= 0) {
            builder.setRedirectsEnabled(false);
        }
        if (StringUtils.isNotBlank(localAddress)) {
            try {
                builder.setLocalAddress(InetAddress.getByName(localAddress));
            } catch (UnknownHostException e) {
                throw new CollectorException(
                        "Invalid local address: " + localAddress, e);
            }
        }
        return builder.build();
    }
    protected HttpHost createProxy() {
        if (StringUtils.isNotBlank(proxyHost)) {
            return new HttpHost(proxyHost, proxyPort, proxyScheme);
        }
        return null;
    }
    protected CredentialsProvider createCredentialsProvider() {
        CredentialsProvider credsProvider = null;
        //--- Proxy ---
        if (StringUtils.isNotBlank(proxyUsername)) {
            String password = 
                    EncryptionUtil.decrypt(proxyPassword, proxyPasswordKey);
            credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(
                    new AuthScope(proxyHost, proxyPort, proxyRealm),
                    new UsernamePasswordCredentials(
                            proxyUsername, password));
        }
        //--- Auth ---
        if (StringUtils.isNotBlank(authUsername)
                && !AUTH_METHOD_FORM.equalsIgnoreCase(authMethod)) {
            if (credsProvider == null) {
                credsProvider = new BasicCredentialsProvider();
            }
            Credentials creds = null;
            String password = 
                    EncryptionUtil.decrypt(authPassword, authPasswordKey);
            if (AUTH_METHOD_NTLM.equalsIgnoreCase(authMethod)) {
                creds = new NTCredentials(authUsername, password, 
                        authWorkstation, authDomain);
            } else {
                creds = new UsernamePasswordCredentials(
                        authUsername, password);
            }
            credsProvider.setCredentials(new AuthScope(
                    authHostname, authPort, authRealm, authMethod), creds);
        }
        return credsProvider;
    }
    protected ConnectionConfig createConnectionConfig() {
        if (StringUtils.isNotBlank(proxyUsername)) {
            return ConnectionConfig.custom()
                    .setCharset(Consts.UTF_8)
                    .build(); 
        }
        return null;
    }
    
    protected LayeredConnectionSocketFactory createSSLSocketFactory(
            SSLContext sslContext) {
        if (!trustAllSSLCertificates && ArrayUtils.isEmpty(sslProtocols)) {
            return null;
        }

        SSLContext context = sslContext;
        if (context == null) {
            try {
                context = SSLContexts.custom().build();
            } catch (KeyManagementException | NoSuchAlgorithmException e) {
                throw new CollectorException(
                        "Cannot create SSL context.", e);
            }
        }
        
        // Turn off host name verification and remove all algorithm constraints.
        return new SSLConnectionSocketFactory(
                        context, new NoopHostnameVerifier()) {
            @Override
            protected void prepareSocket(SSLSocket socket)
                    throws IOException {
                SSLParameters sslParams = new SSLParameters();
                
                // Trust all certificates
                if (trustAllSSLCertificates) {
                    LOG.debug("SSL: Turning off host name verification.");
                    sslParams.setAlgorithmConstraints(
                            new AlgorithmConstraints() {
                        @Override
                        public boolean permits(
                                Set<CryptoPrimitive> primitives, Key key) {
                            return true;
                        }
                        @Override
                        public boolean permits(Set<CryptoPrimitive> primitives,
                                String algorithm, 
                                AlgorithmParameters parameters) {
                            return true;
                        }
                        @Override
                        public boolean permits(
                                Set<CryptoPrimitive> primitives,
                                String algorithm, Key key,
                                AlgorithmParameters parameters) {
                            return true;
                        }
                    });
                }
                
                // Specify protocols
                if (ArrayUtils.isNotEmpty(sslProtocols)) {
                    LOG.debug("SSL: Protocols=" 
                            + StringUtils.join(sslProtocols, ","));
                    sslParams.setProtocols(sslProtocols);
                }
                
                sslParams.setEndpointIdentificationAlgorithm("HTTPS");

                if (disableSNI) {
                    // Disabling SNI extension introduced in Java 7 is necessary
                    // to avoid SSLProtocolException: handshake alert:  unrecognized_name
                    // for some sites with wrong Virtual Host - config.
                    // Described here:
                    // http://bugs.java.com/bugdatabase/view_bug.do?bug_id=7127374
                    // Instead of using the SystemProperty to disable SNI, follow the approach from here
                    // https://github.com/lightbody/browsermob-proxy/issues/117#issuecomment-141363454
                    // and disable SNI for this SSLConnectionSocketFactory only
                    LOG.debug("SSL: Disabling SNI Extension for this httpClientFactory.");
                    sslParams.setServerNames(Collections.emptyList());
                }

                socket.setSSLParameters(sslParams);
            }
        };
    }    

    protected SSLContext createSSLContext() {
        if (!trustAllSSLCertificates) {
            return null;
        }
        LOG.info("SSL: Trusting all certificates.");
        
        // Use a trust strategy that always returns true
        SSLContext sslcontext;
        try {
            sslcontext = SSLContexts.custom().build();
            sslcontext.init(null, new TrustManager[] {
                    new TrustAllX509TrustManager()}, new SecureRandom());
        } catch (Exception e) {
            throw new CollectorException(
                    "Cannot create SSL context trusting all certificates.", e);
        }
        return sslcontext;
    }
    
    @Override
    public void loadFromXML(Reader in) {
        XMLConfiguration xml = XMLConfigurationUtil.newXMLConfiguration(in);
        if (xml.getBoolean("cookiesDisabled", false)) {
            cookieSpec = CookieSpecs.IGNORE_COOKIES;
        } else {
            cookieSpec = xml.getString("cookieSpec", cookieSpec);
        }
        authMethod = xml.getString("authMethod", authMethod);
        authUsernameField = 
                xml.getString("authUsernameField", authUsernameField);
        authUsername = xml.getString("authUsername", authUsername);
        authPasswordField = 
                xml.getString("authPasswordField", authPasswordField);
        authPassword = xml.getString("authPassword", authPassword);
        authPasswordKey = 
                loadXMLPasswordKey(xml, "authPasswordKey", authPasswordKey);
        authURL = xml.getString("authURL", authURL);
        authHostname = xml.getString("authHostname", authHostname);
        authPort = xml.getInt("authPort", authPort);
        authRealm = xml.getString("authRealm", authRealm);
        authFormCharset = xml.getString("authFormCharset", authFormCharset);
        authWorkstation = xml.getString("authWorkstation", authWorkstation);
        authDomain = xml.getString("authDomain", authDomain);
        authPreemptive = xml.getBoolean("authPreemptive", authPreemptive);
        proxyHost = xml.getString("proxyHost", proxyHost);
        proxyPort = xml.getInt("proxyPort", proxyPort);
        proxyScheme = xml.getString("proxyScheme", proxyScheme);
        proxyUsername = xml.getString("proxyUsername", proxyUsername);
        proxyPassword = xml.getString("proxyPassword", proxyPassword);
        proxyPasswordKey = 
                loadXMLPasswordKey(xml, "proxyPasswordKey", proxyPasswordKey);
        proxyRealm = xml.getString("proxyRealm", proxyRealm);
        connectionTimeout = (int) XMLConfigurationUtil.getDuration(
                xml, "connectionTimeout", connectionTimeout);
        socketTimeout = (int) XMLConfigurationUtil.getDuration(
                xml, "socketTimeout", socketTimeout);
        connectionRequestTimeout = (int) XMLConfigurationUtil.getDuration(
                xml, "connectionRequestTimeout", connectionRequestTimeout);
        connectionCharset = xml.getString(
                "connectionCharset", connectionCharset);
        expectContinueEnabled = xml.getBoolean(
                "expectContinueEnabled", expectContinueEnabled);
        maxRedirects = xml.getInt("maxRedirects", maxRedirects);
        maxConnections = xml.getInt("maxConnections", maxConnections);
        trustAllSSLCertificates = xml.getBoolean(
                "trustAllSSLCertificates", trustAllSSLCertificates);
        disableSNI = xml.getBoolean(
                "disableSNI", disableSNI);;
        localAddress = xml.getString("localAddress", localAddress);
        maxConnectionsPerRoute = xml.getInt(
                "maxConnectionsPerRoute", maxConnectionsPerRoute);
        maxConnectionIdleTime = (int) XMLConfigurationUtil.getDuration(
                xml, "maxConnectionIdleTime", maxConnectionIdleTime);
        maxConnectionInactiveTime = (int) XMLConfigurationUtil.getDuration(
                xml, "maxConnectionInactiveTime", maxConnectionInactiveTime);
        String sslProtocolsCSV = xml.getString("sslProtocols", null);
        if (StringUtils.isNotBlank(sslProtocolsCSV)) {
            setSSLProtocols(sslProtocolsCSV.trim().split("(\\s*,\\s*)+"));
        }
        
        // request headers
        List<HierarchicalConfiguration> xmlHeaders = 
                xml.configurationsAt("headers.header");
        if (!xmlHeaders.isEmpty()) {
            requestHeaders.clear();
            for (HierarchicalConfiguration xmlHeader : xmlHeaders) {
                requestHeaders.put(
                        xmlHeader.getString("[@name]"), 
                        xmlHeader.getString(""));
            }
        }

        // auth form parameters
        List<HierarchicalConfiguration> xmlAuthFormParams = 
                xml.configurationsAt("authFormParams.param");
        if (!xmlAuthFormParams.isEmpty()) {
            authFormParams.clear();
            for (HierarchicalConfiguration xmlParam : xmlAuthFormParams) {
                requestHeaders.put(
                        xmlParam.getString("[@name]"), 
                        xmlParam.getString(""));
            }
        }
        
        if (xml.getString("staleConnectionCheckDisabled") != null) {
            LOG.warn("Since 2.1.0, the configuration option \""
                    + "staleConnectionCheckDisabled\" is no longer supported. "
                    + "Use \"maxConnectionInactiveTime\" instead.");
        }
    }

    private EncryptionKey loadXMLPasswordKey(
            XMLConfiguration xml, String field, EncryptionKey defaultKey) {
        String xmlKey = xml.getString(field, null);
        String xmlSource = xml.getString(field + "Source", null);
        if (StringUtils.isBlank(xmlKey)) {
            return defaultKey;
        }
        EncryptionKey.Source source = null;
        if (StringUtils.isNotBlank(xmlSource)) {
            source = EncryptionKey.Source.valueOf(xmlSource.toUpperCase());
        }
        return new EncryptionKey(xmlKey, source);
    }
    
    @Override
    public void saveToXML(Writer out) throws IOException {
        try {
            EnhancedXMLStreamWriter writer = new EnhancedXMLStreamWriter(out);
            writer.writeStartElement("httpClientFactory");
            writer.writeAttribute("class", getClass().getCanonicalName());

            writer.writeElementString("cookieSpec", cookieSpec);
            writer.writeElementString("authMethod", authMethod);
            writer.writeElementString("authUsername", authUsername);
            writer.writeElementString("authPassword", authPassword);
            saveXMLPasswordKey(writer, "authPasswordKey", authPasswordKey);
            writer.writeElementString("authUsernameField", authUsernameField);
            writer.writeElementString("authPasswordField", authPasswordField);
            writer.writeElementString("authURL", authURL);
            writer.writeElementString("authHostname", authHostname);
            writer.writeElementInteger("authPort", authPort);
            writer.writeElementString("authFormCharset", authFormCharset);
            writer.writeElementString("authWorkstation", authWorkstation);
            writer.writeElementString("authDomain", authDomain);
            writer.writeElementString("authRealm", authRealm);
            writer.writeElementBoolean("authPreemptive", authPreemptive);
            writer.writeElementString("proxyHost", proxyHost);
            writer.writeElementInteger("proxyPort", proxyPort);
            writer.writeElementString("proxyScheme", proxyScheme);
            writer.writeElementString("proxyUsername", proxyUsername);
            writer.writeElementString("proxyPassword", proxyPassword);
            saveXMLPasswordKey(writer, "proxyPasswordKey", proxyPasswordKey);
            writer.writeElementString("proxyRealm", proxyRealm);
            writer.writeElementInteger("connectionTimeout", connectionTimeout);
            writer.writeElementInteger("socketTimeout", socketTimeout);
            writer.writeElementInteger("connectionRequestTimeout",
                    connectionRequestTimeout);
            writer.writeElementString("connectionCharset", connectionCharset);
            writer.writeElementBoolean(
                    "expectContinueEnabled", expectContinueEnabled);
            writer.writeElementInteger("maxRedirects", maxRedirects);
            writer.writeElementString("localAddress", localAddress);
            writer.writeElementInteger("maxConnections", maxConnections);
            writer.writeElementBoolean(
                    "trustAllSSLCertificates", trustAllSSLCertificates);
            writer.writeElementBoolean(
                    "disableSNI", disableSNI);
            writer.writeElementInteger(
                    "maxConnectionsPerRoute", maxConnectionsPerRoute);
            writer.writeElementInteger(
                    "maxConnectionIdleTime", maxConnectionIdleTime);
            writer.writeElementInteger(
                    "maxConnectionInactiveTime", maxConnectionInactiveTime);
            if (ArrayUtils.isNotEmpty(sslProtocols)) {
                writer.writeElementString(
                        "sslProtocols", StringUtils.join(sslProtocols, ","));
            }
            if (!requestHeaders.isEmpty()) {
                writer.writeStartElement("headers");
                for (Entry<String, String> entry : requestHeaders.entrySet()) {
                    writer.writeStartElement("header");
                    writer.writeAttributeString("name", entry.getKey());
                    writer.writeCharacters(entry.getValue());
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
            if (!authFormParams.isEmpty()) {
                writer.writeStartElement("authFormParams");
                for (Entry<String, String> entry : authFormParams.entrySet()) {
                    writer.writeStartElement("param");
                    writer.writeAttributeString("name", entry.getKey());
                    writer.writeCharacters(entry.getValue());
                    writer.writeEndElement();
                }
                writer.writeEndElement();
            }
            
            writer.writeEndElement();
            
            writer.flush();
            writer.close();
        } catch (XMLStreamException e) {
            throw new IOException("Cannot save as XML.", e);
        }        
    }
    private void saveXMLPasswordKey(EnhancedXMLStreamWriter writer, 
            String field, EncryptionKey key) throws XMLStreamException {
        if (key == null) {
            return;
        }
        writer.writeElementString(field, key.getValue());
        if (key.getSource() != null) {
            writer.writeElementString(
                    field + "Source", key.getSource().name().toLowerCase());
        }
    }

    //--- Getters/Setters ------------------------------------------------------
    
    /**
     * Sets a default HTTP request header every HTTP connection should have.
     * Those are in addition to any default request headers Apache HttpClient 
     * may already provide.
     * @param name HTTP request header name
     * @param value HTTP request header value
     * @since 2.3.0
     */
    public void setRequestHeader(String name, String value) {
        requestHeaders.put(name, value);
    }
    /**
     * Gets the HTTP request header value matching the given name, previously
     * set with {@link #setRequestHeader(String, String)}.
     * @param name HTTP request header name
     * @return HTTP request header value or <code>null</code> if 
     *         no match is found
     * @since 2.3.0
     */
    public String getRequestHeader(String name) {
        return requestHeaders.get(name);
    }
    /**
     * Gets all HTTP request header names for headers previously set
     * with {@link #setRequestHeader(String, String)}. If no request headers
     * are set, it returns an empty array.
     * @return HTTP request header names
     * @since 2.3.0
     * @deprecated Since 2.8.0 use {@link #getRequestHeaderNames()}
     */
    @Deprecated
    public String[] getRequestHeaders() {
        return getRequestHeaderNames();
    }
    /**
     * Gets all HTTP request header names for headers previously set
     * with {@link #setRequestHeader(String, String)}. If no request headers
     * are set, it returns an empty array.
     * @return HTTP request header names
     * @since 2.8.0
     */
    public String[] getRequestHeaderNames() {
        return requestHeaders.keySet().toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }
    /**
     * Remove the request header matching the given name.
     * @param name name of HTTP request header to remove
     * @return the previous value associated with the name, or <code>null</code>
     *         if there was no request header for the name. 
     * @since 2.8.0
     */
    public String removeRequestHeader(String name) {
        return requestHeaders.remove(name);
    }
    
    /**
     * Gets the authentication method.
     * @return authentication method
     */
    public String getAuthMethod() {
        return authMethod;
    }
    /**
     * Sets the authentication method.
     * <br><br>
     * Valid values are (case insensitive):
     * <ul>
     *   <li>form</li>
     *   <li>basic</li>
     *   <li>digest</li>
     *   <li>ntlm</li>
     * </ul> 
     * Experimental (not fully tested, please report):
     * <ul>
     *   <li>spnego</li>
     *   <li>kerberos</li>
     * </ul>
     * @param authMethod authentication method
     */
    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }

    /**
     * Gets the name of the HTML field where the username is set.
     * This is used only for "form" authentication.
     * @return username name of the HTML field
     */
    public String getAuthUsernameField() {
        return authUsernameField;
    }
    /**
     * Sets the name of the HTML field where the username is set.
     * This is used only for "form" authentication.
     * @param authUsernameField name of the HTML field
     */
    public void setAuthUsernameField(String authUsernameField) {
        this.authUsernameField = authUsernameField;
    }

    /**
     * Gets the username.
     * Used for all authentication methods.
     * @return username
     */
    public String getAuthUsername() {
        return authUsername;
    }
    /**
     * Sets the username.
     * Used for all authentication methods.
     * @param authUsername username
     */
    public void setAuthUsername(String authUsername) {
        this.authUsername = authUsername;
    }

    /**
     * Gets the name of the HTML field where the password is set.
     * This is used only for "form" authentication.
     * @return name of the HTML field
     */
    public String getAuthPasswordField() {
        return authPasswordField;
    }
    /**
     * Sets the name of the HTML field where the password is set.
     * This is used only for "form" authentication.
     * @param authPasswordField name of the HTML field
     */
    public void setAuthPasswordField(String authPasswordField) {
        this.authPasswordField = authPasswordField;
    }

    /**
     * Gets the authentication password.
     * Used for all authentication methods.
     * @return the password
     */
    public String getAuthPassword() {
        return authPassword;
    }
    /**
     * Sets the authentication password.
     * Used for all authentication methods.
     * @param authPassword password
     */
    public void setAuthPassword(String authPassword) {
        this.authPassword = authPassword;
    }

    /**
     * Gets the authentication password encryption key.
     * @return the password key or <code>null</code> if the password is not
     * encrypted.
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public EncryptionKey getAuthPasswordKey() {
        return authPasswordKey;
    }
    /**
     * Sets the authentication password encryption key. Only required when 
     * the password is encrypted.
     * @param authPasswordKey password key
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public void setAuthPasswordKey(EncryptionKey authPasswordKey) {
        this.authPasswordKey = authPasswordKey;
    }
    
    /**
     * Whether cookie support is disabled.
     * @return <code>true</code> if disabled
     */
    public boolean isCookiesDisabled() {
        return CookieSpecs.IGNORE_COOKIES.equals(cookieSpec);
    }
    /**
     * Sets whether cookie support is disabled.
     * @param cookiesDisabled <code>true</code> if disabled
     */
    public void setCookiesDisabled(boolean cookiesDisabled) {
        this.cookieSpec = CookieSpecs.IGNORE_COOKIES;
    }
    
    /**
     * @return the cookieSpec to use as defined in {@link CookieSpecs}
     */
    public String getCookieSpec() {
        return cookieSpec;
    }
    /**
     * @param cookieSpec the cookieSpec to use as defined in {@link CookieSpecs}
     */
    public void setCookieSpec(String cookieSpec) {
        this.cookieSpec = cookieSpec;
    }

    /**
     * Gets the URL for "form" authentication.
     * The username and password will be POSTed to this URL.
     * This is used only for "form" authentication.
     * @return "form" authentication URL
     */
    public String getAuthURL() {
        return authURL;
    }
    /**
     * Sets the URL for "form" authentication.
     * The username and password will be POSTed to this URL.
     * This is used only for "form" authentication.
     * @param authURL "form" authentication URL
     */
    public void setAuthURL(String authURL) {
        this.authURL = authURL;
    }
    
    /**
     * Gets the host name for the current authentication scope.
     * <code>null</code> means any host names for the scope.
     * Used for BASIC and DIGEST authentication.
     * @return hostname for the scope
     */
    public String getAuthHostname() {
        return authHostname;
    }
    /**
     * Sets the host name for the current authentication scope.
     * Setting this to null (default value) indicates "any hostname" for the 
     * scope.
     * Used for BASIC and DIGEST authentication.
     * @param authHostname hostname for the scope
     */
    public void setAuthHostname(String authHostname) {
        this.authHostname = authHostname;
    }

    /**
     * Gets the port for the current authentication scope.
     * A negative number indicates "any port" 
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @return port for the scope
     */
    public int getAuthPort() {
        return authPort;
    }
    /**
     * Sets the port for the current authentication scope.
     * Setting this to a negative number (default value) indicates "any port" 
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @param authPort port for the scope
     */
    public void setAuthPort(int authPort) {
        this.authPort = authPort;
    }

    /**
     * Gets the realm name for the current authentication scope.
     * <code>null</code> indicates "any realm" 
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @return realm name for the scope
     */
    public String getAuthRealm() {
        return authRealm;
    }
    /**
     * Sets the realm name for the current authentication scope.
     * Setting this to null (the default value) indicates "any realm" 
     * for the scope.
     * Used for BASIC and DIGEST authentication.
     * @param authRealm reaml name for the scope
     */
    public void setAuthRealm(String authRealm) {
        this.authRealm = authRealm;
    }

    /**
     * Gets the authentication form character set.
     * @return authentication form character set
     */
    public String getAuthFormCharset() {
        return authFormCharset;
    }
    /**
     * Sets the authentication form character set for the form field values.
     * Default is UTF-8.
     * @param authFormCharset authentication form character set 
     */
    public void setAuthFormCharset(String authFormCharset) {
        this.authFormCharset = authFormCharset;
    }

    /**
     * Whether to trust all SSL certificates (affects only "https" connections).
     * @since 1.3.0
     * @return <code>true</code> if trusting all SSL certificates
     */
    public boolean isTrustAllSSLCertificates() {
        return trustAllSSLCertificates;
    }
    /**
     * Sets whether to trust all SSL certificate.  This is typically a bad
     * idea (favors man-in-the-middle attacks) . Try to install a SSL 
     * certificate locally to ensure a proper certificate exchange instead.
     * @since 1.3.0
     * @param trustAllSSLCertificates <code>true</code> if trusting all SSL 
     *            certificates
     */
    public void setTrustAllSSLCertificates(boolean trustAllSSLCertificates) {
        this.trustAllSSLCertificates = trustAllSSLCertificates;
    }

    /**
     * Whether to disable SNI (affects only "https" connections).
     * @since 2.8.2
     * @return <code>true</code> if SNI is disabled
     */
    public boolean isDisableSNI() {
        return trustAllSSLCertificates;
    }
    /**
     * Sets whether to disable SNI.
     * This may be needed to connect to SSL hosts with a bad virtual host configuration, that do not understand
     * server names passed in the SSL protocol (in difference to only pass these in the HTTP headers only).
     * The default is <code>false</code>
     * @since 2.8.2
     * @param disableSNI <code>true</code> if SNI is disabled
     */
    public void setDisableSNI(boolean disableSNI) {
        this.disableSNI = disableSNI;
    }

    /**
     * Gets the proxy host.
     * @return proxy host
     */
    public String getProxyHost() {
        return proxyHost;
    }
    /**
     * Sets the proxy host.
     * @param proxyHost proxy host
     */
    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    /**
     * Gets the proxy port.
     * @return proxy port
     */
    public int getProxyPort() {
        return proxyPort;
    }
    /**
     * Sets the proxy port.
     * @param proxyPort proxy port
     */
    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
    }

    /**
     * Gets the proxy scheme.
     * @return proxy scheme
     */
    public String getProxyScheme() {
        return proxyScheme;
    }
    /**
     * Sets the proxy scheme.
     * @param proxyScheme proxy scheme
     */
    public void setProxyScheme(String proxyScheme) {
        this.proxyScheme = proxyScheme;
    }

    /**
     * Gets the proxy username.
     * @return proxy username
     */
    public String getProxyUsername() {
        return proxyUsername;
    }
    /**
     * Sets the proxy username
     * @param proxyUsername proxy username
     */
    public void setProxyUsername(String proxyUsername) {
        this.proxyUsername = proxyUsername;
    }

    /**
     * Gets the proxy password.
     * @return proxy password
     */
    public String getProxyPassword() {
        return proxyPassword;
    }
    /**
     * Sets the proxy password.
     * @param proxyPassword proxy password
     */
    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }
    
    /**
     * Gets the proxy password encryption key.
     * @return the password key or <code>null</code> if the password is not
     * encrypted.
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public EncryptionKey getProxyPasswordKey() {
        return proxyPasswordKey;
    }
    /**
     * Sets the proxy password encryption key. Only required when 
     * the password is encrypted.
     * @param proxyPasswordKey password key
     * @see EncryptionUtil
     * @since 2.4.0
     */
    public void setProxyPasswordKey(EncryptionKey proxyPasswordKey) {
        this.proxyPasswordKey = proxyPasswordKey;
    }

    /**
     * Gets the proxy realm.
     * @return proxy realm
     */
    public String getProxyRealm() {
        return proxyRealm;
    }
    /**
     * Sets the proxy realm
     * @param proxyRealm proxy realm
     */
    public void setProxyRealm(String proxyRealm) {
        this.proxyRealm = proxyRealm;
    }

    /**
     * Gets the connection timeout until a connection is established,
     * in milliseconds.
     * @return connection timeout
     */
    public int getConnectionTimeout() {
        return connectionTimeout;
    }
    /**
     * Sets the connection timeout until a connection is established,
     * in milliseconds. Default is {@link #DEFAULT_TIMEOUT}.
     * @param connectionTimeout connection timeout
     */
    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
    }

    /**
     * Gets the maximum period of inactivity between two consecutive data 
     * packets, in milliseconds. 
     * @return connection timeout
     */
    public int getSocketTimeout() {
        return socketTimeout;
    }
    /**
     * Sets the maximum period of inactivity between two consecutive data 
     * packets, in milliseconds. Default is {@link #DEFAULT_TIMEOUT}.
     * @param socketTimeout socket timeout
     */
    public void setSocketTimeout(int socketTimeout) {
        this.socketTimeout = socketTimeout;
    }

    /**
     * Gets the timeout when requesting a connection, in milliseconds
     * @return connection timeout
     */
    public int getConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }
    /**
     * Sets the timeout when requesting a connection, in milliseconds.
     * Default is {@link #DEFAULT_TIMEOUT}.
     * @param connectionRequestTimeout connection request timeout
     */
    public void setConnectionRequestTimeout(int connectionRequestTimeout) {
        this.connectionRequestTimeout = connectionRequestTimeout;
    }

    /**
     * Gets the connection character set.
     * @return connection character set
     */
    public String getConnectionCharset() {
        return connectionCharset;
    }
    /**
     * Sets the connection character set.  The HTTP protocol specification
     * mandates the use of ASCII for HTTP message headers.  Sites do not always
     * respect this and it may be necessary to force a non-standard character 
     * set.
     * @param connectionCharset connection character set
     */
    public void setConnectionCharset(String connectionCharset) {
        this.connectionCharset = connectionCharset;
    }

    /**
     * Whether 'Expect: 100-continue' handshake is enabled.
     * @return <code>true</code> if enabled
     */
    public boolean isExpectContinueEnabled() {
        return expectContinueEnabled;
    }
    /**
     * Sets whether 'Expect: 100-continue' handshake is enabled.
     * See {@link RequestConfig#isExpectContinueEnabled()}
     * @param expectContinueEnabled <code>true</code> if enabled
     */
    public void setExpectContinueEnabled(boolean expectContinueEnabled) {
        this.expectContinueEnabled = expectContinueEnabled;
    }

    /**
     * Gets the maximum number of redirects to be followed.
     * @return maximum number of redirects to be followed
     */
    public int getMaxRedirects() {
        return maxRedirects;
    }
    /**
     * Sets the maximum number of redirects to be followed.  This can help
     * prevent infinite loops.  A value of zero effectively disables
     * redirects.  Default is {@link #DEFAULT_MAX_REDIRECT}.
     * @param maxRedirects maximum number of redirects to be followed
     */
    public void setMaxRedirects(int maxRedirects) {
        this.maxRedirects = maxRedirects;
    }

    /**
     * Gets the local address (IP or hostname).
     * @return local address
     */
    public String getLocalAddress() {
        return localAddress;
    }
    /**
     * Sets the local address, which may be useful when working with multiple
     * network interfaces.
     * @param localAddress locale address
     */
    public void setLocalAddress(String localAddress) {
        this.localAddress = localAddress;
    }

    /**
     * Gets whether stale connection check is disabled.
     * @return <code>true</code> if stale connection check is disabled
     * @deprecated Since 2.1.0.
     * As of 2.2.0, use {@link #getMaxConnectionInactiveTime()} instead. 
     */
    @Deprecated
    public boolean isStaleConnectionCheckDisabled() {
        return false;
    }
    /**
     * Sets whether stale connection check is disabled.  Disabling stale
     * connection check can slightly improve performance.
     * @param staleConnectionCheckDisabled <code>true</code> if stale 
     *        connection check is disabled
     * @deprecated Since 2.1.0.
     * As of 2.2.0, use {@link #setMaxConnectionInactiveTime(int)} instead. 
     */
    @Deprecated
    public void setStaleConnectionCheckDisabled(
            boolean staleConnectionCheckDisabled) {
        // do nothing
    }

    /**
     * Gets the NTLM authentication workstation name.
     * @return workstation name
     */
    public String getAuthWorkstation() {
        return authWorkstation;
    }
    /**
     * Sets the NTLM authentication workstation name.
     * @param authWorkstation workstation name
     */
    public void setAuthWorkstation(String authWorkstation) {
        this.authWorkstation = authWorkstation;
    }

    /**
     * Gets the NTLM authentication domain.
     * @return authentication domain
     */
    public String getAuthDomain() {
        return authDomain;
    }
    /**
     * Sets the NTLM authentication domain
     * @param authDomain authentication domain
     */
    public void setAuthDomain(String authDomain) {
        this.authDomain = authDomain;
    }

    /**
     * Gets the maximum number of connections that can be created.
     * @return number of connections
     */
    public int getMaxConnections() {
        return maxConnections;
    }
    /**
     * Sets maximum number of connections that can be created.  Typically,
     * you would have at least the same amount as threads.
     * Default is {@link #DEFAULT_MAX_CONNECTIONS}.
     * @param maxConnections maximum number of connections
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /**
     * Gets the maximum number of connections that can be used per route.
     * @return number of connections per route
     * @since 2.2.0
     */
    public int getMaxConnectionsPerRoute() {
        return maxConnectionsPerRoute;
    }
    /**
     * Sets the maximum number of connections that can be used per route.
     * Default is {@link #DEFAULT_MAX_CONNECTIONS_PER_ROUTE}.
     * @param maxConnectionsPerRoute maximum number of connections per route
     * @since 2.2.0
     */
    public void setMaxConnectionsPerRoute(int maxConnectionsPerRoute) {
        this.maxConnectionsPerRoute = maxConnectionsPerRoute;
    }

    /**
     * Gets the period of time in milliseconds after which to evict idle 
     * connections from the connection pool.
     * @return amount of time after which to evict idle connections
     * @since 2.2.0
     */
    public int getMaxConnectionIdleTime() {
        return maxConnectionIdleTime;
    }
    /**
     * Sets the period of time in milliseconds after which to evict idle 
     * connections from the connection pool. 
     * Default is {@link #DEFAULT_MAX_IDLE_TIME}.
     * @param maxConnectionIdleTime amount of time after which to evict idle 
     *         connections
     * @since 2.2.0
     */
    public void setMaxConnectionIdleTime(int maxConnectionIdleTime) {
        this.maxConnectionIdleTime = maxConnectionIdleTime;
    }

    /**
     * Gets the period of time in milliseconds a connection must be inactive
     * to be checked in case it became stalled.
     * @return period of time in milliseconds
     * @since 2.2.0
     */
    public int getMaxConnectionInactiveTime() {
        return maxConnectionInactiveTime;
    }
    /**
     * Sets the period of time in milliseconds a connection must be inactive
     * to be checked in case it became stalled. Default is 0 (not proactively
     * checked).
     * @param maxConnectionInactiveTime period of time in milliseconds
     * @since 2.2.0
     */
    public void setMaxConnectionInactiveTime(int maxConnectionInactiveTime) {
        this.maxConnectionInactiveTime = maxConnectionInactiveTime;
    }

    /**
     * Gets the supported SSL/TLS protocols.  Default is <code>null</code>,
     * which means it will use those provided/configured by your Java 
     * platform. 
     * @return SSL/TLS protocols
     * @since 2.6.2
     */
    public String[] getSSLProtocols() {
        return sslProtocols;
    }
    /**
     * Sets the supported SSL/TLS protocols, such as SSLv3, TLSv1, TLSv1.1, 
     * and TLSv1.2.  Note that specifying a protocol not supported by 
     * your underlying Java platform will not work. 
     * @param sslProtocols SSL/TLS protocols supported
     * @since 2.6.2
     */
    public void setSSLProtocols(String... sslProtocols) {
        this.sslProtocols = sslProtocols;
    }

    /**
     * Sets an authentication form parameter (equivalent to "input" or other
     * fields in HTML forms).
     * @param name form parameter name
     * @param value form parameter value
     * @since 2.8.0
     */
    public void setAuthFormParam(String name, String value) {
        authFormParams.put(name, value);
    }
    /**
     * Gets an authentication form parameter (equivalent to "input" or other
     * fields in HTML forms).
     * @param name form parameter name
     * @return form parameter value or <code>null</code> if 
     *         no match is found
     * @since 2.8.0
     */
    public String getAuthFormParam(String name) {
        return authFormParams.get(name);
    }
    /**
     * Gets all authentication form parameter names. If no form parameters
     * are set, it returns an empty array.
     * @return HTTP request header names
     * @since 2.8.0
     */
    public String[] getAuthFormParamNames() {
        return authFormParams.keySet().toArray(ArrayUtils.EMPTY_STRING_ARRAY);
    }
    /**
     * Remove the authentication form parameter matching the given name.
     * @param name name of form parameter to remove
     * @return the previous value associated with the name, or <code>null</code>
     *         if there was no form parameter for the name. 
     * @since 2.8.0
     */
    public String removeAuthFormParameter(String name) {
        return authFormParams.remove(name);
    }

    /**
     * Gets whether to perform preemptive authentication 
     * (valid for "basic" authentication method).
     * @return <code>true</code> to perform preemptive authentication
     * @since 2.8.0
     */
    public boolean isAuthPreemptive() {
        return authPreemptive;
    }
    /**
     * Sets whether to perform preemptive authentication 
     * (valid for "basic" authentication method).
     * @param authPreemptive 
     *            <code>true</code> to perform preemptive authentication
     * @since 2.8.0
     */
    public void setAuthPreemptive(boolean authPreemptive) {
        this.authPreemptive = authPreemptive;
    }

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof GenericHttpClientFactory)) {
            return false;
        }
        GenericHttpClientFactory other = (GenericHttpClientFactory) obj;
        return new EqualsBuilder()
                .append(authMethod, other.authMethod)
                .append(authURL, other.authURL)
                .append(authUsernameField, other.authUsernameField)
                .append(authUsername, other.authUsername)
                .append(authPasswordField, other.authPasswordField)
                .append(authPassword, other.authPassword)
                .append(authPasswordKey, other.authPasswordKey)
                .append(authHostname, other.authHostname)
                .append(authPort, other.authPort)
                .append(authRealm, other.authRealm)
                .append(authFormCharset, other.authFormCharset)
                .append(authWorkstation, other.authWorkstation)
                .append(authDomain, other.authDomain)
                .append(authPreemptive, other.authPreemptive)
                .append(cookieSpec, other.cookieSpec)
                .append(trustAllSSLCertificates, other.trustAllSSLCertificates)
                .append(disableSNI, other.disableSNI)
                .append(proxyHost, other.proxyHost)
                .append(proxyPort, other.proxyPort)
                .append(proxyScheme, other.proxyScheme)
                .append(proxyUsername, other.proxyUsername)
                .append(proxyPassword, other.proxyPassword)
                .append(proxyPasswordKey, other.proxyPasswordKey)
                .append(proxyRealm, other.proxyRealm)
                .append(connectionTimeout, other.connectionTimeout)
                .append(socketTimeout, other.socketTimeout)
                .append(connectionRequestTimeout, 
                        other.connectionRequestTimeout)
                .append(connectionCharset, other.connectionCharset)
                .append(localAddress, other.localAddress)
                .append(expectContinueEnabled, other.expectContinueEnabled)
                .append(maxRedirects, other.maxRedirects)
                .append(maxConnections, other.maxConnections)
                .append(maxConnectionsPerRoute, other.maxConnectionsPerRoute)
                .append(maxConnectionIdleTime, other.maxConnectionIdleTime)
                .append(maxConnectionInactiveTime, 
                        other.maxConnectionInactiveTime)
                .append(sslProtocols, other.sslProtocols)
                .isEquals()
                && EqualsUtil.equalsMap(requestHeaders, other.requestHeaders)
                && EqualsUtil.equalsMap(authFormParams, other.authFormParams);
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder()
                .append(authMethod)
                .append(authURL)
                .append(authUsernameField)
                .append(authUsername)
                .append(authPasswordField)
                .append(authPassword)
                .append(authPasswordKey)
                .append(authHostname)
                .append(authPort)
                .append(authRealm)
                .append(authFormCharset)
                .append(authWorkstation)
                .append(authDomain)
                .append(authPreemptive)
                .append(cookieSpec)
                .append(trustAllSSLCertificates)
                .append(disableSNI)
                .append(proxyHost)
                .append(proxyPort)
                .append(proxyScheme)
                .append(proxyUsername)
                .append(proxyPassword)
                .append(proxyPasswordKey)
                .append(proxyRealm)
                .append(connectionTimeout)
                .append(socketTimeout)
                .append(connectionRequestTimeout)
                .append(connectionCharset)
                .append(localAddress)
                .append(expectContinueEnabled)
                .append(maxRedirects)
                .append(maxConnections)
                .append(maxConnectionsPerRoute)
                .append(maxConnectionIdleTime)
                .append(maxConnectionInactiveTime)
                .append(sslProtocols)
                .append(requestHeaders)
                .append(authFormParams)
                .toHashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("authMethod", authMethod)
                .append("authURL", authURL)
                .append("authUsernameField", authUsernameField)
                .append("authUsername", authUsername)
                .append("authPasswordField", authPasswordField)
                .append("authPassword", authPassword)
                .append("authPasswordKey", authPasswordKey)
                .append("authHostname", authHostname)
                .append("authPort", authPort)
                .append("authRealm", authRealm)
                .append("authFormCharset", authFormCharset)
                .append("authWorkstation", authWorkstation)
                .append("authDomain", authDomain)
                .append("authPreemptive", authPreemptive)
                .append("cookieSpec", cookieSpec)
                .append("trustAllSSLCertificates", trustAllSSLCertificates)
                .append("disableSNI", disableSNI)
                .append("proxyHost", proxyHost)
                .append("proxyPort", proxyPort)
                .append("proxyScheme", proxyScheme)
                .append("proxyUsername", proxyUsername)
                .append("proxyPassword", proxyPassword)
                .append("proxyPasswordKey", proxyPasswordKey)
                .append("proxyRealm", proxyRealm)
                .append("connectionTimeout", connectionTimeout)
                .append("socketTimeout", socketTimeout)
                .append("connectionRequestTimeout", connectionRequestTimeout)
                .append("connectionCharset", connectionCharset)
                .append("localAddress", localAddress)
                .append("expectContinueEnabled", expectContinueEnabled)
                .append("maxRedirects", maxRedirects)
                .append("maxConnections", maxConnections)
                .append("maxConnectionsPerRoute", maxConnectionsPerRoute)
                .append("maxConnectionIdleTime", maxConnectionIdleTime)
                .append("maxConnectionInactiveTime", maxConnectionInactiveTime)
                .append("sslProtocols", sslProtocols)
                .append("requestHeaders", requestHeaders)
                .append("authFormParams", authFormParams)
                .toString();
    }
}
