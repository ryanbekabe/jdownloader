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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Random;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.components.antiDDoSForHost;
import org.jdownloader.plugins.controller.host.LazyHostPlugin.FEATURE;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.parser.html.Form.MethodType;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.AccountInfo;
import jd.plugins.AccountUnavailableException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.components.MultiHosterManagement;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "pan.baiduwp.com" }, urls = { "" })
public class PanBaiduwpCom extends antiDDoSForHost {
    private static final String          PROTOCOL                  = "https://";
    /* Connection limits */
    private static final boolean         ACCOUNT_PREMIUM_RESUME    = true;
    private static final int             ACCOUNT_PREMIUM_MAXCHUNKS = 0;
    private static MultiHosterManagement mhm                       = new MultiHosterManagement("pan.baiduwp.com");

    public PanBaiduwpCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://pan.baiduwp.com/");
    }

    @Override
    public String getAGBLink() {
        return "https://pan.baiduwp.com/";
    }

    private Browser prepBR(final Browser br) {
        br.setCookiesExclusive(true);
        br.setFollowRedirects(true);
        return br;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws PluginException {
        return AvailableStatus.UNCHECKABLE;
    }

    @Override
    public boolean canHandle(final DownloadLink link, final Account account) throws Exception {
        if (account == null) {
            /* without account its not possible to download the link */
            return false;
        }
        final String internal_md5hash = link.getStringProperty("internal_md5hash", null);
        final String shorturl_id = link.getStringProperty("shorturl_id", null);
        final boolean urlCompatible = internal_md5hash != null && shorturl_id != null;
        return urlCompatible;
    }

    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public void handlePremium(DownloadLink link, Account account) throws Exception {
        /* handle premium should never be called */
        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
    }

    @Override
    public FEATURE[] getFeatures() {
        return new FEATURE[] { FEATURE.MULTIHOST };
    }

    @Override
    public void handleMultiHost(final DownloadLink link, final Account account) throws Exception {
        this.br = prepBR(this.br);
        mhm.runCheck(account, link);
        login(account, false);
        final String dllink = getDllink(account, link);
        if (StringUtils.isEmpty(dllink)) {
            mhm.handleErrorGeneric(account, link, "dllinknull", 50, 2 * 60 * 1000l);
        }
        handleDL(account, link, dllink);
    }

    private String getDllink(final Account account, final DownloadLink link) throws Exception {
        String dllink = checkDirectLink(link, this.getHost() + "directlink");
        if (dllink == null) {
            dllink = getDllinkWebsite(account, link);
        }
        return dllink;
    }

    private String getDllinkAPI(final DownloadLink link) throws IOException, PluginException {
        return null;
    }

    private String getDllinkWebsite(final Account account, final DownloadLink link) throws Exception {
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        final String internal_md5hash = link.getStringProperty("internal_md5hash", null);
        final String shorturl_id = link.getStringProperty("shorturl_id", null);
        /* In over 99% of all cases, we should already have the correct password here! */
        String passCode = link.getDownloadPassword();
        int counter = 0;
        boolean failed = false;
        do {
            if (counter > 0) {
                /* Password was incorrect or not given on the first try? Ask the user! */
                passCode = getUserInput("Password?", link);
            }
            getPage("https://" + this.getHost() + "/s/?surl=" + shorturl_id + "&pwd=" + Encoding.urlEncode(passCode));
            counter++;
            failed = br.containsHTML("class=\"modal\\-title\">请输入提取码<");
        } while (counter <= 2 && failed);
        if (failed) {
            link.setDownloadPassword(null);
            throw new PluginException(LinkStatus.ERROR_RETRY, "Wrong password entered");
        }
        if (passCode != null) {
            link.setDownloadPassword(passCode);
        }
        String targetHTML = null;
        /* Now we have a list of files of a folder but we want to download a specific file so let's find that ... */
        final String[] htmls = br.getRegex("<li class=\"list-group-item border-muted rounded text-muted py-2\">.*?</li>").getColumn(-1);
        for (final String html : htmls) {
            if (html.contains(internal_md5hash)) {
                targetHTML = html;
                break;
            }
        }
        if (targetHTML == null) {
            logger.warning("Failed to find html leading to desired file");
            mhm.handleErrorGeneric(account, link, "target_html_null", 50, 2 * 60 * 1000l);
        }
        String dlparamsStr = new Regex(targetHTML, "dl\\(([^\\)]+)\\)").getMatch(0);
        if (StringUtils.isEmpty(dlparamsStr)) {
            logger.warning("Failed to find dlparamsStr");
            mhm.handleErrorGeneric(account, link, "dlparamsStr_null", 50, 2 * 60 * 1000l);
        }
        dlparamsStr = dlparamsStr.replace("'", "");
        final String[] dlparams = dlparamsStr.split(",");
        if (dlparams == null || dlparams.length < 4) {
            mhm.handleErrorGeneric(account, link, "not_all_dlparams_given", 50, 2 * 60 * 1000l);
        }
        final Form dlform = new Form();
        dlform.setMethod(MethodType.POST);
        dlform.setAction("/download");
        dlform.put("f", dlparams[0]);
        dlform.put("t", dlparams[1]);
        dlform.put("p", dlparams[2]);
        dlform.put("v", dlparams[3]);
        dlform.put("n", "");
        dlform.put("i", "");
        this.submitForm(dlform);
        if (br.containsHTML(">下载次数已达今日上限，请明天再试")) {
            throw new AccountUnavailableException("Daily downloadlimit reached", 1 * 60 * 60 * 1000l);
        }
        final String continue_url = br.getRegex("url\\s*?:\\s*?\\'(http[^<>\"\\']+)\\'").getMatch(0);
        if (continue_url == null) {
            logger.warning("Failed to find continue_url");
            mhm.handleErrorGeneric(account, link, "continue_url_null", 50, 2 * 60 * 1000l);
        }
        getPage(continue_url);
        final LinkedHashMap<String, Object> entries = (LinkedHashMap<String, Object>) JavaScriptEngineFactory.jsonToJavaMap(br.toString());
        final ArrayList<Object> ressourcelist = (ArrayList<Object>) entries.get("urls");
        if (ressourcelist == null || ressourcelist.isEmpty()) {
            mhm.handleErrorGeneric(account, link, "mirrors_null", 50, 2 * 60 * 1000l);
        }
        /* Usually there are 4 mirrors available. Chose random mirror to download. */
        final int random = new Random().nextInt(ressourcelist.size());
        String dllink = (String) ressourcelist.get(random);
        return dllink;
    }

    private void handleDL(final Account account, final DownloadLink link, final String dllink) throws Exception {
        link.setProperty(this.getHost() + "directlink", dllink);
        /* Important! Bad headers e.g. Referer will cause a 403 response! */
        br = new Browser();
        try {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, ACCOUNT_PREMIUM_RESUME, ACCOUNT_PREMIUM_MAXCHUNKS);
            String server_filename = getFileNameFromDispositionHeader(dl.getConnection());
            if (server_filename != null && server_filename.contains("%")) {
                server_filename = Encoding.htmlDecode(server_filename);
                link.setFinalFileName(server_filename);
            }
            final String contenttype = dl.getConnection().getContentType();
            if (contenttype.contains("html")) {
                br.followConnection();
                mhm.handleErrorGeneric(account, link, "unknowndlerror", 20, 5 * 60 * 1000l);
            }
            this.dl.startDownload();
        } catch (final Exception e) {
            link.setProperty(this.getHost() + "directlink", Property.NULL);
            throw e;
        }
    }

    private String checkDirectLink(final DownloadLink downloadLink, final String property) {
        String dllink = downloadLink.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openHeadConnection(dllink);
                if (con.getContentType().contains("html") || con.getLongContentLength() == -1) {
                    downloadLink.setProperty(property, Property.NULL);
                    dllink = null;
                }
            } catch (final Exception e) {
                downloadLink.setProperty(property, Property.NULL);
                dllink = null;
            } finally {
                try {
                    con.disconnect();
                } catch (final Throwable e) {
                }
            }
        }
        return dllink;
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        this.br = prepBR(this.br);
        final AccountInfo ai = fetchAccountInfoWebsite(account);
        return ai;
    }

    public AccountInfo fetchAccountInfoWebsite(final Account account) throws Exception {
        /*
         * 2017-11-29: Lifetime premium not (yet) supported via website mode! But by the time we might need the website version again, they
         * might have stopped premium lifetime sales already as that has never been a good idea for any (M)OCH.
         */
        final AccountInfo ai = new AccountInfo();
        login(account, true);
        ArrayList<String> supportedHosts = new ArrayList<String>();
        supportedHosts.add("pan.baidu.com");
        account.setType(AccountType.PREMIUM);
        account.setMaxSimultanDownloads(1);
        ai.setStatus("Dummy account");
        ai.setMultiHostSupport(this, supportedHosts);
        return ai;
    }

    public AccountInfo fetchAccountInfoAPI(final Account account) throws Exception {
        return null;
    }

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            /* Load cookies */
            br.setCookiesExclusive(true);
            this.br = prepBR(this.br);
            loginWebsite(account, force);
        }
    }

    private void loginWebsite(final Account account, final boolean force) throws Exception {
        /* Dummy */
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        /*
         * 2019-07-04: I was not able to find a real limit of simultaneous downloads but they have a daily limit which can be reached easily
         * which is why I've limited this to 1.
         */
        return 1;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}