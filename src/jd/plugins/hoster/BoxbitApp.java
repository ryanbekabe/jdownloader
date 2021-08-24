//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
//
//This program is free software: you can redistribute it and/or modify
//it under the terms of the GNU General Public License as published by
//the Free Software Foundation, either version 3 of the License, or
//(at your option) any later version.
//
//This program is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty of
//MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//GNU General Public License for more details.
//
//You should have received a copy of the GNU General Public License
//along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.appwork.net.protocol.http.HTTPConstants;
import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.DebugMode;
import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.TimeFormatter;
import org.appwork.utils.parser.UrlQuery;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.MultiHosterManagement;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "boxbit.app" }, urls = { "" })
public class BoxbitApp extends PluginForHost {
    /**
     * New project of: geragera.com.br </br>
     * API docs: https://boxbit.readme.io/reference/introduction
     */
    private static final String          API_BASE                        = "https://api.boxbit.app";
    private static MultiHosterManagement mhm                             = new MultiHosterManagement("boxbit.app");
    private static final int             defaultMAXDOWNLOADS             = -1;
    private static final int             defaultMAXCHUNKS                = 0;
    private static final boolean         defaultRESUME                   = true;
    private static final String          PROPERTY_userid                 = "userid";
    private static final String          PROPERTY_logintoken             = "token";
    private static final String          PROPERTY_logintoken_valid_until = "token_valid_until";
    private static final String          PROPERTY_directlink             = "directlink";

    @SuppressWarnings("deprecation")
    public BoxbitApp(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://boxbit.app/!/register");
    }

    @Override
    public String getAGBLink() {
        return "https://boxbit.app/";
    }

    private Browser prepBR(final Browser br) {
        br.setCookiesExclusive(true);
        br.getHeaders().put(HTTPConstants.HEADER_REQUEST_USER_AGENT, "JDownloader");
        br.getHeaders().put(HTTPConstants.HEADER_REQUEST_ACCEPT, "application/json");
        br.setFollowRedirects(true);
        br.setAllowedResponseCodes(new int[] { 422, 429 });
        return br;
    }

    private void setLoginHeader(final Browser br, final Account account) {
        br.getHeaders().put(HTTPConstants.HEADER_REQUEST_AUTHORIZATION, "Bearer " + this.getLoginToken(account));
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws PluginException {
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public boolean canHandle(final DownloadLink link, final Account account) throws Exception {
        return account != null;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        /* handlePremium should never get called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    private void handleDL(final Account account, final DownloadLink link) throws Exception {
        final String directlinkproperty = this.getHost() + PROPERTY_directlink;
        if (!attemptStoredDownloadurlDownload(link, directlinkproperty)) {
            this.loginAPI(account, false);
            String passCode = link.getDownloadPassword();
            if (link.isPasswordProtected() && passCode == null) {
                passCode = getUserInput("Password?", link);
            }
            final Map<String, Object> postdata = new HashMap<String, Object>();
            postdata.put("link", link.getDefaultPlugin().buildExternalDownloadURL(link, this));
            if (passCode != null) {
                postdata.put("password", passCode);
            }
            br.postPageRaw(API_BASE + "/users/" + this.getUserID(account) + "/downloader/request-file", JSonStorage.serializeToJson(postdata));
            this.handleErrors(this.br, account, link);
            if (passCode != null) {
                /* Given password is correct --> Save it for later usage */
                link.setDownloadPassword(passCode);
            }
            final Map<String, Object> entries = JavaScriptEngineFactory.jsonToJavaMap(br.toString());
            final String dllink = (String) entries.get("link");
            if (StringUtils.isEmpty(dllink)) {
                /* This should never happen */
                mhm.handleErrorGeneric(account, link, "dllinknull", 50, 5 * 60 * 1000l);
            }
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, defaultRESUME, defaultMAXCHUNKS);
            link.setProperty(this.getHost() + PROPERTY_directlink, dl.getConnection().getURL().toString());
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                try {
                    br.followConnection(true);
                } catch (final IOException e) {
                    logger.log(e);
                }
                handleErrors(this.br, account, link);
                mhm.handleErrorGeneric(account, link, "unknown_dl_error", 50, 5 * 60 * 1000l);
            }
        } else {
            logger.info("Re-using stored directurl");
        }
        this.dl.startDownload();
    }

    private boolean attemptStoredDownloadurlDownload(final DownloadLink link, final String directlinkproperty) throws Exception {
        final String url = link.getStringProperty(directlinkproperty);
        if (StringUtils.isEmpty(url)) {
            return false;
        }
        try {
            final Browser brc = br.cloneBrowser();
            dl = new jd.plugins.BrowserAdapter().openDownload(brc, link, url, defaultRESUME, defaultMAXCHUNKS);
            if (this.looksLikeDownloadableContent(dl.getConnection())) {
                return true;
            } else {
                brc.followConnection(true);
                throw new IOException();
            }
        } catch (final Throwable e) {
            logger.log(e);
            try {
                dl.getConnection().disconnect();
            } catch (Throwable ignore) {
            }
            return false;
        }
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        prepBR(this.br);
        mhm.runCheck(account, link);
        handleDL(account, link);
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        prepBR(this.br);
        final AccountInfo ai = new AccountInfo();
        loginAPI(account, true);
        /* Access userInfo if it hasn't been accessed already. */
        if (br.getURL() == null || !br.getURL().contains("/users/")) {
            accessUserInfo(this.br, account);
        }
        final Map<String, Object> user = JavaScriptEngineFactory.jsonToJavaMap(br.toString());
        final Map<String, Object> subscription = (Map<String, Object>) user.get("subscription");
        List<Map<String, Object>> hosts = null;
        final Object currentSubscriptionO = subscription.get("current");
        if (currentSubscriptionO == null) {
            setAccountInfo(account, ai, AccountType.FREE);
        } else {
            final Map<String, Object> currentSubscription = (Map<String, Object>) currentSubscriptionO;
            if ((Boolean) currentSubscription.get("is_active")) {
                setAccountInfo(account, ai, AccountType.PREMIUM);
                final String end_date = currentSubscription.get("end_date").toString();
                ai.setValidUntil(TimeFormatter.getMilliSeconds(end_date, "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH));
                ai.setStatus("Package: " + currentSubscription.get("package_name"));
                hosts = (List<Map<String, Object>>) currentSubscription.get("filehosts");
            } else {
                /* Expired premium account?? */
                setAccountInfo(account, ai, AccountType.FREE);
            }
        }
        if (DebugMode.TRUE_IN_IDE_ELSE_FALSE) {
            String status = ai.getStatus();
            if (status == null) {
                status = "";
            }
            status += "|TokenValidity: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(this.getLoginTokenValidity(account));
            ai.setStatus(status);
        }
        final ArrayList<String> supportedhostslist = new ArrayList<String>();
        /* Can be null for free accounts! */
        if (hosts != null) {
            for (final Map<String, Object> hostInfo : hosts) {
                final Map<String, Object> hostDetails = (Map<String, Object>) hostInfo.get("details");
                final String host = (String) hostDetails.get("identifier");
                if (hostDetails.get("status").toString().equalsIgnoreCase("working")) {
                    supportedhostslist.add(host);
                } else {
                    logger.info("Skipping non working host: " + host);
                }
            }
        }
        ai.setMultiHostSupport(this, supportedhostslist);
        account.setConcurrentUsePossible(true);
        return ai;
    }

    private void setAccountInfo(final Account account, final AccountInfo ai, final AccountType type) {
        if (type == AccountType.PREMIUM) {
            account.setType(AccountType.PREMIUM);
            account.setMaxSimultanDownloads(defaultMAXDOWNLOADS);
        } else {
            account.setType(type);
            account.setMaxSimultanDownloads(defaultMAXDOWNLOADS);
            /* Free accounts cannot be used for downloading */
            ai.setTrafficLeft(0);
            ai.setExpired(true);
        }
    }

    private void loginAPI(final Account account, final boolean forceAuthCheck) throws IOException, PluginException, InterruptedException {
        synchronized (account) {
            this.prepBR(this.br);
            final String token = getLoginToken(account);
            final String userid = getUserID(account);
            Map<String, Object> entries;
            if (token != null && userid != null) {
                setLoginHeader(this.br, account);
                final long tokenRemainingTimeMillis = getLoginTokenRemainingMillis(account);
                /* Force token refresh if it has expired or is valid for less than <minimumTokenValidity> only. */
                final long minimumTokenValidity = 60 * 60 * 1000;
                final boolean tokenRefreshRequired = tokenRemainingTimeMillis < minimumTokenValidity;
                if (!forceAuthCheck && !tokenRefreshRequired) {
                    /* We trust our token --> Do not check them */
                    logger.info("Trust login token without check");
                    return;
                } else {
                    if (tokenRefreshRequired) {
                        logger.info("Token refresh required");
                    } else {
                        logger.info("Attempting token login");
                        try {
                            accessUserInfo(this.br, account);
                            this.handleErrors(this.br, account, getDownloadLink());
                            logger.info("Token login successful");
                            return;
                        } catch (final PluginException ignore) {
                            logger.info("Token login failed --> Trying token refresh");
                        }
                    }
                    logger.info("Attempting token refresh");
                    /* Send empty POST request with existing token to get a fresh token */
                    br.postPage(API_BASE + "/auth/refresh", new UrlQuery());
                    try {
                        this.handleErrors(this.br, account, getDownloadLink());
                        logger.info("Token refresh successful");
                        entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
                        /* Store new token */
                        setAuthTokenData(account, (Map<String, Object>) entries.get("auth"));
                        /* Set new auth header */
                        setLoginHeader(this.br, account);
                        return;
                    } catch (final PluginException tokenRefreshFailure) {
                        logger.warning("Token refresh failed");
                        br.getHeaders().remove(HTTPConstants.HEADER_REQUEST_AUTHORIZATION);
                        /* Try full login as last-chance */
                    }
                }
            }
            logger.info("Performing full login");
            br.postPageRaw(API_BASE + "/auth/login", "{\"email\":\"" + account.getUser() + "\",\"password\":\"" + account.getPass() + "\"}");
            this.handleErrors(this.br, account, getDownloadLink());
            entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            account.setProperty(PROPERTY_userid, JavaScriptEngineFactory.walkJson(entries, "user/uuid").toString());
            setAuthTokenData(account, (Map<String, Object>) entries.get("auth"));
            setLoginHeader(this.br, account);
        }
    }

    private static void setAuthTokenData(final Account account, final Map<String, Object> auth) {
        account.setProperty(PROPERTY_logintoken, auth.get("access_token").toString());
        /* 2021-08-18: tokens are usually valid for 72 hours */
        account.setProperty(PROPERTY_logintoken_valid_until, System.currentTimeMillis() + ((Number) auth.get("expires_in")).longValue() * 1000);
    }

    private String getLoginToken(final Account account) {
        return account.getStringProperty(PROPERTY_logintoken);
    }

    private long getLoginTokenValidity(final Account account) {
        return account.getLongProperty(PROPERTY_logintoken_valid_until, 0);
    }

    private long getLoginTokenRemainingMillis(final Account account) {
        final long validityTimestamp = getLoginTokenValidity(account);
        if (validityTimestamp < System.currentTimeMillis()) {
            return 0;
        } else {
            return validityTimestamp - System.currentTimeMillis();
        }
    }

    private String getUserID(final Account account) {
        return account.getStringProperty(PROPERTY_userid);
    }

    /** https://boxbit.readme.io/reference/user-filehost-list */
    private void accessUserInfo(final Browser br, final Account account) throws IOException {
        br.getPage(API_BASE + "/users/" + getUserID(account) + "/?with[]=subscription&with[]=current_subscription_filehosts&with[]=current_subscription_filehost_usages");
    }

    /** Handle errors according to: https://boxbit.readme.io/reference/error-codes */
    private void handleErrors(final Browser br, final Account account, final DownloadLink link) throws PluginException, InterruptedException {
        if (br.getHttpConnection().getResponseCode() == 401) {
            /* Authentication failure */
            final Map<String, Object> entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            final String message = (String) entries.get("message");
            throw new PluginException(LinkStatus.ERROR_PREMIUM, message, PluginException.VALUE_ID_PREMIUM_DISABLE);
        } else if (br.getHttpConnection().getResponseCode() == 422 || br.getHttpConnection().getResponseCode() == 429) {
            final Map<String, Object> entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            final String message = (String) entries.get("message");
            /* All possible error-keys go along with http response 422 except "busy_worker" --> 429 */
            final ArrayList<String> errors = (ArrayList<String>) entries.get("errors");
            for (final String error : errors) {
                if (error.equals("subscription_not_active ")) {
                    /* Account doesn't have any paid subscription package */
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, message, PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else if (error.equals("unsupported_filehost") || error.equals("user_missing_filehost") || error.equals("filehost_unavailable") || error.equals("filehost_links_quota_exceeded") || error.equals("filehost_traffic_quota_exceeded")) {
                    /* Errors that immediately temporarily disable a host */
                    mhm.putError(account, link, 5 * 60 * 1000l, message);
                } else if (error.equals("wrong_password")) {
                    /* Missing or wrong password */
                    link.setPasswordProtected(true); // Enable this so next time we'll ask the user to enter the password
                    link.setDownloadPassword(null);
                    throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
                } else {
                    /* Stuff we may know but purposely handle as generic error: blocked_ip, temporarily_unavailable, ip_address_not_br */
                    logger.info("Detected unknown/generic errorkey: " + error);
                }
            }
            /* They also got "file_not_found" but we still don't trust such errors when multihosts return them! */
            /* No known error? Handle as generic one. */
            mhm.handleErrorGeneric(account, link, message, 50);
        } else {
            /* No error */
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }
}