//jDownloader - Downloadmanager
//Copyright (C) 2010  JD-Team support@jdownloader.org
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

import org.appwork.utils.StringUtils;
import org.appwork.utils.formatter.SizeFormatter;

import jd.PluginWrapper;
import jd.config.Property;
import jd.http.Browser;
import jd.http.Cookies;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.Account;
import jd.plugins.AccountInfo;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.components.PluginJSonUtils;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "opendrive.com" }, urls = { "https?://(?:www\\.)?(?:[a-z0-9]+\\.)?(?:opendrive\\.com/files\\?[A-Za-z0-9\\-_]+|od\\.lk/(?:d|f)/[A-Za-z0-9\\-_]+)" })
public class OpenDriveCom extends PluginForHost {
    public OpenDriveCom(PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium("https://www.opendrive.com/");
    }

    @Override
    public String getAGBLink() {
        return "https://www.opendrive.com/terms";
    }

    public void correctDownloadLink(final DownloadLink link) {
        final String fileID = getFID(link);
        link.setPluginPatternMatcher(String.format("https://od.lk/f/%s", fileID));
    }

    @Override
    public String getLinkID(final DownloadLink link) {
        final String fid = getFID(link);
        if (fid != null) {
            return this.getHost() + "://" + fid;
        } else {
            return super.getLinkID(link);
        }
    }

    private String getFID(final DownloadLink link) {
        return new Regex(link.getPluginPatternMatcher(), "([A-Za-z0-9\\-_]+)$").getMatch(0);
    }

    public static Browser prepBRAjax(final Browser br) {
        br.getHeaders().put("Accept", "application/json, text/javascript, */*; q=0.01");
        br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
        br.setAllowedResponseCodes(new int[] { 400 });
        return br;
    }

    private enum MODE {
        API,
        WEBSITE_AJAX,
        WEBSITE_HTML
    };

    private static final MODE access_mode = MODE.WEBSITE_AJAX;

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        final String fileID = getFID(link);
        if (!link.isNameSet()) {
            /* Set fallback name */
            link.setName(fileID);
        }
        String filename, filesize, md5hash = null;
        if (access_mode == MODE.API) {
            logger.info("Using API");
            prepBRAjax(this.br);
            /* Call which is used inside folders to embed information of single filelinks */
            br.getPage("http://www.opendrive.com/ajax/file-info/" + fileID);
            br.getRequest().setHtmlCode(br.toString().replace("\\", ""));
            if (!br.containsHTML("\"FileId\"")) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = getJson("Name");
            filesize = getJson("SizeOriginal");
        } else if (access_mode == MODE.WEBSITE_AJAX) {
            prepBRAjax(this.br);
            br.getPage("https://web.opendrive.com/api/file/info.json/" + fileID);
            if (br.getHttpConnection().getResponseCode() == 400 || br.getHttpConnection().getResponseCode() == 404) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            filename = PluginJSonUtils.getJson(br, "Name");
            filesize = PluginJSonUtils.getJson(br, "Size");
            md5hash = PluginJSonUtils.getJson(br, "FileHash");
        } else {
            /* access_mode == MODE.WEBSITE_HTML */
            logger.info("NOT using API");
            return requestFileInformationWebsite(link);
        }
        if (!StringUtils.isEmpty(filesize)) {
            if (filesize.matches("\\d+")) {
                link.setDownloadSize(Long.parseLong(filesize));
            } else {
                link.setDownloadSize(SizeFormatter.getSize(filesize));
            }
        }
        if (!StringUtils.isEmpty(filename)) {
            link.setName(Encoding.htmlDecode(filename.trim()));
        }
        if (!StringUtils.isEmpty(md5hash)) {
            link.setMD5Hash(md5hash);
        }
        return AvailableStatus.TRUE;
    }

    public AvailableStatus requestFileInformationWebsite(final DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        br.getPage(link.getPluginPatternMatcher());
        if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML("(?i)>\\s*File not found<|>or access limited<|List file info failed|File was not found")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final Regex fInfo = br.getRegex("<h1 class=\"filename\">([^<>\"]*?)  \\((\\d+(\\.\\d+)? [A-Za-z]+)\\)</h1>");
        String filename = fInfo.getMatch(0);
        if (filename == null) {
            filename = br.getRegex("<i class=\"fa fa-info\"></i>\\s*<h3>(.*?)</h3>").getMatch(0);
            if (filename == null) {
                filename = br.getRegex("<div class=\"title bottom_border\"><span>([^<>\"]*?)</span>").getMatch(0);
                if (filename == null) {
                    filename = br.getRegex("<title>OpenDrive \\- ([^<>\"]*?)b</title>").getMatch(0);
                }
            }
        }
        String filesize = fInfo.getMatch(1);
        if (filesize == null) {
            filesize = br.getRegex("Size\\s*:\\s*<b>(.*?)</b>").getMatch(0);
            if (filesize == null) {
                filesize = br.getRegex("class=\"file_info size fl\"><b>Size:</b><span>([^<>\"]*?)</span></div>").getMatch(0);
            }
        }
        if (!StringUtils.isEmpty(filesize)) {
            if (filesize.matches("\\d+")) {
                link.setDownloadSize(Long.parseLong(filesize));
            } else {
                link.setDownloadSize(SizeFormatter.getSize(filesize));
            }
        }
        if (filename != null) {
            link.setName(Encoding.htmlDecode(filename.trim()));
        }
        return AvailableStatus.TRUE;
    }

    @Override
    public void handleFree(final DownloadLink link) throws Exception, PluginException {
        requestFileInformation(link);
        if (br.containsHTML("File is private and cannot be downloaded\">Download</a>")) {
            throw new AccountRequiredException("File is private and cannot be downloaded");
        }
        String dllink = checkDirectLink(link, "directurl");
        if (StringUtils.isEmpty(dllink)) {
            boolean useWebsiteFallback = false;
            if (access_mode == MODE.API) {
                dllink = getJson("DirectLink");
            } else if (access_mode == MODE.WEBSITE_AJAX) {
                dllink = PluginJSonUtils.getJson(br, "DownloadLink");
                if (StringUtils.equalsIgnoreCase(dllink, "n/a")) {
                    // /* 2021-11-17: Workaround */
                    logger.info("API did not provide downloadurl --> Fallback to website");
                    requestFileInformationWebsite(link);
                    useWebsiteFallback = true;
                }
            } else {
                /* access_mode == MODE.WEBSITE_HTML */
            }
            if (access_mode == MODE.WEBSITE_HTML || useWebsiteFallback) {
                dllink = br.getRegex("\"(https?://[^/]+/api/v\\d+/download/file\\.json/[^<>\"]+)\"").getMatch(0);
                if (dllink == null) {
                    dllink = br.getRegex("<a class=\"[^\"]*download\" href=\"(http[^<>\"]*?|/download/[^\"]+)\"").getMatch(0);
                    if (dllink == null) {
                        dllink = br.getRegex("\"(https?://(www\\.)?([a-z0-9]+\\.)?(?:opendrive\\.com|od\\.lk)/files/[A-Za-z0-9\\-_]+/[^<>\"]*?)\"").getMatch(0);
                    }
                    if (dllink == null) {
                        /* 2021-11-17 */
                        dllink = br.getRegex("(/download/[A-Za-z0-9\\-_]+\\?)").getMatch(0);
                    }
                }
            }
        }
        if (StringUtils.isEmpty(dllink)) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, dllink, true, 1);
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            try {
                br.followConnection(true);
            } catch (final IOException e) {
                logger.log(e);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if ("limit_exceeded.jpg".equalsIgnoreCase(getFileNameFromHeader(dl.getConnection()))) {
            throw new PluginException(LinkStatus.ERROR_TEMPORARILY_UNAVAILABLE, "Limit exeeded");
        }
        link.setProperty("directurl", dl.getConnection().getURL().toString());
        dl.startDownload();
    }

    private void login(final Account account, final boolean force) throws Exception {
        synchronized (account) {
            try {
                br.setCookiesExclusive(true);
                final Cookies cookies = account.loadCookies("");
                if (cookies != null && !force) {
                    br.setCookies(this.getHost(), cookies);
                    return;
                }
                this.br.setFollowRedirects(true);
                this.br.getPage("https://www." + this.getHost() + "/login");
                Form loginform = this.br.getFormbyProperty("id", "login-form");
                if (loginform == null) {
                    loginform = this.br.getForm(0);
                }
                if (loginform == null) {
                    if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin defekt, bitte den JDownloader Support kontaktieren!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else if ("pl".equalsIgnoreCase(System.getProperty("user.language"))) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nBłąd wtyczki, skontaktuj się z Supportem JDownloadera!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nPlugin broken, please contact the JDownloader Support!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                }
                loginform.put("login_username", account.getUser());
                loginform.put("login_password", account.getPass());
                loginform.remove("remember_me");
                loginform.put("remember_me", "on");
                for (int i = 0; i < 3; i++) { // Sometimes retry is needed, redirected to /login?ref=%2Ffiles&s=...
                    br.submitForm(loginform);
                    if (br.containsHTML(">Invalid  username or password<")) {
                        throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid username/password!\r\nUngültiger Benutzername oder ungültiges Passwort!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                    }
                    if (br.containsHTML("\"user-controls-menu\"")) {
                        break;
                    }
                    Thread.sleep(3 * 1000);
                }
                account.saveCookies(this.br.getCookies(br.getHost()), "");
            } catch (final PluginException e) {
                account.clearCookies("");
                account.setProperty("cookies", Property.NULL);
                throw e;
            }
        }
    }

    @Override
    public AccountInfo fetchAccountInfo(final Account account) throws Exception {
        AccountInfo ai = new AccountInfo();
        login(account, true);
        br.getPage("/settings");
        if (br.containsHTML("<b>Personal account</b><br>[\t\n\r ]*?Basic")) {
            if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nNicht unterstützter Accounttyp!\r\nFalls du denkst diese Meldung sei falsch die Unterstützung dieses Account-Typs sich\r\ndeiner Meinung nach aus irgendeinem Grund lohnt,\r\nkontaktiere uns über das support Forum.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            } else {
                throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUnsupported account type!\r\nIf you think this message is incorrect or it makes sense to add support for this account type\r\ncontact us via our support forum.", PluginException.VALUE_ID_PREMIUM_DISABLE);
            }
        }
        ai.setStatus("Premium account");
        return ai;
    }

    @Override
    public void handlePremium(final DownloadLink link, final Account account) throws Exception {
        requestFileInformation(link);
        login(account, false);
        br.setFollowRedirects(false);
        br.getPage(link.getPluginPatternMatcher());
        final String dllink = br.getRegex("<a class=\"download\" href=\"(https://[^<>\"]*?)\"").getMatch(0);
        if (dllink == null) {
            logger.warning("Final downloadlink (String is \"dllink\") regex didn't match!");
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl = jd.plugins.BrowserAdapter.openDownload(br, link, Encoding.htmlDecode(dllink), true, 1);
        if (!this.looksLikeDownloadableContent(dl.getConnection())) {
            logger.warning("The final dllink seems not to be a file!");
            try {
                br.followConnection(true);
            } catch (final IOException e) {
                logger.log(e);
            }
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        dl.startDownload();
    }

    private String checkDirectLink(final DownloadLink link, final String property) {
        String dllink = link.getStringProperty(property);
        if (dllink != null) {
            URLConnectionAdapter con = null;
            try {
                final Browser br2 = br.cloneBrowser();
                br2.setFollowRedirects(true);
                con = br2.openHeadConnection(dllink);
                if (this.looksLikeDownloadableContent(con)) {
                    if (con.getCompleteContentLength() > 0) {
                        link.setVerifiedFileSize(con.getCompleteContentLength());
                    }
                    return dllink;
                } else {
                    throw new IOException();
                }
            } catch (final Exception e) {
                logger.log(e);
                return null;
            } finally {
                if (con != null) {
                    con.disconnect();
                }
            }
        }
        return null;
    }

    private String getJson(final String parameter) {
        return getJson(this.br.toString(), parameter);
    }

    private String getJson(final String source, final String parameter) {
        String result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?([0-9\\.]+)").getMatch(1);
        if (result == null) {
            result = new Regex(source, "\"" + parameter + "\":([\t\n\r ]+)?\"([^<>\"]*?)\"").getMatch(1);
        }
        return result;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public void reset() {
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public boolean hasCaptcha(final DownloadLink link, final jd.plugins.Account acc) {
        return false;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }
}