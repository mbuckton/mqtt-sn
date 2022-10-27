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

package org.slj.mqtt.sn.console.impl;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slj.mqtt.sn.cloud.IMqttsnCloudService;
import org.slj.mqtt.sn.cloud.MqttsnCloudServiceException;
import org.slj.mqtt.sn.cloud.MqttsnConnectorDescriptor;
import org.slj.mqtt.sn.cloud.MqttsnConnectorDescriptorProperty;
import org.slj.mqtt.sn.console.http.*;
import org.slj.mqtt.sn.gateway.spi.connector.MqttsnConnectorException;
import org.slj.mqtt.sn.gateway.spi.connector.MqttsnConnectorOptions;
import org.slj.mqtt.sn.spi.IMqttsnRuntimeRegistry;
import org.slj.mqtt.sn.spi.IMqttsnStorageService;
import org.slj.mqtt.sn.spi.MqttsnException;
import org.slj.mqtt.sn.spi.MqttsnRuntimeException;

import java.io.IOException;
import java.util.*;

public class ConnectorHandler extends MqttsnConsoleAjaxRealmHandler {

    static final String ACTION = "action";
    static final String CONNECTOR = "connector";
    static final String START = "start";
    static final String STOP = "stop";
    static final String CONNECTOR_STATUS = "connectorStatus";
    static final String CONNECTION_STATUS = "connectionStatus";
    static final String CONNECTOR_INFO = "connectorInfo";
    static final String CONNECTOR_DETAILS = "connectorDetails";

    protected IMqttsnCloudService cloudService;

    public ConnectorHandler(IMqttsnCloudService cloudService, ObjectMapper mapper, IMqttsnRuntimeRegistry registry) {
        super(mapper, registry);
        this.cloudService = cloudService;
    }

    protected void handleHttpPost(IHttpRequestResponse request) throws HttpException, IOException {
        try {
            PropertyForm form = readRequestBody(request, PropertyForm.class);
            Iterator<String> itr = form.getProperties().keySet().iterator();
            MqttsnConnectorDescriptor descriptor = getDescriptorById(form.getProperties().get("connectorId"));
            IMqttsnStorageService storageService = getRegistry().getStorageService().getPreferenceNamespace(descriptor);
            while(itr.hasNext()){
                String key = itr.next();
                String value = form.getProperties().get(key);
                storageService.setStringPreference(key, value);
            }

            //-- restart the server
            MqttsnConnectorOptions options = new MqttsnConnectorOptions();
            storageService.initializeFieldsFromStorage(options);
            getRegistry().getBackendService().initializeConnector(descriptor, options);

            writeMessageBeanResponse(request, HttpConstants.SC_OK,
                    new Message("Connector successfully initialised", true));

        } catch(MqttsnCloudServiceException| MqttsnException e){
            e.printStackTrace();
            writeMessageBeanResponse(request, HttpConstants.SC_INTERNAL_SERVER_ERROR,
                    new Message("Error starting connector", e.getMessage(), false));
        }
    }

    @Override
    protected void handleHttpGet(IHttpRequestResponse request) throws HttpException, IOException {
        try {

            MqttsnConnectorDescriptor descriptor = getInstalledDescriptor();
            String action = null;
            if((action = getParameter(request, ACTION)) != null){
                String connectorId = null;
                if(STOP.equals(action)){
                    connectorId = getMandatoryParameter(request, CONNECTOR);
                    getRegistry().getBackendService().stop();
                    writeSimpleOKResponse(request);
                } else if(START.equals(action)){
                    connectorId = getMandatoryParameter(request, CONNECTOR);
                    getRegistry().getBackendService().start(getRegistry());
                    writeSimpleOKResponse(request);
                }
                else if(CONNECTOR_DETAILS.equals(action)){
                    connectorId = getMandatoryParameter(request, CONNECTOR);
                    writeJSONBeanResponse(request, HttpConstants.SC_OK, getDescriptorById(connectorId));
                }
                else if(CONNECTOR_STATUS.equals(action)){
                    boolean status = getRegistry().getBackendService().running();
                    String text = status ? "Connector is running" : "Connector is stopped";
                    writeHTMLResponse(request, HttpConstants.SC_OK, status(status, text));
                }
                else if(CONNECTION_STATUS.equals(action)){
                    boolean status = getRegistry().getBackendService().isConnected(null);
                    String text = status ? descriptor.getProtocol() + "  connection established" : descriptor.getProtocol()+" connection unavailable";
                    writeHTMLResponse(request, HttpConstants.SC_OK, status(status, text));
                }
                else if(CONNECTOR_INFO.equals(action)){
                    writeHTMLResponse(request, HttpConstants.SC_OK,
                            Html.span("Currently Installed:", Html.BLACK, false) + Html.linespace(3) +
                                    Html.span(descriptor.getName(), Html.BLACK, true));
                }
            } else {
                //return the connector bean list
                BeanList beanList = runtimeConnectors();
                writeJSONBeanResponse(request, HttpConstants.SC_OK, beanList);
            }
        } catch(Exception e){
            throw new HttpInternalServerError("error populating bean;", e);
        }
    }

    protected BeanList runtimeConnectors() throws MqttsnCloudServiceException {

        List<MqttsnConnectorDescriptor> connectors = null;
        if(cloudService != null && connectors == null){
            connectors = cloudService.getAvailableConnectors();
        }

        //for each connector apply its local runtime status
        if(connectors != null && !connectors.isEmpty()){
            connectors = applyRuntimeStatus(connectors);
        }

        BeanList beanList = new BeanList();
        beanList.connectors = connectors;
        return beanList;
    }

    protected List<MqttsnConnectorDescriptor> applyRuntimeStatus(List<MqttsnConnectorDescriptor> list){

        List<MqttsnConnectorDescriptor> runtimeConnectors = new ArrayList<>();
        Iterator<MqttsnConnectorDescriptor> itr = list.iterator();
        while(itr.hasNext()){
            MqttsnConnectorDescriptor bean = itr.next();
            RuntimeConnectorBean runtimeBean = new RuntimeConnectorBean(bean);
            try {
                applyRuntimeStatus(runtimeBean);
                runtimeConnectors.add(runtimeBean);
            } catch(MqttsnConnectorException e){
                //ignore
            }
        }
        return runtimeConnectors;
    }

    private void applyRuntimeStatus(RuntimeConnectorBean runtimeBean) throws MqttsnConnectorException{

        runtimeBean.available = getRegistry().getBackendService().connectorAvailable(runtimeBean);
        runtimeBean.running = getRegistry().getBackendService().matchesRunningConnector(runtimeBean) &&
                getRegistry().getBackendService().isConnected(null);
        //-- check if its running
        if(runtimeBean.available){
            //-- decorate the properties with those that would be used
            List<MqttsnConnectorDescriptorProperty> properties = runtimeBean.getProperties();
            IMqttsnStorageService storageService = getRegistry().getStorageService();
            if(properties != null){
                //load the connector specific settings and read back to the global ones
                properties.stream().forEach(p -> p.setValue(
                        storageService.getPreferenceNamespace(runtimeBean).
                                getStringPreference(p.getName(),
                                        storageService.getStringPreference(p.getName(), null))));
                properties.stream().filter(p -> p.getDefaultValue() != null).
                        forEach(p -> p.setValue(p.getDefaultValue()));
            }
        }
    }

    private MqttsnConnectorDescriptor getInstalledDescriptor() throws MqttsnCloudServiceException {
        return getDescriptorById(getRegistry().getConnector().getClass().getName());
    }

    private MqttsnConnectorDescriptor getDescriptorById(String connectorId) throws MqttsnCloudServiceException {
        BeanList beanList = runtimeConnectors();
        Optional<MqttsnConnectorDescriptor> descriptor = beanList.connectors.stream().
                filter(c -> c.getClassName().equals(connectorId)).findAny();
        if(descriptor.isPresent()) return descriptor.get();
        throw new MqttsnRuntimeException("unable to find running descriptor in cloud list " + connectorId);
    }

    private static String status(boolean status, String text){
        String statusStr = null;
        if(status){
            statusStr = "<button class=\"btn btn-sm btn-success\" type=\"button\" style=\"opacity: 100%\" disabled>\n" +
                    "                <span class=\"spinner-grow spinner-grow-sm\" role=\"status\" aria-hidden=\"true\"></span>\n" +
                    "                "+text+"\n" +
                    "            </button>";
        } else {
            statusStr = "<button class=\"btn btn-sm btn-danger\" type=\"button\" style=\"opacity: 100%\" disabled>\n" +
                    "                "+text+"\n" +
                    "            </button>";
        }

        return statusStr;
    }




    class BeanList {
        public List<MqttsnConnectorDescriptor> connectors;
    }

    class RuntimeConnectorBean extends MqttsnConnectorDescriptor {

        public boolean running = false;
        public boolean available = false;
        public RuntimeConnectorBean(MqttsnConnectorDescriptor bean){
            copyFrom(bean);
        }
    }


    static class PropertyForm {

        public PropertyForm() {
        }

        private Map<String, String> properties = new HashMap<>();

        @JsonAnyGetter
        public Map<String, String> getProperties() {
            return properties;
        }

        @JsonAnySetter
        public void setProperty(String property, String value){
            properties.put(property, value);
        }
    }
}
