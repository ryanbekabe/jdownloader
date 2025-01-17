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

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "shooshtime.com" }, urls = { "https?://(?:www\\.)?shooshtime\\.com/videos/\\d+/[a-z0-9\\-_]+/?" })
public class ShooshtimeComCrawler extends PluginForDecrypt {
    public ShooshtimeComCrawler(PluginWrapper wrapper) {
        super(wrapper);
    }

    public ArrayList<DownloadLink> decryptIt(CryptedLink param, ProgressController progress) throws Exception {
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String parameter = param.toString().replaceFirst("http://", "https://");
        br.setFollowRedirects(false);
        br.getPage(parameter);
        if (br.getHttpConnection().getResponseCode() == 404) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        final String redirect = br.getRedirectLocation();
        if (redirect != null && !this.canHandle(redirect)) {
            /* Advertising or externally hosted content. */
            decryptedLinks.add(createDownloadlink(redirect));
        } else {
            /* Selfhosted content -> Pass to hostplugin */
            /*
             * Small workaround so we do not have to create an extra plugin: Their URLs can contain underscores while mist of all
             * KernelVideoSharing sites only contain [a-z0-9\\-].
             */
            final String correctedURL = parameter.replace("_", "-");
            decryptedLinks.add(createDownloadlink(correctedURL));
        }
        return decryptedLinks;
    }
}
