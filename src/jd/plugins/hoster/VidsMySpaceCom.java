//    jDownloader - Downloadmanager
//    Copyright (C) 2010  JD-Team support@jdownloader.org
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.
package jd.plugins.hoster;

import java.util.Map;

import org.appwork.utils.DebugMode;
import org.jdownloader.scripting.JavaScriptEngineFactory;

import jd.PluginWrapper;
import jd.http.URLConnectionAdapter;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.AccountRequiredException;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "vids.myspace.com" }, urls = { "https?://(?:www\\.)?(myspace\\.com/(([a-z0-9\\-_\\.]+/)?video/[a-z0-9\\-_]+/\\d+|[a-z0-9\\-_]+/music/song/[a-z0-9\\-_\\.]+)|mediaservices\\.myspace\\.com/services/media/embed\\.aspx/m=\\d+)" })
public class VidsMySpaceCom extends PluginForHost {
    private static final String SONGURL = "https?://(www\\.)?myspace\\.com/[a-z0-9\\-_]+/music/song/[a-z0-9\\-_\\.]+";

    @Override
    public void correctDownloadLink(final DownloadLink link) {
        // correction of old embded link format.
        String[] movuid = new Regex(link.getDownloadURL(), "(https?).+embed\\.aspx/m=(\\d+)").getRow(0);
        if (movuid != null && movuid.length == 2) {
            link.setUrlDownload(movuid[0] + "://myspace.com/video/" + movuid[1]);
        }
    }

    public VidsMySpaceCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.myspace.com/index.cfm?fuseaction=misc.terms";
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public AvailableStatus requestFileInformation(final DownloadLink link) throws Exception {
        this.setBrowserExclusive();
        br.setFollowRedirects(true);
        URLConnectionAdapter con = null;
        try {
            con = br.openGetConnection(link.getPluginPatternMatcher());
            // This usually only happens for embed links
            if (con.getResponseCode() == 404 || con.getResponseCode() == 500) {
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            br.followConnection();
        } finally {
            try {
                con.disconnect();
            } catch (Throwable e) {
            }
        }
        if (br.getURL().contains("myspace.com/error")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (!br.getURL().matches("https?://(www\\.)?(myspace\\.com/(([a-z0-9\\-_\\.]+/)?video/[a-z0-9\\-_]+/\\d+|[a-z0-9\\-_]+/music/song/[a-z0-9\\-_\\.]+)|mediaservices\\.myspace\\.com/services/media/embed\\.aspx/m=\\d+)")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = null;
        if (link.getDownloadURL().matches(SONGURL)) {
            filename = br.getRegex("<meta property=\"og:title\" content=\"([^\"]+)\"").getMatch(0);
            if (filename == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            link.setFinalFileName(Encoding.htmlDecode(filename.trim()) + ".m4a");
        } else {
            if (br.containsHTML("class=\"lock_16\"")) {
                link.getLinkStatus().setStatusText("+18 Videos are only downloadable for registered users");
                link.setName(new Regex(link.getDownloadURL(), "myspace\\.com/(.+)").getMatch(0));
                return AvailableStatus.TRUE;
            }
            filename = br.getRegex("<meta property=\"og:title\" content=\"([^\"]+) Video by[^\"]+\"").getMatch(0);
            if (filename == null) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            link.setFinalFileName(Encoding.htmlDecode(filename.trim()) + ".mp4");
        }
        return AvailableStatus.TRUE;
    }

    @SuppressWarnings("deprecation")
    @Override
    public void handleFree(final DownloadLink link) throws Exception {
        requestFileInformation(link);
        String dlurl = null;
        if (link.getDownloadURL().matches(SONGURL)) {
            dlurl = br.getRegex("data\\-stream\\-url=\"(rtmp[^<>\"]*?)\"").getMatch(0);
        } else {
            if (br.containsHTML("class=\"lock_16\"")) {
                /* +18 Videos are only downloadable for registered users */
                throw new AccountRequiredException();
            }
            dlurl = br.getRegex("\"(rtmp[^<>\"]+)\"").getMatch(0);
            if (dlurl == null) {
                if (!DebugMode.TRUE_IN_IDE_ELSE_FALSE) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.getHeaders().put("Accept", "*/*");
                br.getHeaders().put("X-Requested-With", "XMLHttpRequest");
                br.getHeaders().put("Origin", "https://myspace.com");
                br.getHeaders().put("Client", "TODO");
                br.getHeaders().put("Hash", "TODO");
                final String videoID = new Regex(link.getDownloadURL(), "(\\d+)").getMatch(0);
                if (videoID == null) {
                    throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                }
                br.clearCookies(br.getHost());
                // br.postPage("https://myspace.com/ajax/streamurl", "mediaType=video&mediaId=" + videoID);
                br.postPage("https://" + this.getHost() + "/ajax/videos/rightRail/render", "entityKey=video_" + videoID + "&mediaId=" + videoID);
                if (br.getHttpConnection().getResponseCode() == 404) {
                    throw new PluginException(LinkStatus.ERROR_FATAL);
                }
                final Map<String, Object> entries = JavaScriptEngineFactory.jsonToJavaMap(br.toString());
                final String html = (String) entries.get("html");
                dlurl = new Regex(html, "\"(rtmp[^<>\"]+)\"").getMatch(0);
            }
        }
        if (dlurl == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (dlurl.startsWith("rtmp")) {
            throw new PluginException(LinkStatus.ERROR_FATAL, "Unsupported streaming protocol");
        } else {
            dl = jd.plugins.BrowserAdapter.openDownload(br, link, dlurl, true, 0);
            if (!this.looksLikeDownloadableContent(dl.getConnection())) {
                dl.getConnection().disconnect();
                throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
            }
            dl.startDownload();
        }
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }
}