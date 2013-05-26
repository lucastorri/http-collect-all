package collector;

/**
 * Copyright MITRE
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

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyServlet extends HttpServlet {

    private static final String host = "local";
    private static final String secureSufix = "-secure";

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        Pattern uriPattern = Pattern.compile("(.*)\\."+ host +"(" + secureSufix +")?");
        System.out.println("serverName: " + req.getServerName());
        Matcher matcher = uriPattern.matcher(req.getServerName());
        matcher.find();
        String destHost = matcher.group(1);
        boolean isHttps = matcher.group(2) != null && !matcher.group(2).isEmpty();
        String destScheme = isHttps ? "https://" : "http://";
        int destPort = req.getLocalPort();
        System.out.println(destHost);
        String destPath = req.getRequestURI();
        String destQueryString = req.getQueryString();

        HttpUriRequest fwdReq = null;


        String destUri = destScheme + destHost + ":" + destPort + destPath + "?" + destQueryString;

        switch (req.getMethod()) {
            case "POST":
                break;

            case "GET":
            default:
                fwdReq = new HttpGet(destUri);
                break;
        }

        Enumeration headerNames = req.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String header = headerNames.nextElement().toString();
            String value = header.equalsIgnoreCase("host") ? destHost : req.getHeader(header);
            fwdReq.addHeader(header, value);
        }
        //TODO add body


        HttpClient httpclient = new DefaultHttpClient();
        ResponseHandler<String> responseHandler = new BasicResponseHandler();
        String responseBody = httpclient.execute(fwdReq, responseHandler);
        System.out.println(responseBody);

        res.setStatus(200 /*TODO from response*/);
        res.getWriter().append(responseBody);
    }
}