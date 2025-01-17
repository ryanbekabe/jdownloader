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

import jd.PluginWrapper;
import jd.controlling.AccountController;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.Account;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.plugins.hoster.EroProfileCom;
import jd.utils.JDUtilities;

import org.jdownloader.plugins.controller.LazyPlugin;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "eroprofile.com" }, urls = { "https?://(www\\.)?eroprofile\\.com/m/photos/album/[A-Za-z0-9\\-_]+" })
public class EroProfileComGallery extends PluginForDecrypt {
    public EroProfileComGallery(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.IMAGE_GALLERY, LazyPlugin.FEATURE.XXX };
    }

    /* must be static so all plugins share same lock */
    private static Object LOCK = new Object();

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        String parameter = param.toString();
        br.setReadTimeout(3 * 60 * 1000);
        br.setCookiesExclusive(false);
        br.getHeaders().put("Accept-Language", "en-us,en;q=0.5");
        br.setCookie("http://eroprofile.com/", "lang", "en");
        boolean loggedin = false;
        synchronized (LOCK) {
            /** Login process */
            // force login to see if that solves problems
            loggedin = getUserLogin(true);
        }
        br.setFollowRedirects(true);
        br.getPage(parameter);
        // Check if account needed but none account entered
        if (br.containsHTML(jd.plugins.hoster.EroProfileCom.NOACCESS) && !loggedin) {
            logger.info("Account needed to decrypt link: " + parameter);
            return decryptedLinks;
        } else if (br.containsHTML(jd.plugins.hoster.EroProfileCom.NOACCESS)) {
            logger.info("No cookies, login maybe failed: " + parameter);
            return decryptedLinks;
        } else if (br.getHttpConnection().getResponseCode() == 404 || br.containsHTML(">Album not found<|>\\s*No photos found")) {
            decryptedLinks.add(this.createOfflinelink(parameter));
            return decryptedLinks;
        }
        String fpName = br.getRegex("Browse photos from album \\&quot;([^<>\"]*?)\\&quot;<").getMatch(0);
        if (fpName == null) {
            fpName = EroProfileCom.getFilename(br);
        }
        final List<String> pagesDones = new ArrayList<String>();
        final List<String> pagesLeft = new ArrayList<String>();
        pagesLeft.add("1");
        while (!isAbort()) {
            final String page;
            if (pagesLeft.size() > 0) {
                page = pagesLeft.remove(0);
                if (pagesDones.contains(page)) {
                    continue;
                }
            } else {
                break;
            }
            if (!page.equals("1")) {
                br.getPage(parameter + "?pnum=" + page);
            }
            pagesDones.add(page);
            final String[] nextPages = br.getRegex("\\?pnum=(\\d+)\"").getColumn(0);
            if (nextPages != null && nextPages.length != 0) {
                for (final String nextPage : nextPages) {
                    if (!pagesLeft.contains(nextPage)) {
                        pagesLeft.add(nextPage);
                    }
                }
            }
            String[][] links = br.getRegex("<a href=\"(/m/photos/view/([A-Za-z0-9\\-_]+))\"").getMatches();
            if (links == null || links.length == 0) {
                logger.warning("Decrypter broken for link: " + parameter);
                return null;
            }
            for (final String singleLink[] : links) {
                final DownloadLink dl = createDownloadlink("https://www.eroprofile.com" + singleLink[0]);
                // final filename is set later in hosterplugin
                dl.setName(singleLink[1] + ".jpg");
                dl.setAvailable(true);
                decryptedLinks.add(dl);
            }
        }
        if (fpName != null) {
            FilePackage fp = FilePackage.getInstance();
            fp.setName(Encoding.htmlDecode(fpName.trim()));
            fp.addLinks(decryptedLinks);
        }
        return decryptedLinks;
    }

    /**
     * JD2 CODE: DO NOIT USE OVERRIDE FÒR COMPATIBILITY REASONS!!!!!
     */
    public boolean isProxyRotationEnabledForLinkCrawler() {
        return false;
    }

    private boolean getUserLogin(final boolean force) throws Exception {
        final PluginForHost hostPlugin = JDUtilities.getPluginForHost("eroprofile.com");
        Account aa = AccountController.getInstance().getValidAccount(hostPlugin);
        if (aa == null) {
            return false;
        }
        try {
            ((jd.plugins.hoster.EroProfileCom) hostPlugin).login(this.br, aa, force);
        } catch (final PluginException e) {
            aa.setValid(false);
            return false;
        }
        // Account is valid, let's just add it
        AccountController.getInstance().addAccount(hostPlugin, aa);
        return true;
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}