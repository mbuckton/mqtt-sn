/*
 * Copyright (c) 2021 Simon Johnson <simon622 AT gmail DOT com>
 *
 * Find me on GitHub:
 * https://github.com/simon622
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.slj.mqtt.sn.http.impl;

import org.slj.mqtt.sn.http.*;
import org.slj.mqtt.sn.http.impl.handlers.StaticFileHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class AbstractHttpRequestResponseHandler implements IHttpRequestResponseHandler {
    protected final Logger LOG =
            Logger.getLogger(getClass().getName());

    public void handleRequest(IHttpRequestResponse httpRequestResponse) throws IOException {
        long start = System.currentTimeMillis();
        try {
            switch(httpRequestResponse.getMethod()){
                case GET:
                    handleHttpGet(httpRequestResponse);
                    break;
                case POST:
                    handleHttpPost(httpRequestResponse);
                    break;
                case HEAD:
                case PUT:
                case OPTIONS:
                case CONNECT:
                case TRACE:
                case PATCH:
                case DELETE:
                default:
                    sendUnsupportedOperationRequest(httpRequestResponse);
            }
        } catch(Exception e){
            e.printStackTrace();
            LOG.log(Level.WARNING, String.format("error handling request", e));
            try {
                writeASCIIResponse(httpRequestResponse, HttpConstants.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (Exception ex){
                LOG.log(Level.SEVERE, String.format("error sending internal server error request!", ex));
            }
        } finally {
            LOG.log(Level.INFO, String.format("request [%s] [%s] (%s) -> [%s] done in [%s]",
                    httpRequestResponse.getHttpRequestUri(), httpRequestResponse.getContextPath(),
                    httpRequestResponse.getContextRelativePath(), httpRequestResponse.getResponseCode(), System.currentTimeMillis() - start));
        }
    }

    protected void handleHttpGet(IHttpRequestResponse request) throws IOException {
        sendNotFoundResponse(request);
    }

    protected void handleHttpPost(IHttpRequestResponse request) throws IOException {
        sendNotFoundResponse(request);
    }

    protected void sendUnsupportedOperationRequest(IHttpRequestResponse request) throws IOException {
        writeASCIIResponse(request, HttpConstants.SC_METHOD_NOT_ALLOWED,
            Html.getErrorMessage(HttpConstants.SC_METHOD_NOT_ALLOWED, "Method not allowed"));
    }

    protected void sendNotFoundResponse(IHttpRequestResponse request) throws IOException {
        writeHTMLResponse(request, HttpConstants.SC_NOT_FOUND,
                Html.getErrorMessage(HttpConstants.SC_NOT_FOUND, "Resource Not found"));
    }

    protected void writeASCIIResponse(IHttpRequestResponse request, int responseCode, String message) throws IOException {
        writeResponse(request, responseCode, HttpConstants.PLAIN_MIME_TYPE, message.getBytes(StandardCharsets.UTF_8));
    }

    protected void writeHTMLResponse(IHttpRequestResponse request, int responseCode, String html) throws IOException {
        writeResponse(request, responseCode, HttpConstants.HTML_MIME_TYPE, html.getBytes(StandardCharsets.UTF_8));
    }

    protected void writeJSONResponse(IHttpRequestResponse request, int responseCode, byte[] bytes) throws IOException {
        writeResponse(request, responseCode, HttpConstants.JSON_MIME_TYPE, bytes);
    }

    protected void writeBinaryResponse(IHttpRequestResponse request, int responseCode, String mimeType, InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(1024);
        byte[] buf = new byte[1024];
        int length;
        while ((length = is.read(buf)) != -1) {
            baos.write(buf, 0, length);
        }
        byte[] bytes = baos.toByteArray();
        writeResponse(request, responseCode, mimeType, bytes);
    }

    protected void writeResponse(IHttpRequestResponse request, int responseCode, String mimeType, byte[] bytes) throws IOException {
        OutputStream os = null;
        try {
            request.setResponseContentType(mimeType, StandardCharsets.UTF_8);
            request.sendResponseHeaders(responseCode, bytes.length);
            os = request.getResponseBody();
            os.write(bytes);
        } finally {
            if(os != null) os.close();
        }
    }

    protected void writeContentFromResource(IHttpRequestResponse requestResponse, String resourcePath) throws IOException {
        InputStream is = loadClasspathResource(resourcePath);
        if (is == null) {
            sendNotFoundResponse(requestResponse);
        } else {
            String fileName = HttpUtils.getFileName(resourcePath);
            String ext = HttpUtils.getFileExtension(resourcePath);
            String mimeType = HttpUtils.getMimeTypeFromFileExtension(ext);
            writeBinaryResponse(requestResponse, HttpConstants.SC_OK, mimeType, is);
        }
    }

    protected InputStream loadClasspathResource(String resource) {
        LOG.log(Level.INFO, String.format("loading resource from path " + resource));
        return StaticFileHandler.class.getClassLoader().getResourceAsStream(resource);
    }
}