//    jDownloader - Downloadmanager
//    Copyright (C) 2013  JD-Team support@jdownloader.org
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

import jd.PluginWrapper;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.http.URLConnectionAdapter;
import jd.nutils.JDHash;
import jd.nutils.encoding.Encoding;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;
import jd.utils.locale.JDL;

@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "wat.tv" }, urls = { "http://(www\\.)?wat\\.tv/video/.*?\\.html" }, flags = { 0 })
public class WatTv extends PluginForHost {

    public WatTv(final PluginWrapper wrapper) {
        super(wrapper);
    }

    // private String getTS(long ts) {
    // int t = (int) Math.floor(ts / 1000);
    // return Integer.toString(t, 36);
    // }

    private static final boolean enable_hds_workaround = true;

    private String computeToken(String quality, final String videoID) {
        quality += videoID;
        // String salt = String.valueOf(Integer.toHexString(Integer.parseInt(ts, 36)));
        // while (salt.length() < 8) {
        // salt = "0" + salt;
        // }
        long timestamp = System.currentTimeMillis();
        try {
            final Browser br2 = br.cloneBrowser();
            br2.getPage("http://www.wat.tv/servertime?" + videoID);
            timestamp = Long.parseLong(br2.toString().split("\\|")[0]);
        } catch (final Throwable e) {
            logger.warning("Failed to get server timestamp");
        }
        final String timestamp_hex = Long.toHexString(timestamp);
        final String key = "9b673b13fa4682ed14c3cfa5af5310274b514c4133e9b3a81e6e3aba009l2564";
        return JDHash.getMD5(key + quality + timestamp_hex) + "/" + timestamp_hex;
    }

    @Override
    public String getAGBLink() {
        return "http://www.wat.tv/cgu";
    }

    @SuppressWarnings("deprecation")
    @Override
    public AvailableStatus requestFileInformation(final DownloadLink downloadLink) throws Exception {
        setBrowserExclusive();
        br.setCustomCharset("utf-8");
        br.setFollowRedirects(true);
        try {
            br.getPage(downloadLink.getDownloadURL());
        } catch (final BrowserException e) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (br.getURL().equals("http://www.wat.tv/") || br.containsHTML("<title> WAT TV, vidéos replay musique et films, votre média vidéo \\– Wat\\.tv </title>")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        String filename = br.getRegex("<meta property=\"og:title\" content=\"(.*?)\"").getMatch(0);
        if (filename == null || filename.equals("")) {
            filename = br.getRegex("<meta name=\"name\" content=\"(.*?)\"").getMatch(0);
        }
        if (filename == null || filename.equals("")) {
            throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        }
        if (filename.endsWith(" - ")) {
            filename = filename.replaceFirst(" \\- $", "");
        }
        filename = Encoding.htmlDecode(filename.trim());
        downloadLink.setName(filename + ".mp4");
        return AvailableStatus.TRUE;
    }

    public String getFinalLink() throws Exception {
        String videolink = null;
        // 8 digit id, located at the end of some fields
        String videoID = br.getRegex("<meta property=\"og:video(:secure_url)?\" content=\"[^\"]+(\\d{8})\">").getMatch(1);
        if (videoID == null) {
            videoID = br.getRegex("xtpage = \"[^;]+video\\-(\\d{8})\";").getMatch(0);
        }
        if (videoID == null) {
            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
        }
        if (enable_hds_workaround) {
            /* Thanks to: https://github.com/rg3/youtube-dl/blob/master/youtube_dl/extractor/wat.py */
            /* Android devices get http urls. This can also be used to avoid GEO-blocks! */
            videolink = "http://wat.tv/get/android5/" + videoID + ".mp4";
        } else {
            final Browser br2 = br.cloneBrowser();
            /* Contentv4 is used on the website but it returns information that we don't need */
            br2.getPage("http://www.wat.tv/interface/contentv3/" + videoID);
            String quality = "/web/", country = "DE";
            if (br2.containsHTML("\"hasHD\":true")) {
                quality = "/webhd/";
            }
            if (br2.containsHTML("\"geolock\":true")) {
                country = "FR";
            }
            final String token = computeToken(quality, videoID);
            final String getpage = "http://www.wat.tv/get" + quality + videoID + "?token=" + token + "&domain=www.wat.tv&refererURL=wat.tv&revision=04.00.759%0A&synd=0&helios=1&context=playerWat&pub=1&country=" + country + "&sitepage=WAT%2Fhumour%2Fp%2Fle-comte-de-bouderbala&lieu=wat&playerContext=CONTEXT_WAT&getURL=1&version=WIN%2017,0,0,169";
            br2.getPage(getpage);
            if (br2.containsHTML("No htmlCode read")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Video not available in your country!");
            }
            videolink = br2.toString();
            if (videolink == null || !videolink.startsWith("http") && !videolink.startsWith("rtmp")) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            videolink = Encoding.htmlDecode(videolink.trim());
            if (videolink.startsWith("http")) {
                final URLConnectionAdapter con = br2.openGetConnection(videolink);
                if (con.getResponseCode() == 404 || con.getResponseCode() == 403) {
                    throw new PluginException(LinkStatus.ERROR_FATAL, JDL.L("plugins.hoster.WatTv.CountryBlocked", "This video isn't available in your country!"));
                }
            }
        }
        return videolink;
    }

    @Override
    public int getMaxSimultanFreeDownloadNum() {
        return -1;
    }

    @Override
    public void handleFree(final DownloadLink downloadLink) throws Exception {
        requestFileInformation(downloadLink);
        String finallink = getFinalLink();
        if (finallink.startsWith("rtmp")) {
            /* Old */
            if (isStableEnviroment()) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "JD2 BETA needed!");
            }
            /**
             * NOT WORKING IN RTMPDUMP
             */
            final String nw = "rtmpdump";
            if (nw.equals("rtmpdump")) {
                throw new PluginException(LinkStatus.ERROR_FATAL, "Not supported yet!");
            }

            finallink = finallink.replaceAll("^.*?:", "rtmpe:");
            finallink = finallink.replaceAll("watestreaming/", "watestreaming/#");

            dl = new RTMPDownload(this, downloadLink, finallink);
            final jd.network.rtmp.url.RtmpUrlConnection rtmp = ((RTMPDownload) dl).getRtmpConnection();

            rtmp.setUrl(finallink.substring(0, finallink.indexOf("#")));
            rtmp.setPlayPath(finallink.substring(finallink.indexOf("#") + 1));
            rtmp.setApp(new Regex(finallink.substring(0, finallink.indexOf("#")), "[a-zA-Z]+://.*?/(.*?)$").getMatch(0));
            rtmp.setSwfVfy("http://www.wat.tv/images/v40/PlayerWat.swf");
            rtmp.setResume(true);

            ((RTMPDownload) dl).startDownload();
        } else {
            // dl = new HDSDownloader(downloadLink, br, finallink);
            // dl.startDownload();
            dl = jd.plugins.BrowserAdapter.openDownload(br, downloadLink, finallink, true, 0);
            if (dl.getConnection().getContentType().contains("html")) {
                throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
            }
            dl.startDownload();
        }
    }

    private boolean isStableEnviroment() {
        String prev = JDUtilities.getRevision();
        if (prev == null || prev.length() < 3) {
            prev = "0";
        } else {
            prev = prev.replaceAll(",|\\.", "");
        }
        final int rev = Integer.parseInt(prev);
        if (rev < 10000) {
            return true;
        }
        return false;
    }

    @Override
    public void reset() {
    }

    @Override
    public void resetDownloadlink(final DownloadLink link) {
    }

    @Override
    public void resetPluginGlobals() {
    }

}