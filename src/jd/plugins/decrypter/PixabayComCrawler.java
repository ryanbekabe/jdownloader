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

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "pixabay.com" }, urls = { "https?://(?:www\\.)?pixabay\\.com/(?:videos|music)/[a-z0-9\\-]+-(\\d+)/?" })
public class PixabayComCrawler extends PluginForDecrypt {
    public PixabayComCrawler(PluginWrapper wrapper) {
        super(wrapper);
    }

    private static final String TYPE_VIDEO = "https?://(?:www\\.)?pixabay\\.com/videos/[a-z0-9\\-]+-(\\d+)/?";
    private static final String TYPE_MUSIC = "https?://(?:www\\.)?pixabay\\.com/music/[a-z0-9\\-]+-(\\d+)/?";

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString();
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (parameter.matches(TYPE_VIDEO)) {
            final String finallink = this.br.getRegex("<iframe[^>]*src=\"((?:https?:)?//player\\.vimeo\\.com/video/[^\"]+)\"").getMatch(0);
            if (finallink == null) {
                logger.info("Failed to find any downloadable content");
            } else {
                decryptedLinks.add(createDownloadlink(finallink));
            }
        } else {
            final String finallink = this.br.getRegex("<a href=\"(https?://[^\"]+)\"[^>]*class=\"audio-download download-button\"").getMatch(0);
            if (finallink == null) {
                logger.info("Failed to find any downloadable content");
            } else {
                final DownloadLink music = createDownloadlink(finallink);
                music.setAvailable(true);
                decryptedLinks.add(music);
            }
        }
        return decryptedLinks;
    }
}
