/**
 * Copyright (c) Codice Foundation
 *
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 *
 **/
package org.codice.ddf.security.handler.basic;

import org.apache.cxf.sts.QNameConstants;
import org.apache.cxf.ws.security.sts.provider.model.secext.AttributedString;
import org.apache.cxf.ws.security.sts.provider.model.secext.PasswordString;
import org.apache.cxf.ws.security.sts.provider.model.secext.UsernameTokenType;
import org.apache.ws.security.WSConstants;
import org.codice.ddf.security.handler.api.AuthenticationHandler;
import org.codice.ddf.security.handler.api.HandlerResult;
import org.jasypt.contrib.org.apache.commons.codec_1_3.binary.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.namespace.QName;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.security.Principal;

/**
 * Handler implementing Basic HTTP Authentication. If basic credentials are supplied in the HTTP
 * header, a UsernameToken will be created.
 */
public class BasicAuthenticationHandler implements AuthenticationHandler {

    private static final transient Logger LOGGER = LoggerFactory
            .getLogger(BasicAuthenticationHandler.class);

    public static final String AUTHENTICATION_SCHEME_BASIC = "Basic";

    /**
     * Basic type to use when configuring context policy.
     */
    public static final String AUTH_TYPE = "BASIC";

    private static final JAXBContext utContext = initContext();

    public String getAuthenticationType() {
        return AUTH_TYPE;
    }

    private static JAXBContext initContext() {
        try {
            return JAXBContext.newInstance(UsernameTokenType.class);
        } catch (JAXBException e) {
            LOGGER.error("Unable to create UsernameToken JAXB context.", e);
        }
        return null;
    }

    /**
     * Processes the incoming request to retrieve the username/password tokens. Handles responding
     * to the client that authentication is needed if they are not present in the request.
     * Returns the {@link org.codice.ddf.security.handler.api.HandlerResult} for the HTTP Request.
     *
     * @param request http request to obtain attributes from and to pass into any local filter chains required
     * @param response http response to return http responses or redirects
     * @param chain original filter chain (should not be called from your handler)
     * @param resolve flag with true implying that credentials should be obtained, false implying return if no credentials are found.
     * @return
     */
    @Override
    public HandlerResult getNormalizedToken(ServletRequest request,
        ServletResponse response, FilterChain chain, boolean resolve) {

        HandlerResult handlerResult;

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String path = httpRequest.getServletPath();
        LOGGER.debug("Handling request for path {}", path);

        LOGGER.debug("Doing authentication and authorization for path {}", path);
        final UsernameTokenType result = setAuthenticationInfo(httpRequest);
        if (resolve) {
            if (result == null) {
                handlerResult = new HandlerResult(HandlerResult.Status.REDIRECTED, null, "");
                doAuthPrompt("DDF", (HttpServletResponse) response);
                return handlerResult;
            } else {
                String usernameToken = getUsernameTokenElement(result);
                Principal principal = getPrincipal(result);
                handlerResult = new HandlerResult(HandlerResult.Status.COMPLETED, principal, usernameToken);
                return handlerResult;
            }
        } else {
            if (result == null) {
                handlerResult = new HandlerResult(HandlerResult.Status.NO_ACTION, null, "");
                return handlerResult;
            } else {
                String usernameToken = getUsernameTokenElement(result);
                Principal principal = getPrincipal(result);
                handlerResult = new HandlerResult(HandlerResult.Status.COMPLETED, principal, usernameToken);
                return handlerResult;
            }
        }
    }

    @Override
    public HandlerResult handleError(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws ServletException {
        doAuthPrompt("DDF", (HttpServletResponse) servletResponse);
        HandlerResult result = new HandlerResult();
        LOGGER.debug("In error handler for basic auth - prompted for auth credentials.");
        result.setStatus(HandlerResult.Status.REDIRECTED);
        return result;
    }

    /**
     * Extracts a Principal from a UsernameToken
     * @param result
     * @return Principal
     */
    private Principal getPrincipal(final UsernameTokenType result) {
        return new Principal() {
            private String username = result.getUsername().getValue();
            @Override public String getName() {
                return username;
            }
        };
    }

    /**
     * Returns the UsernameToken marshalled as a String so that it can be attached to the
     * {@link org.codice.ddf.security.handler.api.HandlerResult} object.
     * @param result
     * @return String
     */
    private synchronized String getUsernameTokenElement(UsernameTokenType result) {
        Writer writer = new StringWriter();
        Marshaller marshaller = null;
        if(utContext != null) {
            try {
                marshaller = utContext.createMarshaller();
                marshaller.setProperty(Marshaller.JAXB_FRAGMENT, Boolean.TRUE);
            } catch (JAXBException e) {
                LOGGER.error("Exception while creating UsernameToken marshaller.", e);
            }

            JAXBElement<UsernameTokenType> usernameTokenElement = new JAXBElement<UsernameTokenType>(
                    new QName(
                            "http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd",
                            "UsernameToken"), UsernameTokenType.class,
                    result
            );

            if (marshaller != null) {
                try {
                    marshaller.marshal(usernameTokenElement, writer);
                } catch (JAXBException e) {
                    LOGGER.error("Exception while writing username token.", e);
                }
            }
        }

        return writer.toString();
    }

    /**
     * Return a 401 response back to the web browser to prompt for basic auth.
     * @param realm
     * @param response
     */
    private void doAuthPrompt(String realm, HttpServletResponse response) {
        try {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE,
                    AUTHENTICATION_SCHEME_BASIC + " realm=\"" + realm + "\"");
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentLength(0);
            response.flushBuffer();
        } catch (IOException ioe) {
            LOGGER.debug("Failed to send auth response: {}", ioe);
        }

    }

    /**
     * Extract the Authorization header and parse into username/password.
     * @param authHeader
     * @param cb
     */
    private void extractAuthInfo(String authHeader, ExtractAuthInfoCallback cb) {
        authHeader = authHeader.trim();
        String[] parts = authHeader.split(" ");
        if (parts.length == 2) {
            String authType = parts[0];
            String authInfo = parts[1];

            if (authType.equalsIgnoreCase(AUTHENTICATION_SCHEME_BASIC)) {
                String decoded = new String(Base64.decodeBase64(authInfo.getBytes()));
                parts = decoded.split(":");
                if (parts.length == 2) {
                    String user = parts[0];
                    String password = parts[1];
                    cb.getAuthInfo(user, password);
                }
            }
        }
    }

    /**
     * Creates a UsernameToken from an HTTP request.
     * @param request
     * @return UsernameTokenType
     */
    private UsernameTokenType setAuthenticationInfo(HttpServletRequest request) {

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (authHeader == null || authHeader.equals("")) {
            return null;
        }

        final String[] username = {null};
        final String[] pass = {null};

        extractAuthInfo(authHeader, new ExtractAuthInfoCallback() {
            @Override
            public void getAuthInfo(String userName, String password) {
                username[0] = userName;
                pass[0] = password;
            }
        });

        if (username[0] == null) {
            return null;
        }

        if (username[0] != null && pass[0] != null) {
            UsernameTokenType usernameTokenType = new UsernameTokenType();
            AttributedString user = new AttributedString();
            user.setValue(username[0]);
            usernameTokenType.setUsername(user);

            // Add a password
            PasswordString password = new PasswordString();
            password.setValue(pass[0]);
            password.setType(WSConstants.PASSWORD_TEXT);
            JAXBElement<PasswordString> passwordType = new JAXBElement<PasswordString>(QNameConstants.PASSWORD, PasswordString.class, password);
            usernameTokenType.getAny().add(passwordType);

            return usernameTokenType;
        }

        return null;
    }

    public interface ExtractAuthInfoCallback {

        public void getAuthInfo(String userName, String password);

    }
}