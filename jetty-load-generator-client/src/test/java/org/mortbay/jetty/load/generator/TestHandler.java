//
// ========================================================================
// Copyright (c) 2016-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.mortbay.jetty.load.generator;

import java.nio.ByteBuffer;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.IteratingNestedCallback;

public class TestHandler extends Handler.Abstract {
    @Override
    public boolean handle(Request request, Response response, Callback callback) {
        long length = request.getHeaders().getLongField(Resource.RESPONSE_LENGTH);
        if (length > 0) {
            sendResponseContent(response, length, callback);
        } else {
            callback.succeeded();
        }
        return true;
    }

    private void sendResponseContent(Response response, long contentLength, Callback callback) {
        new IteratingNestedCallback(callback) {
            private long length = contentLength;

            @Override
            protected Action process() {
                if (length == 0) {
                    return Action.SUCCEEDED;
                }
                int l = (int)Math.min(length, 2048);
                length -= l;
                response.write(length == 0, ByteBuffer.allocate(l), this);
                return Action.SCHEDULED;
            }
        }.iterate();
    }
}
