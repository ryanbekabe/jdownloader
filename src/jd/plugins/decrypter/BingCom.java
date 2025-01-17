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
import java.util.LinkedHashMap;
import java.util.Random;

import jd.PluginWrapper;
import jd.config.SubConfiguration;
import jd.controlling.ProgressController;
import jd.nutils.encoding.Encoding;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "bing.com" }, urls = { "http://(www\\.)?bing\\.com/(videos/watch/video|watch/video)/.+" })
public class BingCom extends PluginForDecrypt {
    public BingCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String Q_BEST     = "Q_BEST";
    private static final String Q_LOW      = "Q_LOW";
    private static final String Q_SD       = "Q_SD";
    private static final String Q_HIGH     = "Q_HIGH";
    private static final String Q_HIGH_FLV = "Q_HIGH_FLV";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        // Prefer english language
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            final DownloadLink dl = this.createOfflinelink(parameter);
            decryptedLinks.add(dl);
            return decryptedLinks;
        }
        String externID = br.getRegex("class=\"vxp_relatedLink vxp_tl1\" href=\"(http://(www\\.)?metacafe\\.com/watch[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        externID = br.getRegex("data\\-externalUrl=\"(https?://(www\\.)?youtube\\.com/watch[^<>\"]*?)\"").getMatch(0);
        if (externID != null) {
            decryptedLinks.add(createDownloadlink(externID));
            return decryptedLinks;
        }
        final SubConfiguration cfg = SubConfiguration.getConfig(this.getHost());
        final LinkedHashMap<String, String> foundQualities = new LinkedHashMap<String, String>();
        final ArrayList<String[]> selectedQualities = new ArrayList<String[]>();
        // 1002 == 102
        final String[][] allQualities = { { "103", "high", ".mp4" }, { "1003", "high_flv", ".flv" }, { "102", "sd", ".mp4" }, { "101", "low", ".mp4" } };
        String filename = br.getRegex("property=\"og:title\" content=\"([^<>\"]*?)\"").getMatch(0);
        final String[][] qualities = br.getRegex("\\{formatCode: (\\d+), url: \\'(http[^<>\"]*?)\\'").getMatches();
        if (qualities == null || qualities.length == 0 || filename == null) {
            logger.warning("Decrypter broken for link: " + parameter);
            return null;
        }
        filename = Encoding.htmlDecode(filename.trim());
        for (final String[] quality : qualities) {
            final String qualityCode = quality[0];
            String directlink = quality[1];
            directlink = Encoding.unicodeDecode(directlink);
            foundQualities.put(qualityCode, directlink);
        }
        if (cfg.getBooleanProperty(Q_BEST, false)) {
            for (final String quality[] : allQualities) {
                final String qLink = foundQualities.get(quality[0]);
                if (qLink != null) {
                    selectedQualities.add(quality);
                    break;
                }
            }
        } else {
            boolean qlow = false;
            boolean qsd = false;
            boolean qhigh = false;
            boolean qhighflv = false;
            if (cfg.getBooleanProperty(Q_LOW, false)) {
                qlow = true;
            }
            if (cfg.getBooleanProperty(Q_SD, false)) {
                qsd = true;
            }
            if (cfg.getBooleanProperty(Q_HIGH, false)) {
                qhigh = true;
            }
            if (cfg.getBooleanProperty(Q_HIGH_FLV, false)) {
                qhighflv = true;
            }
            // None selected? Grab all!
            if (qlow == false && qsd == false && qhigh == false && qhighflv == false) {
                qlow = true;
                qsd = true;
                qhigh = true;
                qhighflv = true;
            }
            if (qlow) {
                selectedQualities.add(new String[] { "101", "low", ".mp4" });
            }
            if (qsd) {
                selectedQualities.add(new String[] { "102", "sd", ".mp4" });
            }
            if (qhigh) {
                selectedQualities.add(new String[] { "103", "high", ".mp4" });
            }
            if (qhighflv) {
                selectedQualities.add(new String[] { "1003", "high_flv", ".flv" });
            }
        }
        for (final String selectedQuality[] : selectedQualities) {
            final String qKey = selectedQuality[0];
            final String qName = selectedQuality[1];
            final String qExt = selectedQuality[2];
            final String qDirectlink = foundQualities.get(qKey);
            if (qDirectlink != null) {
                final String finalfilename = filename + "_" + qName + qExt;
                final DownloadLink dl = createDownloadlink("decrypted://bing.com/" + System.currentTimeMillis() + new Random().nextInt(1000000));
                dl.setProperty("directURL", qDirectlink);
                dl.setProperty("mainlink", parameter);
                dl.setProperty("directName", finalfilename);
                dl.setFinalFileName(finalfilename);
                decryptedLinks.add(dl);
            }
        }
        final FilePackage fp = FilePackage.getInstance();
        fp.setName(filename);
        fp.addLinks(decryptedLinks);
        return decryptedLinks;
    }
}
