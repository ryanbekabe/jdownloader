//jDownloader - Downloadmanager
//Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.appwork.storage.JSonStorage;
import org.appwork.storage.TypeRef;
import org.appwork.utils.StringUtils;
import org.appwork.utils.parser.UrlQuery;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterException;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.hoster.DuboxCom;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class DuboxComFolder extends PluginForDecrypt {
    public DuboxComFolder(PluginWrapper wrapper) {
        super(wrapper);
    }

    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForDecrypt, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "dubox.com" });
        return ret;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return buildAnnotationUrls(getPluginDomains());
    }

    public static String[] buildAnnotationUrls(final List<String[]> pluginDomains) {
        final List<String> ret = new ArrayList<String>();
        for (final String[] domains : pluginDomains) {
            ret.add("https?://(?:www\\.)?" + buildHostsPatternPart(domains) + "/(web/share/(?:link|init)\\?surl=[A-Za-z0-9\\-_]+(\\&path=[^/]+)?|s/[A-Za-z0-9\\-_]+)");
        }
        return ret.toArray(new String[0]);
    }

    @Override
    public int getMaxConcurrentProcessingInstances() {
        /* 2021-04-14: Try to avoid captchas */
        return 1;
    }

    public static final String getAppID() {
        return "250528";
    }

    public static final String getClientType() {
        return "0";
    }

    public static final String getChannel() {
        return "dubox";
    }

    public static final void setPasswordCookie(final Browser br, final String host, final String passwordCookie) {
        br.setCookie(host, "BOXCLND", passwordCookie);
    }

    private static final String TYPE_SHORT = "https?://[^/]+/s/(.+)";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final UrlQuery params = UrlQuery.parse(param.getCryptedUrl());
        String surl;
        String preGivenPath = null;
        if (param.getCryptedUrl().matches(TYPE_SHORT)) {
            surl = new Regex(param.getCryptedUrl(), TYPE_SHORT).getMatch(0);
        } else {
            surl = params.get("surl");
            preGivenPath = params.get("path");
        }
        if (!Encoding.isUrlCoded(preGivenPath)) {
            preGivenPath = Encoding.urlEncode(preGivenPath);
        }
        /* Fix surl value */
        if (surl.startsWith("1")) {
            surl = surl.substring(1, surl.length());
        }
        final PluginForHost plg = this.getNewPluginForHostInstance(this.getHost());
        final Account account = AccountController.getInstance().getValidAccount(this.getHost());
        if (account != null) {
            ((jd.plugins.hoster.DuboxCom) plg).login(account, false);
        }
        String passCode = param.getDecrypterPassword();
        /**
         * TODO: That is not enough -> We might have to re-use all cookies and/or maybe always store current/new session on account. </br>
         * It is only possible to use one "passwordCookie" at the same time!
         */
        String passwordCookie = param.getDownloadLink() != null ? param.getDownloadLink().getStringProperty(DuboxCom.PROPERTY_PASSWORD_COOKIE) : param.getDecrypterPassword();
        if (passwordCookie != null) {
            setPasswordCookie(this.br, this.br.getHost(), passwordCookie);
        }
        br.setFollowRedirects(true);
        Map<String, Object> entries = null;
        br.getHeaders().put("Accept", "application/json, text/plain, */*");
        final UrlQuery query = new UrlQuery();
        query.add("page", "1");
        query.add("num", "20");
        query.add("order", "time");
        query.add("desc", "1");
        query.add("shorturl", surl);
        if (!StringUtils.isEmpty(preGivenPath)) {
            query.add("dir", preGivenPath);
        } else {
            query.add("root", "1");
        }
        /* 2021-04-14 */
        query.add("app_id", getAppID());
        query.add("web", "1");
        query.add("channel", getChannel());
        query.add("clienttype", getClientType());
        do {
            br.getPage("https://www." + this.getHost() + "/share/list?" + query.toString());
            entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            int errno = ((Number) entries.get("errno")).intValue();
            if (errno == -9) {
                /* Password protected folder */
                final UrlQuery querypw = new UrlQuery();
                querypw.add("surl", surl);
                querypw.add("app_id", getAppID());
                querypw.add("web", "1");
                querypw.add("channel", getChannel());
                querypw.add("clienttype", getClientType());
                boolean captchaRequired = false;
                int count = 0;
                do {
                    if (passCode == null || count > 0) {
                        passCode = getUserInput("Password?", param);
                    }
                    errno = ((Number) entries.get("errno")).intValue();
                    final UrlQuery querypwPOST = new UrlQuery();
                    querypwPOST.appendEncoded("pwd", passCode);
                    /* 2021-04-14: Captcha only happens when adding a lot of items within a short amount of time. */
                    if (errno == -62) {
                        captchaRequired = true;
                        br.getPage("/api/getcaptcha?prod=shareverify&app_id=" + getAppID() + "&web=1&channel=" + getChannel() + "&clienttype=" + getClientType());
                        entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
                        final String captchaurl = (String) entries.get("vcode_img");
                        final String code = this.getCaptchaCode(captchaurl, param);
                        querypwPOST.appendEncoded("vcode", code);
                        querypwPOST.add("vcode_str", (String) entries.get("vcode_str"));
                    } else {
                        querypwPOST.add("vcode", "");
                        querypwPOST.add("vcode_str", "");
                    }
                    br.postPage("/share/verify?" + querypw.toString(), querypwPOST);
                    entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
                    errno = ((Number) entries.get("errno")).intValue();
                    passwordCookie = (String) entries.get("randsk");
                    if (!StringUtils.isEmpty(passwordCookie)) {
                        break;
                    } else {
                        if (count >= 3) {
                            logger.info("Giving up");
                            break;
                        } else {
                            logger.info("Wrong password or captcha");
                            count += 1;
                            continue;
                        }
                    }
                } while (!this.isAbort());
                if (passwordCookie == null) {
                    logger.info("Wrong password and/or captcha");
                    /* Asume wrong captcha if one was required */
                    if (captchaRequired) {
                        throw new PluginException(LinkStatus.ERROR_CAPTCHA);
                    } else {
                        throw new DecrypterException(DecrypterException.PASSWORD);
                    }
                }
                setPasswordCookie(this.br, this.br.getHost(), passwordCookie);
                /* Repeat the first request */
                br.getPage("https://www." + this.getHost() + "/share/list?" + query.toString());
                entries = JSonStorage.restoreFromString(br.toString(), TypeRef.HASHMAP);
            }
            final List<Object> ressourcelist = (List<Object>) entries.get("list");
            for (final Object ressourceO : ressourcelist) {
                entries = (Map<String, Object>) ressourceO;
                final String path = (String) entries.get("path");
                /* 2021-04-14: 'category' is represented as a String. */
                final long category = JavaScriptEngineFactory.toLong(entries.get("category"), -1);
                final long fsid = JavaScriptEngineFactory.toLong(entries.get("fs_id"), -1);
                if (JavaScriptEngineFactory.toLong(entries.get("isdir"), -1) == 1) {
                    final String url = "https://www.dubox.com/web/share/link?surl=" + surl + "&path=" + Encoding.urlEncode(path);
                    final DownloadLink folder = this.createDownloadlink(url);
                    if (passCode != null) {
                        folder.setDownloadPassword(passCode);
                    }
                    /* Saving- and re-using this can save us some time later. */
                    if (passwordCookie != null) {
                        folder.setProperty(DuboxCom.PROPERTY_PASSWORD_COOKIE, passwordCookie);
                    }
                    distribute(folder);
                    decryptedLinks.add(folder);
                } else {
                    final String serverfilename = (String) entries.get("server_filename");
                    final String realpath;
                    if (path.endsWith("/" + serverfilename)) {
                        realpath = path.replaceFirst("/" + org.appwork.utils.Regex.escape(serverfilename) + "$", "");
                    } else {
                        realpath = path;
                    }
                    final UrlQuery thisparams = new UrlQuery();
                    thisparams.add("surl", surl);
                    thisparams.appendEncoded("dir", realpath);// only the path!
                    thisparams.add("fsid", Long.toString(fsid));
                    thisparams.appendEncoded("fileName", serverfilename);
                    thisparams.add("page", "1");
                    final String url = "https://www.dubox.com/web/share/?" + thisparams.toString();
                    final String contentURL;
                    if (category == 1) {
                        contentURL = "https://www.dubox.com/web/share/videoPlay?" + thisparams.toString();
                    } else {
                        /* No URL available that points directly to that file! */
                        contentURL = param.toString();
                    }
                    final DownloadLink dl = new DownloadLink(plg, "dubox", this.getHost(), url, true);
                    dl.setContentUrl(contentURL);
                    dl.setContainerUrl(param.getCryptedUrl());
                    jd.plugins.hoster.DuboxCom.parseFileInformation(dl, entries);
                    if (passCode != null) {
                        dl.setDownloadPassword(passCode);
                    }
                    /* Saving- and re-using this can save us some time later. */
                    if (passwordCookie != null) {
                        dl.setProperty(DuboxCom.PROPERTY_PASSWORD_COOKIE, passwordCookie);
                    }
                    if (realpath.length() > 1) {
                        dl.setProperty(DownloadLink.RELATIVE_DOWNLOAD_FOLDER_PATH, realpath);
                        final FilePackage fp = FilePackage.getInstance();
                        fp.setName(realpath);
                        dl._setFilePackage(fp);
                    }
                    distribute(dl);
                    decryptedLinks.add(dl);
                }
            }
            /* TODO: Add pagination support */
            break;
        } while (!this.isAbort());
        return decryptedLinks;
    }
}