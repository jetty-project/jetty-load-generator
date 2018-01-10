//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.mortbay.jetty.load.generator;

import java.io.IOException;
import java.io.OutputStream;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.handler.AbstractHandler;

public class TestHandler extends AbstractHandler {
    @Override
    public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        jettyRequest.setHandled(true);
        String header = request.getHeader(Resource.RESPONSE_LENGTH);
        if (header != null) {
            OutputStream output = response.getOutputStream();
            int length = Integer.parseInt(header);
            byte[] buffer = new byte[2048];
            while (length > 0) {
                int l = Math.min(length, buffer.length);
                output.write(buffer, 0, l);
                length -= l;
            }
        }
    }
}
