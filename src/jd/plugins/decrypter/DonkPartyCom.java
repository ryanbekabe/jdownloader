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

import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.http.Browser;
import jd.plugins.DecrypterPlugin;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "donkparty.com" }, urls = { "https?://(?:www\\.)?donkparty\\.com/videos/([a-z0-9\\-_]+)_(\\d+)" })
public class DonkPartyCom extends PornEmbedParser {
    public DonkPartyCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.XXX };
    }
    /* DEV NOTES */
    /* Porn_plugin */

    protected boolean isOffline(final Browser br) {
        if (br.containsHTML("(?i)Media not found\\!<") || br.containsHTML("(?i)<title> free sex video \\- DonkParty</title>") || br.getHttpConnection().getResponseCode() == 404) {
            return true;
        } else {
            return false;
        }
    }

    protected boolean returnRedirectToUnsupportedLinkAsResult() {
        return true;
    }
}