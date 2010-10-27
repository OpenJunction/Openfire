<%@ page import="org.jivesoftware.util.CertificateManager" %>
<%@ page import="org.jivesoftware.util.ParamUtils" %>
<%@ page import="org.jivesoftware.openfire.XMPPServer" %>
<%@ page import="org.jivesoftware.openfire.net.SSLConfig" %>
<%@ page import="java.security.KeyStore" %>
<%@ page import="java.security.cert.X509Certificate" %>
<%@ page import="java.util.Enumeration" %>
<%@ page import="java.util.HashMap" %>
<%@ page import="java.util.Map" %>

<%@ taglib uri="http://java.sun.com/jstl/core_rt" prefix="c" %>
<%@ taglib uri="http://java.sun.com/jstl/fmt_rt" prefix="fmt" %>
<jsp:useBean id="webManager" class="org.jivesoftware.util.WebManager"  />
<% webManager.init(request, response, session, application, out ); %>

<%--
  Created by IntelliJ IDEA.
  User: gato
  Date: Nov 6, 2006
  Time: 3:15:13 PM
  To change this template use File | Settings | File Templates.
--%>

<% // Get parameters:
    boolean save = ParamUtils.getParameter(request, "save") != null;
    String name = ParamUtils.getParameter(request, "name");
    String organizationalUnit = ParamUtils.getParameter(request, "ou");
    String organization = ParamUtils.getParameter(request, "o");
    String city = ParamUtils.getParameter(request, "city");
    String state = ParamUtils.getParameter(request, "state");
    String countryCode = ParamUtils.getParameter(request, "country");

    Map<String, Object> errors = new HashMap<String, Object>();

    if (save) {
        KeyStore keyStore;
        try {
            keyStore = SSLConfig.getKeyStore();
        }
        catch (Exception e) {
            keyStore = SSLConfig.initializeKeyStore();
        }

        // Verify that fields were completed
        if (name == null) {
            errors.put("name", "");
        }
        if (organizationalUnit == null) {
            errors.put("organizationalUnit", "");
        }
        if (organization == null) {
            errors.put("organization", "");
        }
        if (city == null) {
            errors.put("city", "");
        }
        if (state == null) {
            errors.put("state", "");
        }
        if (countryCode == null) {
            errors.put("countryCode", "");
        }
        if (errors.size() == 0) {
            try {
                // Regenerate self-sign certs whose subjectDN matches the issuerDN and set the new issuerDN
                String domain = XMPPServer.getInstance().getServerInfo().getXMPPDomain();
                StringBuilder issuerDN = new StringBuilder();
                issuerDN.append("CN=").append(name);
                issuerDN.append(", OU=").append(organizationalUnit);
                issuerDN.append(", O=").append(organization);
                issuerDN.append(", L=").append(city);
                issuerDN.append(", ST=").append(state);
                issuerDN.append(", C=").append(countryCode);
                StringBuilder subjectDN = new StringBuilder();
                subjectDN.append("CN=").append(domain);
                subjectDN.append(", OU=").append(organizationalUnit);
                subjectDN.append(", O=").append(organization);
                subjectDN.append(", L=").append(city);
                subjectDN.append(", ST=").append(state);
                subjectDN.append(", C=").append(countryCode);
                // Update certs with new issuerDN information
                for (Enumeration<String> certAliases = keyStore.aliases(); certAliases.hasMoreElements();) {
                    String alias = certAliases.nextElement();
                    X509Certificate certificate = (X509Certificate) keyStore.getCertificate(alias);
                    // Update only Self-signed certs
                    if (CertificateManager.isSelfSignedCertificate(keyStore, alias)) {
                        if (CertificateManager.isDSACertificate(certificate)) {
                            CertificateManager.createDSACert(keyStore, SSLConfig.getKeyPassword(), alias,
                                    issuerDN.toString(), subjectDN.toString(), "*." + domain);
                        } else {
                            CertificateManager.createRSACert(keyStore, SSLConfig.getKeyPassword(), alias,
                                    issuerDN.toString(), subjectDN.toString(), "*." + domain);
                        }
                    }
                }
                // Save keystore
                SSLConfig.saveStores();
                // Log the event
                webManager.logEvent("generated SSL signing request", null);
                response.sendRedirect("ssl-certificates.jsp?issuerUpdated=true");
                return;
            }
            catch (Exception e) {
                e.printStackTrace();
                errors.put("general", "");
            }
        }
    }
%>

<html>
<head>
    <title>
        <fmt:message key="ssl.signing-request.title"/>
    </title>
    <meta name="pageID" content="ssl-certificates"/>
</head>
<body>
<%  if (errors.containsKey("name")) { %>
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.signing-request.enter_name" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%  } else if (errors.containsKey("organizationalUnit")) { %>
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.signing-request.enter_ou" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%  } else if (errors.containsKey("organization")) { %>
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.signing-request.enter_o" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%  } else if (errors.containsKey("city")) { %>
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.signing-request.enter_city" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%  } else if (errors.containsKey("state")) { %>
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.signing-request.enter_state" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%  } else if (errors.containsKey("countryCode")) { %>
    <div class="jive-error">
    <table cellpadding="0" cellspacing="0" border="0">
    <tbody>
        <tr><td class="jive-icon"><img src="images/error-16x16.gif" width="16" height="16" border="0" alt=""></td>
        <td class="jive-icon-label">
        <fmt:message key="ssl.signing-request.enter_country" />
        </td></tr>
    </tbody>
    </table>
    </div><br>
<%  } %>

<!-- BEGIN 'Issuer information form' -->
<form action="ssl-signing-request.jsp" method="post">
    <input type="hidden" name="save" value="true">
    <div class="jive-contentBoxHeader">
        <fmt:message key="ssl.signing-request.issuer_information"/>
    </div>
    <div class="jive-contentBox">
        <p>
            <fmt:message key="ssl.signing-request.issuer_information_info"/>
        </p>
        <table cellpadding="3" cellspacing="0" border="0">
            <tbody>
                <tr>
                    <td width="1%" nowrap>
                        <label for="namef">
                            <fmt:message key="ssl.signing-request.name"/>
                            :</label>
                    </td>
                    <td width="99%">
                        <input type="text" name="name" size="50" maxlength="75"
                               value="<%= ((name!=null) ? name : "") %>" id="namef">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="ouf"><fmt:message key="ssl.signing-request.organizational_unit"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="ou" size="50" maxlength="75" value="<%= ((organizationalUnit!=null) ? organizationalUnit : "") %>" id="ouf">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="of"><fmt:message key="ssl.signing-request.organization"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="o" size="50" maxlength="75" value="<%= ((organization!=null) ? organization : "") %>" id="of">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="cityf"><fmt:message key="ssl.signing-request.city"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="city" size="50" maxlength="75" value="<%= ((city!=null) ? city : "") %>" id="cityf">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="statef"><fmt:message key="ssl.signing-request.state"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="state" size="30" maxlength="75" value="<%= ((state!=null) ? state : "") %>" id="statef">
                    </td>
                </tr>
                <tr>
                    <td width="1%" nowrap>
                        <label for="countryf"><fmt:message key="ssl.signing-request.country"/>:</label></td>
                    <td width="99%">
                        <input type="text" name="country" size="2" maxlength="2" value="<%= ((countryCode!=null) ? countryCode : "") %>" id="countryf">
                    </td>
                </tr>
              <tr>
                  <td colspan="2">
                      <br>
                      <input type="submit" name="install" value="<fmt:message key="ssl.signing-request.save" />">
                  </td>
              </tr>
          </tbody>
          </table>
      </div>
  </form>
  <!-- END 'Issuer information form' -->
  </body>
</html>