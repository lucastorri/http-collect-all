package collector;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class VoidServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        System.out.println("AuthType: " + req.getAuthType());
        System.out.println("ContextPath: " + req.getContextPath());
        System.out.println("Cookies: " + req.getCookies());
        System.out.println("HeaderNames: " + req.getHeaderNames());
        System.out.println("Method: " + req.getMethod());
        System.out.println("PathInfo: " + req.getPathInfo());
        System.out.println("PathTranslated: " + req.getPathTranslated());
        System.out.println("QueryString: " + req.getQueryString());
        System.out.println("RemoteUser: " + req.getRemoteUser());
        System.out.println("RequestedSessionId: " + req.getRequestedSessionId());
        System.out.println("RequestURI: " + req.getRequestURI());
        System.out.println("RequestURL: " + req.getRequestURL());
        System.out.println("ServletPath: " + req.getServletPath());
        System.out.println("Session: " + req.getSession());
        System.out.println("UserPrincipal: " + req.getUserPrincipal());
        System.out.println("AttributeNames: " + req.getAttributeNames());
        System.out.println("CharacterEncoding: " + req.getCharacterEncoding());
        System.out.println("ContentLength: " + req.getContentLength());
        System.out.println("ContentType: " + req.getContentType());
        System.out.println("LocalAddr: " + req.getLocalAddr());
        System.out.println("Locale: " + req.getLocale());
        System.out.println("Locales: " + req.getLocales());
        System.out.println("LocalName: " + req.getLocalName());
        System.out.println("LocalPort: " + req.getLocalPort()); // <--
        System.out.println("ParameterMap: " + req.getParameterMap());
        System.out.println("ParameterNames: " + req.getParameterNames());
        System.out.println("Protocol: " + req.getProtocol());
        System.out.println("RemoteAddr: " + req.getRemoteAddr());
        System.out.println("RemoteHost: " + req.getRemoteHost());
        System.out.println("RemotePort: " + req.getRemotePort());
        System.out.println("Scheme: " + req.getScheme());
        System.out.println("ServerName: " + req.getServerName()); // <--
        System.out.println("ServerPort: " + req.getServerPort());
    }
}
