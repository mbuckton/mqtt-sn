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

package org.slj.mqtt.sn.wire.version1_2.payload;

import org.slj.mqtt.sn.MqttsnConstants;
import org.slj.mqtt.sn.MqttsnSpecificationValidator;
import org.slj.mqtt.sn.codec.MqttsnCodecException;
import org.slj.mqtt.sn.spi.IMqttsnMessageValidator;

public class MqttsnConnack extends AbstractMqttsnMessage implements IMqttsnMessageValidator {

    @Override
    public int getMessageType() {
        return MqttsnConstants.CONNACK;
    }

    @Override
    public void decode(byte[] arr) throws MqttsnCodecException {
        setReturnCode(arr[arr.length - 1]);
    }

    @Override
    public byte[] encode() throws MqttsnCodecException {

        byte[] msg = new byte[3];
        msg[0] = 3;
        msg[1] = (byte) getMessageType();
        msg[2] = (byte) getReturnCode();
        return msg;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("MqttsnConnack{");
        sb.append("messageType=").append(messageType);
        sb.append(", returnCode=").append(returnCode);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public void validate() throws MqttsnCodecException {
        MqttsnSpecificationValidator.validateReturnCode(returnCode);
    }
}
