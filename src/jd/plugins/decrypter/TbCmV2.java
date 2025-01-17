//    jDownloader - Downloadmanager
//    Copyright (C) 2009  JD-Team support@jdownloader.org
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
package jd.plugins.decrypter;

import java.awt.Dialog.ModalityType;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.controlling.linkcollector.LinkCollector;
import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledPackage;
import jd.controlling.packagecontroller.AbstractNodeVisitor;
import jd.http.Browser;
import jd.http.Browser.BrowserException;
import jd.nutils.encoding.Encoding;
import jd.nutils.encoding.HTMLEntities;
import jd.parser.Regex;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.DownloadLink.AvailableStatus;
import jd.plugins.FilePackage;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.components.PluginJSonUtils;
import jd.plugins.components.UserAgents;
import jd.plugins.components.UserAgents.BrowserName;
import jd.plugins.hoster.YoutubeDashV2;
import jd.utils.locale.JDL;

import org.appwork.uio.ConfirmDialogInterface;
import org.appwork.uio.UIOManager;
import org.appwork.utils.Application;
import org.appwork.utils.StringUtils;
import org.appwork.utils.encoding.Base64;
import org.appwork.utils.encoding.URLEncode;
import org.appwork.utils.net.httpconnection.HTTPProxy;
import org.appwork.utils.net.httpconnection.HTTPProxyStorable;
import org.appwork.utils.swing.dialog.ConfirmDialog;
import org.appwork.utils.swing.dialog.DialogCanceledException;
import org.appwork.utils.swing.dialog.DialogClosedException;
import org.jdownloader.plugins.components.youtube.ClipDataCache;
import org.jdownloader.plugins.components.youtube.Projection;
import org.jdownloader.plugins.components.youtube.StreamCollection;
import org.jdownloader.plugins.components.youtube.VariantIDStorable;
import org.jdownloader.plugins.components.youtube.YoutubeClipData;
import org.jdownloader.plugins.components.youtube.YoutubeConfig;
import org.jdownloader.plugins.components.youtube.YoutubeConfig.IfUrlisAPlaylistAction;
import org.jdownloader.plugins.components.youtube.YoutubeConfig.IfUrlisAVideoAndPlaylistAction;
import org.jdownloader.plugins.components.youtube.YoutubeHelper;
import org.jdownloader.plugins.components.youtube.YoutubeStreamData;
import org.jdownloader.plugins.components.youtube.configpanel.AbstractVariantWrapper;
import org.jdownloader.plugins.components.youtube.configpanel.YoutubeVariantCollection;
import org.jdownloader.plugins.components.youtube.itag.AudioBitrate;
import org.jdownloader.plugins.components.youtube.itag.AudioCodec;
import org.jdownloader.plugins.components.youtube.itag.VideoCodec;
import org.jdownloader.plugins.components.youtube.itag.VideoFrameRate;
import org.jdownloader.plugins.components.youtube.itag.VideoResolution;
import org.jdownloader.plugins.components.youtube.variants.AbstractVariant;
import org.jdownloader.plugins.components.youtube.variants.AudioInterface;
import org.jdownloader.plugins.components.youtube.variants.FileContainer;
import org.jdownloader.plugins.components.youtube.variants.ImageVariant;
import org.jdownloader.plugins.components.youtube.variants.SubtitleVariant;
import org.jdownloader.plugins.components.youtube.variants.VariantGroup;
import org.jdownloader.plugins.components.youtube.variants.VariantInfo;
import org.jdownloader.plugins.components.youtube.variants.VideoVariant;
import org.jdownloader.plugins.config.PluginJsonConfig;
import org.jdownloader.plugins.controller.LazyPlugin;
import org.jdownloader.scripting.JavaScriptEngineFactory;
import org.jdownloader.settings.staticreferences.CFG_YOUTUBE;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 3, names = { "youtube.com", "youtube.com", "youtube.com", "youtube.com" }, urls = { "https?://(?:www\\.)?youtube-nocookie\\.com/embed/.+", "https?://([a-z]+\\.)?yt\\.not\\.allowed/.+", "https?://([a-z]+\\.)?youtube\\.com/(embed/|.*?watch.*?v(%3D|=)|shorts/|view_play_list\\?p=|playlist\\?(p|list)=|.*?g/c/|.*?grid/user/|v/|user/|channel/|c/|course\\?list=)[%A-Za-z0-9\\-_]+(.*?index=\\d+)?(.*?page=\\d+)?(.*?list=[%A-Za-z0-9\\-_]+)?(\\#variant=\\S++)?|watch_videos\\?.*?video_ids=.+", "https?://youtube\\.googleapis\\.com/(v/|user/|channel/|c/)[%A-Za-z0-9\\-_]+(\\#variant=\\S+)?" })
public class TbCmV2 extends PluginForDecrypt {
    private static final int DDOS_WAIT_MAX        = Application.isJared(null) ? 1000 : 10;
    private static final int DDOS_INCREASE_FACTOR = 15;

    public TbCmV2(PluginWrapper wrapper) {
        super(wrapper);
    };

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.VIDEO_STREAMING };
    }

    /**
     * Returns host from provided String.f
     */
    static String getBase() {
        return "https://www.youtube.com";
    }

    /**
     * Returns a ListID from provided String.
     */
    private String getListIDByUrls(String originUrl) {
        // String list = null;
        // http://www.youtube.com/user/wirypodge#grid/user/41F2A8E7EBF86D7F
        // list = new Regex(originUrl, "(g/c/|grid/user/)([A-Za-z0-9\\-_]+)").getMatch(1);
        // /user/
        // http://www.youtube.com/user/Gronkh
        // if (list == null) list = new Regex(originUrl, "/user/([A-Za-z0-9\\-_]+)").getMatch(0);
        // play && course
        // http://www.youtube.com/playlist?list=PL375B54C39ED612FC
        return new Regex(originUrl, "list=([%A-Za-z0-9\\-_]+)").getMatch(0);
    }

    private String getVideoIDByUrl(String URL) {
        final String videoIDPattern = "([A-Za-z0-9\\-_]+)";
        String vuid = new Regex(URL, "v=" + videoIDPattern).getMatch(0);
        if (vuid == null) {
            vuid = new Regex(URL, "v/" + videoIDPattern).getMatch(0);
            if (vuid == null) {
                vuid = new Regex(URL, "shorts/" + videoIDPattern).getMatch(0);
                if (vuid == null) {
                    vuid = new Regex(URL, "embed/(?!videoseries\\?)" + videoIDPattern).getMatch(0);
                }
            }
        }
        return vuid;
    }

    private boolean linkCollectorContainsEntryByID(final String videoID) {
        final AtomicBoolean containsFlag = new AtomicBoolean(false);
        LinkCollector.getInstance().visitNodes(new AbstractNodeVisitor<CrawledLink, CrawledPackage>() {
            @Override
            public Boolean visitPackageNode(CrawledPackage pkg) {
                if (containsFlag.get()) {
                    return null;
                } else {
                    return Boolean.TRUE;
                }
            }

            @Override
            public Boolean visitChildrenNode(CrawledLink node) {
                if (containsFlag.get()) {
                    return null;
                } else {
                    if (StringUtils.equalsIgnoreCase(getHost(), node.getHost())) {
                        final DownloadLink downloadLink = node.getDownloadLink();
                        if (downloadLink != null && StringUtils.equals(videoID, downloadLink.getStringProperty(YoutubeHelper.YT_ID))) {
                            containsFlag.set(true);
                            return null;
                        }
                    }
                    return Boolean.TRUE;
                }
            }
        }, true);
        return containsFlag.get();
    }

    private YoutubeConfig           cfg;
    private static Object           DIALOGLOCK = new Object();
    private String                  videoID;
    private String                  watch_videos;
    private String                  playlistID;
    private String                  channelID;
    private String                  userID;
    private AbstractVariant         requestedVariant;
    private HashMap<String, Object> globalPropertiesForDownloadLink;
    private YoutubeHelper           helper;

    @Override
    protected DownloadLink createOfflinelink(String link, String filename, String message) {
        final DownloadLink ret = super.createOfflinelink(link, filename, message);
        logger.log(new Exception("Debug:" + filename + "|" + message));
        return ret;
    }

    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, final ProgressController progress) throws Exception {
        // nullify, for debugging purposes!
        videoID = null;
        watch_videos = null;
        playlistID = null;
        channelID = null;
        userID = null;
        globalPropertiesForDownloadLink = new HashMap<String, Object>();
        cfg = PluginJsonConfig.get(YoutubeConfig.class);
        final boolean isCrawlDupeCheckEnabled = cfg.isCrawlDupeCheckEnabled();
        String cryptedLink = param.getCryptedUrl();
        if (StringUtils.containsIgnoreCase(cryptedLink, "youtube-nocookie.com")) {
            final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
            cryptedLink = cryptedLink.replaceFirst("https?://(?:www\\.)?youtube-nocookie\\.com/embed/", "https://youtube.com/watch?v=");
            final DownloadLink link = createDownloadlink(cryptedLink);
            link.setContainerUrl(cryptedLink);
            ret.add(link);
            return ret;
        } else if (StringUtils.containsIgnoreCase(cryptedLink, "yt.not.allowed")) {
            final ArrayList<DownloadLink> ret = new ArrayList<DownloadLink>();
            if (cfg.isAndroidSupportEnabled()) {
                if (cryptedLink.matches("https?://[\\w\\.]*yt\\.not\\.allowed/[%a-z_A-Z0-9\\-]+")) {
                    cryptedLink = cryptedLink.replaceFirst("yt\\.not\\.allowed", "youtu.be");
                } else {
                    cryptedLink = cryptedLink.replaceFirst("yt\\.not\\.allowed", "youtube.com");
                }
                final DownloadLink link = createDownloadlink(cryptedLink);
                link.setContainerUrl(cryptedLink);
                ret.add(link);
            }
            return ret;
        }
        final String finalContainerURL = cryptedLink;
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>() {
            @Override
            public boolean add(DownloadLink e) {
                e.setContainerUrl(finalContainerURL);
                distribute(e);
                return super.add(e);
            }
        };
        br = new Browser();
        br.setFollowRedirects(true);
        br.setCookie("http://youtube.com", "PREF", "hl=en-GB");
        String cleanedurl = Encoding.urlDecode(cryptedLink, false);
        cleanedurl = cleanedurl.replace("youtube.jd", "youtube.com");
        String requestedVariantString = new Regex(cleanedurl, "\\#variant=(\\S*)").getMatch(0);
        if (StringUtils.isNotEmpty(requestedVariantString)) {
            requestedVariant = AbstractVariant.get(Base64.decodeToString(requestedVariantString));
        }
        cleanedurl = cleanedurl.replaceAll("\\#variant=\\S+", "");
        cleanedurl = cleanedurl.replace("/embed/", "/watch?v=");
        cleanedurl = cleanedurl.replace("/shorts/", "/watch?v=");
        videoID = getVideoIDByUrl(cleanedurl);
        // for watch_videos, found within youtube.com music
        watch_videos = new Regex(cleanedurl, "video_ids=([a-zA-Z0-9\\-_,]+)").getMatch(0);
        if (watch_videos != null) {
            // first uid in array is the video the user copy url on.
            videoID = new Regex(watch_videos, "([a-zA-Z0-9\\-_]+)").getMatch(0);
        }
        helper = new YoutubeHelper(br, getLogger());
        if (helper.isConsentCookieRequired()) {
            helper.setConsentCookie(br, null);
        }
        helper.login(getLogger(), false);
        /*
         * you can not use this with /c or /channel based urls, it will pick up false positives. see
         * https://www.youtube.com/channel/UCOSGEokQQcdAVFuL_Aq8dlg, it will find list=PLc-T0ryHZ5U_FtsfHQopuvQugBvRoVR3j which only
         * contains 27 videos not the entire channels 112
         */
        if (!cleanedurl.matches(".+youtube\\.com/(?:channel/|c/).+")) {
            playlistID = getListIDByUrls(cleanedurl);
        }
        String userChannel = new Regex(cleanedurl, "/c/([^/\\?]+)").getMatch(0);
        userID = new Regex(cleanedurl, "/user/([^/\\?]+)").getMatch(0);
        channelID = new Regex(cleanedurl, "/channel/([^/\\?]+)").getMatch(0);
        if (StringUtils.isEmpty(channelID) && StringUtils.isNotEmpty(userChannel)) {
            helper.getPage(br, "https://www.youtube.com/c/" + userChannel);
            channelID = br.getRegex("/channel/(UC[A-Za-z0-9\\-_]+)/videos").getMatch(0);
            if (StringUtils.isEmpty(channelID)) {
                // its within meta tags multiple times (ios/ipad/iphone) also
                helper.parserJson();
                channelID = getChannelID(helper, br);
            }
        }
        globalPropertiesForDownloadLink.put(YoutubeHelper.YT_PLAYLIST_ID, playlistID);
        globalPropertiesForDownloadLink.put(YoutubeHelper.YT_CHANNEL_ID, channelID);
        globalPropertiesForDownloadLink.put(YoutubeHelper.YT_USER_ID, userID);
        synchronized (DIALOGLOCK) {
            if (this.isAbort()) {
                logger.info("Thread Aborted!");
                return decryptedLinks;
            }
            {
                // Prevents accidental decrypting of entire Play-List or Channel-List or User-List.
                IfUrlisAPlaylistAction playListAction = cfg.getLinkIsPlaylistUrlAction();
                if ((StringUtils.isNotEmpty(playlistID) || StringUtils.isNotEmpty(channelID) || StringUtils.isNotEmpty(userID)) && StringUtils.isEmpty(videoID)) {
                    if (playListAction == IfUrlisAPlaylistAction.ASK) {
                        ConfirmDialog confirm = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, cleanedurl, JDL.L("plugins.host.youtube.isplaylist.question.message", "This link is a Play-List or Channel-List or User-List. What would you like to do?"), null, JDL.L("plugins.host.youtube.isplaylist.question.onlyplaylist", "Process Playlist?"), JDL.L("plugins.host.youtube.isvideoandplaylist.question.nothing", "Do Nothing?")) {
                            @Override
                            public ModalityType getModalityType() {
                                return ModalityType.MODELESS;
                            }

                            @Override
                            public boolean isRemoteAPIEnabled() {
                                return true;
                            }
                        };
                        try {
                            UIOManager.I().show(ConfirmDialogInterface.class, confirm).throwCloseExceptions();
                            playListAction = IfUrlisAPlaylistAction.PROCESS;
                        } catch (DialogCanceledException e) {
                            logger.log(e);
                            playListAction = IfUrlisAPlaylistAction.NOTHING;
                        } catch (DialogClosedException e) {
                            logger.log(e);
                            playListAction = IfUrlisAPlaylistAction.NOTHING;
                        }
                    }
                    logger.info("LinkIsPlaylistUrlAction:" + playListAction);
                    switch (playListAction) {
                    case PROCESS:
                        break;
                    case NOTHING:
                    default:
                        return decryptedLinks;
                    }
                }
            }
            {
                // Check if link contains a video and a playlist
                IfUrlisAVideoAndPlaylistAction PlaylistVideoAction = cfg.getLinkIsVideoAndPlaylistUrlAction();
                if ((StringUtils.isNotEmpty(playlistID) || StringUtils.isNotEmpty(watch_videos)) && StringUtils.isNotEmpty(videoID)) {
                    if (PlaylistVideoAction == IfUrlisAVideoAndPlaylistAction.ASK) {
                        ConfirmDialog confirm = new ConfirmDialog(UIOManager.LOGIC_COUNTDOWN, cleanedurl, JDL.L("plugins.host.youtube.isvideoandplaylist.question.message", "The Youtube link contains a video and a playlist. What do you want do download?"), null, JDL.L("plugins.host.youtube.isvideoandplaylist.question.onlyvideo", "Only video"), JDL.L("plugins.host.youtube.isvideoandplaylist.question.playlist", "Complete playlist")) {
                            @Override
                            public ModalityType getModalityType() {
                                return ModalityType.MODELESS;
                            }

                            @Override
                            public boolean isRemoteAPIEnabled() {
                                return true;
                            }
                        };
                        try {
                            UIOManager.I().show(ConfirmDialogInterface.class, confirm).throwCloseExceptions();
                            PlaylistVideoAction = IfUrlisAVideoAndPlaylistAction.VIDEO_ONLY;
                        } catch (DialogCanceledException e) {
                            logger.log(e);
                            PlaylistVideoAction = IfUrlisAVideoAndPlaylistAction.PLAYLIST_ONLY;
                        } catch (DialogClosedException e) {
                            logger.log(e);
                            PlaylistVideoAction = IfUrlisAVideoAndPlaylistAction.NOTHING;
                        }
                    }
                    logger.info("LinkIsVideoAndPlaylistUrlAction:" + PlaylistVideoAction);
                    switch (PlaylistVideoAction) {
                    case PLAYLIST_ONLY:
                        // videoID = null;
                        break;
                    case VIDEO_ONLY:
                        playlistID = null;
                        watch_videos = null;
                        break;
                    default:
                        return decryptedLinks;
                    }
                }
            }
        }
        final ArrayList<YoutubeClipData> videoIdsToAdd = new ArrayList<YoutubeClipData>();
        boolean reversePlaylistNumber = false;
        try {
            Boolean userWorkaround = null;
            Boolean channelWorkaround = null;
            if (StringUtils.isNotEmpty(userID) && StringUtils.isEmpty(playlistID)) {
                /*
                 * the user channel parser only parses 1050 videos. this workaround finds the user channel playlist and parses this playlist
                 * instead
                 */
                helper.getPage(br, "https://www.youtube.com/user/" + userID + "/featured");
                helper.parserJson();
                // channel title isn't user_name. user_name is /user/ reference. check logic in YoutubeHelper.extractData()!
                globalPropertiesForDownloadLink.put(YoutubeHelper.YT_CHANNEL_TITLE, extractWebsiteTitle(br));
                globalPropertiesForDownloadLink.put(YoutubeHelper.YT_USER_NAME, userID);
                // you can convert channelid UC[STATICHASH] (UserChanel) ? to UU[STATICHASH] (UsersUpload) which is covered below
                channelID = getChannelID(helper, br);
                if (channelID != null) {
                    globalPropertiesForDownloadLink.put(YoutubeHelper.YT_CHANNEL_ID, channelID);
                    playlistID = "UU" + channelID.substring(2);
                    userWorkaround = Boolean.valueOf(StringUtils.isNotEmpty(playlistID));
                }
            }
            if (StringUtils.isNotEmpty(channelID) && StringUtils.isEmpty(playlistID)) {
                /*
                 * you can not use this with /c or /channel based urls, it will pick up false positives. see
                 * https://www.youtube.com/channel/UCOSGEokQQcdAVFuL_Aq8dlg, it will find list=PLc-T0ryHZ5U_FtsfHQopuvQugBvRoVR3j which only
                 * contains 27 videos not the entire channels 112
                 */
                if (!cleanedurl.matches(".+youtube\\.com/(?:channel/|c/).+")) {
                    /*
                     * the user channel parser only parses 1050 videos. this workaround finds the user channel playlist and parses this
                     * playlist instead
                     */
                    helper.getPage(br, "https://www.youtube.com/channel/" + channelID);
                    playlistID = br.getRegex("list=([A-Za-z0-9\\-_]+)\"[^<>]+play-all-icon-btn").getMatch(0);
                }
                if (StringUtils.isEmpty(playlistID) && channelID.startsWith("UC")) {
                    // channel has no play all button.
                    // like https://www.youtube.com/channel/UCbmRs17gtQxFXQyvIo5k6Ag/feed
                    playlistID = "UU" + channelID.substring(2);
                }
                channelWorkaround = Boolean.valueOf(StringUtils.isNotEmpty(playlistID));
            }
            final ArrayList<YoutubeClipData> playlist = parsePlaylist(helper, videoID, playlistID, cleanedurl);
            if (playlist != null) {
                videoIdsToAdd.addAll(playlist);
            }
            if (videoIdsToAdd.size() == 0 && Boolean.TRUE.equals(channelWorkaround)) {
                videoIdsToAdd.addAll(parseChannelgrid(helper, channelID));
                Collections.reverse(videoIdsToAdd);
                reversePlaylistNumber = true;
            }
            if (videoIdsToAdd.size() == 0 && Boolean.TRUE.equals(userWorkaround)) {
                videoIdsToAdd.addAll(parseUsergrid(helper, userID));
                Collections.reverse(videoIdsToAdd);
                reversePlaylistNumber = true;
            }
            // some unknown playlist type?
            if (videoIdsToAdd.size() == 0 && StringUtils.isNotEmpty(playlistID)) {
                videoIdsToAdd.addAll(parseGeneric(helper, cleanedurl));
            }
            videoIdsToAdd.addAll(parseVideoIds(watch_videos));
            if (StringUtils.isNotEmpty(videoID)) {
                videoIdsToAdd.add(new org.jdownloader.plugins.components.youtube.YoutubeClipData(videoID));
            }
            if (videoIdsToAdd.size() == 0) {
                videoIdsToAdd.addAll(parseGeneric(helper, cleanedurl));
            }
            // /user/username/videos and /channel/[a-zA-Z0-9_-]+/videos are inverted (newest to oldest), we should always return oldest >
            // newest so playlist counter is correct.
            // userworkaround == true == newest to oldest
            // channelworkaround == true == newest to oldest.
        } catch (InterruptedException e) {
            logger.log(e);
            return decryptedLinks;
        }
        final Set<String> videoIDsdupeCheck = new HashSet<String>();
        for (YoutubeClipData vid : videoIdsToAdd) {
            if (this.isAbort()) {
                logger.info("Aborted!");
                return decryptedLinks;
            } else if (isCrawlDupeCheckEnabled && linkCollectorContainsEntryByID(vid.videoID)) {
                logger.info("CrawlDupeCheck skip:" + vid.videoID);
                continue;
            } else if (!videoIDsdupeCheck.add(vid.videoID)) {
                logger.info("Duplicated Video skip:" + vid.videoID);
                continue;
            }
            try {
                // make sure that we reload the video
                final boolean hasCache = ClipDataCache.hasCache(helper, vid.videoID);
                final YoutubeClipData old = vid;
                try {
                    vid = ClipDataCache.get(helper, vid.videoID);
                } catch (Exception e) {
                    logger.log(e);
                    if (hasCache) {
                        ClipDataCache.clearCache(vid.videoID);
                        vid = ClipDataCache.get(helper, vid.videoID);
                    } else {
                        throw e;
                    }
                }
                if (vid.playlistEntryNumber == -1 && old.playlistEntryNumber > 0) {
                    vid.playlistEntryNumber = reversePlaylistNumber ? (videoIdsToAdd.size() - old.playlistEntryNumber + 1) : old.playlistEntryNumber;
                } else if (vid.playlistEntryNumber == -1 && StringUtils.equals(videoID, vid.videoID)) {
                    final String index = new Regex(cleanedurl, "index=(\\d+)").getMatch(0);
                    if (index != null) {
                        vid.playlistEntryNumber = Integer.parseInt(index);
                    }
                }
            } catch (Exception e) {
                logger.log(e);
                String emsg = null;
                try {
                    emsg = e.getMessage().toString();
                } catch (NullPointerException npe) {
                    // e.message can be null...
                }
                if (emsg != null && StringUtils.isEmpty(vid.error)) {
                    vid.error = emsg;
                }
                if (vid.streams == null || StringUtils.isNotEmpty(vid.error)) {
                    decryptedLinks.add(createOfflinelink(YoutubeDashV2.generateContentURL(vid.videoID), "Error - " + vid.videoID + (vid.title != null ? " [" + vid.title + "]:" : "") + " " + vid.error, vid.error));
                    continue;
                }
            }
            if (vid.streams == null || StringUtils.isNotEmpty(vid.error)) {
                decryptedLinks.add(createOfflinelink(YoutubeDashV2.generateContentURL(vid.videoID), "Error - " + vid.videoID + (vid.title != null ? " [" + vid.title + "]:" : "") + " " + vid.error, vid.error));
                if (vid.streams == null) {
                    continue;
                }
            }
            final List<AbstractVariant> enabledVariants = new ArrayList<AbstractVariant>(AbstractVariant.listVariants());
            final HashSet<VariantGroup> enabledVariantGroups = new HashSet<VariantGroup>();
            final VideoResolution maxVideoResolution = CFG_YOUTUBE.CFG.getMaxVideoResolution();
            {
                // nest this, so we don't have variables table full of entries that get called only once
                final List<VariantIDStorable> disabled = CFG_YOUTUBE.CFG.getDisabledVariants();
                final HashSet<String> disabledIds = new HashSet<String>();
                if (disabled != null) {
                    for (VariantIDStorable v : disabled) {
                        disabledIds.add(v.createUniqueID());
                    }
                }
                final HashSet<AudioBitrate> disabledAudioBitrates = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedAudioBitrates());
                final HashSet<AudioCodec> disabledAudioCodecs = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedAudioCodecs());
                final HashSet<FileContainer> disabledFileContainers = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedFileContainers());
                final HashSet<VariantGroup> disabledGroups = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedGroups());
                final HashSet<Projection> disabledProjections = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedProjections());
                final HashSet<VideoResolution> disabledResolutions = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedResolutions());
                final HashSet<VideoCodec> disabledVideoCodecs = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedVideoCodecs());
                final HashSet<VideoFrameRate> disabledFramerates = createHashSet(CFG_YOUTUBE.CFG.getBlacklistedVideoFramerates());
                for (final Iterator<AbstractVariant> it = enabledVariants.iterator(); it.hasNext();) {
                    AbstractVariant cur = it.next();
                    if (disabledGroups.contains(cur.getGroup())) {
                        it.remove();
                        continue;
                    }
                    if (disabledFileContainers.contains(cur.getContainer())) {
                        it.remove();
                        continue;
                    }
                    if (cur instanceof AudioInterface) {
                        if (disabledAudioBitrates.contains(((AudioInterface) cur).getAudioBitrate())) {
                            it.remove();
                            continue;
                        }
                        if (disabledAudioCodecs.contains(((AudioInterface) cur).getAudioCodec())) {
                            it.remove();
                            continue;
                        }
                    }
                    if (cur instanceof VideoVariant) {
                        if (disabledVideoCodecs.contains(((VideoVariant) cur).getVideoCodec())) {
                            it.remove();
                            continue;
                        }
                        if (disabledResolutions.contains(((VideoVariant) cur).getVideoResolution())) {
                            it.remove();
                            continue;
                        }
                        if (disabledFramerates.contains(((VideoVariant) cur).getiTagVideo().getVideoFrameRate())) {
                            it.remove();
                            continue;
                        }
                        if (disabledProjections.contains(((VideoVariant) cur).getProjection())) {
                            it.remove();
                            continue;
                        }
                    }
                    if (cur instanceof ImageVariant) {
                        if (disabledResolutions.contains(VideoResolution.getByHeight(((ImageVariant) cur).getHeight()))) {
                            it.remove();
                            continue;
                        }
                    }
                    if (disabledIds.contains(new AbstractVariantWrapper(cur).getVariableIDStorable().createUniqueID())) {
                        it.remove();
                        continue;
                    }
                    enabledVariantGroups.add(cur.getGroup());
                }
            }
            // write all available variants to groups and allVariants
            List<VariantInfo> foundVariants = vid.findVariants();
            VideoVariant bestVideoResolution = null;
            {
                final Iterator<VariantInfo> it = foundVariants.iterator();
                while (it.hasNext()) {
                    final VariantInfo vi = it.next();
                    if (vi.getVariant() instanceof VideoVariant) {
                        final VideoVariant videoVariant = (VideoVariant) vi.getVariant();
                        if (bestVideoResolution == null || bestVideoResolution.getVideoHeight() < videoVariant.getVideoHeight()) {
                            bestVideoResolution = videoVariant;
                        }
                        if (videoVariant.getVideoHeight() > maxVideoResolution.getHeight()) {
                            it.remove();
                        }
                    }
                }
            }
            vid.bestVideoItag = bestVideoResolution;
            List<VariantInfo> subtitles = enabledVariantGroups.contains(VariantGroup.SUBTITLES) ? vid.findSubtitleVariants() : new ArrayList<VariantInfo>();
            ArrayList<VariantInfo> descriptions = enabledVariantGroups.contains(VariantGroup.DESCRIPTION) ? vid.findDescriptionVariant() : new ArrayList<VariantInfo>();
            if (subtitles != null) {
                foundVariants.addAll(subtitles);
            }
            if (descriptions != null) {
                foundVariants.addAll(descriptions);
            }
            List<YoutubeVariantCollection> links = YoutubeVariantCollection.load();
            if (requestedVariant != null) {
                // create a dummy collection
                links = new ArrayList<YoutubeVariantCollection>();
                ArrayList<VariantIDStorable> varList = new ArrayList<VariantIDStorable>();
                varList.add(new VariantIDStorable(requestedVariant));
                links.add(new YoutubeVariantCollection("Dummy", varList));
            }
            final HashMap<String, AbstractVariant> allowedVariantsMap = new HashMap<String, AbstractVariant>();
            for (AbstractVariant v : enabledVariants) {
                final VariantIDStorable storable = new VariantIDStorable(v);
                allowedVariantsMap.put(storable.createUniqueID(), v);
            }
            final HashMap<VariantInfo, String[]> foundVariableMap = new HashMap<VariantInfo, String[]>();
            for (VariantInfo v : foundVariants) {
                final VariantIDStorable storable = new VariantIDStorable(v.getVariant());
                foundVariableMap.put(v, new String[] { storable.createUniqueID(), storable.createGroupingID(), storable.getContainer() });
            }
            if (CFG_YOUTUBE.CFG.isCollectionMergingEnabled()) {
                for (YoutubeVariantCollection l : links) {
                    if (!l.isEnabled()) {
                        continue;
                    }
                    final ArrayList<VariantInfo> linkVariants = new ArrayList<VariantInfo>();
                    final ArrayList<VariantInfo> cutLinkVariantsDropdown = new ArrayList<VariantInfo>();
                    final HashSet<String> customAlternateSet = l.createUniqueIDSetForDropDownList();
                    if (customAlternateSet.size() > 0) {
                        for (Entry<VariantInfo, String[]> foundVariant : foundVariableMap.entrySet()) {
                            final String uId = foundVariant.getValue()[0];
                            if (customAlternateSet.contains(uId)) {
                                if (allowedVariantsMap.containsKey(uId)) {
                                    final VariantInfo variant = foundVariant.getKey();
                                    cutLinkVariantsDropdown.add(variant);
                                    helper.extendedDataLoading(variant, foundVariants);
                                }
                            }
                        }
                    }
                    if (StringUtils.isNotEmpty(l.getGroupingID())) {
                        final String groupingID = l.getGroupingID();
                        for (Entry<VariantInfo, String[]> foundVariant : foundVariableMap.entrySet()) {
                            final String gId = foundVariant.getValue()[1];
                            final String cId = foundVariant.getValue()[2];
                            if (StringUtils.equals(groupingID, gId) || StringUtils.equals(groupingID, cId)) {
                                final String uId = foundVariant.getValue()[0];
                                if (allowedVariantsMap.containsKey(uId)) {
                                    final VariantInfo variant = foundVariant.getKey();
                                    linkVariants.add(variant);
                                    helper.extendedDataLoading(variant, foundVariants);
                                }
                            }
                        }
                    } else if (l.getVariants() != null && l.getVariants().size() > 0) {
                        HashSet<String> idSet = l.createUniqueIDSet();
                        for (Entry<VariantInfo, String[]> foundVariant : foundVariableMap.entrySet()) {
                            final String uId = foundVariant.getValue()[0];
                            if (idSet.contains(uId)) {
                                if (allowedVariantsMap.containsKey(uId)) {
                                    final VariantInfo variant = foundVariant.getKey();
                                    linkVariants.add(variant);
                                    helper.extendedDataLoading(variant, foundVariants);
                                }
                            }
                        }
                    } else {
                        continue;
                    }
                    Collections.sort(cutLinkVariantsDropdown, new Comparator<VariantInfo>() {
                        @Override
                        public int compare(VariantInfo o1, VariantInfo o2) {
                            return o2.compareTo(o1);
                        }
                    });
                    Collections.sort(linkVariants, new Comparator<VariantInfo>() {
                        @Override
                        public int compare(VariantInfo o1, VariantInfo o2) {
                            return o2.compareTo(o1);
                        }
                    });
                    // remove dupes
                    VariantInfo last = null;
                    for (final Iterator<VariantInfo> it = linkVariants.iterator(); it.hasNext();) {
                        VariantInfo cur = it.next();
                        if (last != null) {
                            if (StringUtils.equals(cur.getVariant().getTypeId(), last.getVariant().getTypeId())) {
                                it.remove();
                                continue;
                            }
                        }
                        last = cur;
                    }
                    last = null;
                    for (final Iterator<VariantInfo> it = cutLinkVariantsDropdown.iterator(); it.hasNext();) {
                        VariantInfo cur = it.next();
                        if (last != null) {
                            if (StringUtils.equals(cur.getVariant().getTypeId(), last.getVariant().getTypeId())) {
                                it.remove();
                                continue;
                            }
                        }
                        last = cur;
                    }
                    // for (final Iterator<VariantInfo> it = linkVariants.iterator(); it.hasNext();) {
                    // VariantInfo cur = it.next();
                    // System.out.println(cur.getVariant().getBaseVariant() + "\t" + cur.getVariant().getQualityRating());
                    // }
                    if (linkVariants.size() > 0) {
                        DownloadLink lnk = createLink(l, linkVariants.get(0), cutLinkVariantsDropdown.size() > 0 ? cutLinkVariantsDropdown : linkVariants);
                        decryptedLinks.add(lnk);
                        if (linkVariants.get(0).getVariant().getGroup() == VariantGroup.SUBTITLES) {
                            ArrayList<String> extras = CFG_YOUTUBE.CFG.getExtraSubtitles();
                            if (extras != null) {
                                for (String s : extras) {
                                    if (s != null) {
                                        for (VariantInfo vi : linkVariants) {
                                            if (vi.getVariant() instanceof SubtitleVariant) {
                                                if ("*".equals(s)) {
                                                    lnk = createLink(l, vi, cutLinkVariantsDropdown.size() > 0 ? cutLinkVariantsDropdown : linkVariants);
                                                    decryptedLinks.add(lnk);
                                                } else if (StringUtils.equalsIgnoreCase(((SubtitleVariant) vi.getVariant()).getGenericInfo().getLanguage(), s)) {
                                                    lnk = createLink(l, vi, cutLinkVariantsDropdown.size() > 0 ? cutLinkVariantsDropdown : linkVariants);
                                                    decryptedLinks.add(lnk);
                                                    break;
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                ArrayList<VariantInfo> linkVariants = new ArrayList<VariantInfo>();
                for (Entry<VariantInfo, String[]> foundVariant : foundVariableMap.entrySet()) {
                    final String uId = foundVariant.getValue()[0];
                    if (allowedVariantsMap.containsKey(uId)) {
                        final VariantInfo variant = foundVariant.getKey();
                        linkVariants.add(variant);
                        helper.extendedDataLoading(variant, foundVariants);
                    }
                }
                Collections.sort(linkVariants, new Comparator<VariantInfo>() {
                    @Override
                    public int compare(VariantInfo o1, VariantInfo o2) {
                        return o2.compareTo(o1);
                    }
                });
                // remove dupes
                // System.out.println("Link " + l.getName());
                VariantInfo last = null;
                for (final Iterator<VariantInfo> it = linkVariants.iterator(); it.hasNext();) {
                    VariantInfo cur = it.next();
                    if (last != null) {
                        if (StringUtils.equals(cur.getVariant().getTypeId(), last.getVariant().getTypeId())) {
                            it.remove();
                            continue;
                        }
                    }
                    last = cur;
                }
                for (VariantInfo vi : linkVariants) {
                    ArrayList<VariantInfo> lst = new ArrayList<VariantInfo>();
                    lst.add(vi);
                    DownloadLink lnk = createLink(new YoutubeVariantCollection(), vi, lst);
                    decryptedLinks.add(lnk);
                }
            }
        }
        return decryptedLinks;
    }

    private String getChannelID(YoutubeHelper helper, Browser br) {
        String channelID = helper != null ? helper.getChannelIdFromMaps() : null;
        if (channelID == null) {
            channelID = br.getRegex("<meta itemprop=\"channelId\" content=\"(UC[A-Za-z0-9\\-_]+)\"").getMatch(0);
            if (channelID == null) {
                channelID = br.getRegex("yt\\.setConfig\\(\\s*'CHANNEL_ID'\\s*,\\s*\"(UC[A-Za-z0-9\\-_]+)\"").getMatch(0);
                if (channelID == null) {
                    channelID = br.getRegex("rssURL\"\\s*:\\s*\"https?://[^\"]*channel_ID=(UC[A-Za-z0-9\\-_]+)\"").getMatch(0);
                }
            }
        }
        return channelID;
    }

    private <T> HashSet<T> createHashSet(List<T> list) {
        HashSet<T> ret = new HashSet<T>();
        if (list != null) {
            ret.addAll(list);
        }
        return ret;
    }

    private Collection<? extends YoutubeClipData> parseGeneric(YoutubeHelper helper, final String cryptedUrl) throws Exception {
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        if (StringUtils.isNotEmpty(cryptedUrl)) {
            int page = 1;
            int counter = 1;
            while (true) {
                if (this.isAbort()) {
                    throw new InterruptedException();
                }
                // br.getHeaders().put("Cookie", "");
                helper.getPage(br, cryptedUrl);
                checkErrors(br);
                String[] videos = br.getRegex("data\\-video\\-id=\"([^\"]+)").getColumn(0);
                if (videos != null) {
                    for (String id : videos) {
                        ret.add(new YoutubeClipData(id, counter++));
                    }
                }
                if (ret.size() == 0) {
                    videos = br.getRegex("href=\"(/watch\\?v=[A-Za-z0-9\\-_]+)\\&amp;list=[A-Z0-9]+").getColumn(0);
                    if (videos != null) {
                        for (String relativeUrl : videos) {
                            final String id = getVideoIDByUrl(relativeUrl);
                            ret.add(new YoutubeClipData(id, counter++));
                        }
                    }
                }
                break;
                // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
                // String nextPage = br.getRegex("<a href=\"/playlist\\?list=" + playlistID +
                // "\\&amp;page=(\\d+)\"[^\r\n]+>Next").getMatch(0);
                // if (nextPage != null) {
                // page = Integer.parseInt(nextPage);
                // // anti ddos
                // Thread.sleep(500);
                // } else {
                // break;
                // }
            }
        }
        logger.info("parseGeneric method returns: " + ret.size() + " VideoID's!");
        return ret;
    }

    private DownloadLink createLink(YoutubeVariantCollection l, VariantInfo variantInfo, List<VariantInfo> alternatives) {
        try {
            YoutubeClipData clip = null;
            if (clip == null && variantInfo.getVideoStreams() != null) {
                clip = variantInfo.getVideoStreams().get(0).getClip();
            }
            if (clip == null && variantInfo.getAudioStreams() != null) {
                clip = variantInfo.getAudioStreams().get(0).getClip();
            }
            if (clip == null && variantInfo.getDataStreams() != null) {
                clip = variantInfo.getDataStreams().get(0).getClip();
            }
            boolean hasVariants = false;
            ArrayList<String> altIds = new ArrayList<String>();
            if (alternatives != null) {
                for (VariantInfo vi : alternatives) {
                    if (!StringUtils.equals(variantInfo.getVariant()._getUniqueId(), vi.getVariant()._getUniqueId())) {
                        hasVariants = true;
                    }
                    altIds.add(vi.getVariant().getStorableString());
                }
            }
            final String linkID = YoutubeHelper.createLinkID(clip.videoID, variantInfo.getVariant());
            final DownloadLink ret = createDownloadlink(linkID);
            final YoutubeHelper helper = new YoutubeHelper(br, getLogger());
            ClipDataCache.referenceLink(helper, ret, clip);
            // thislink.setAvailable(true);
            if (cfg.isSetCustomUrlEnabled()) {
                ret.setCustomURL(getBase() + "/watch?v=" + clip.videoID);
            }
            ret.setContentUrl(getBase() + "/watch?v=" + clip.videoID + "#variant=" + Encoding.urlEncode(Base64.encode(variantInfo.getVariant().getStorableString())));
            // thislink.setProperty(key, value)
            ret.setProperty(YoutubeHelper.YT_ID, clip.videoID);
            ret.setProperty(YoutubeHelper.YT_COLLECTION, l.getName());
            for (Entry<String, Object> es : globalPropertiesForDownloadLink.entrySet()) {
                if (es.getKey() != null && !ret.hasProperty(es.getKey())) {
                    ret.setProperty(es.getKey(), es.getValue());
                }
            }
            clip.copyToDownloadLink(ret);
            // thislink.getTempProperties().setProperty(YoutubeHelper.YT_VARIANT_INFO, variantInfo);
            ret.setVariantSupport(hasVariants);
            ret.setProperty(YoutubeHelper.YT_VARIANTS, altIds);
            // Object cache = downloadLink.getTempProperties().getProperty(YoutubeHelper.YT_VARIANTS, null);
            // thislink.setProperty(YoutubeHelper.YT_VARIANT, variantInfo.getVariant()._getUniqueId());
            YoutubeHelper.writeVariantToDownloadLink(ret, variantInfo.getVariant());
            // variantInfo.fillExtraProperties(thislink, alternatives);
            String filename = helper.createFilename(ret);
            ret.setFinalFileName(filename);
            ret.setLinkID(linkID);
            FilePackage fp = FilePackage.getInstance();
            final String fpName = helper.replaceVariables(ret, helper.getConfig().getPackagePattern());
            // req otherwise returned "" value = 'various', regardless of user settings for various!
            if (StringUtils.isNotEmpty(fpName)) {
                fp.setName(fpName);
                // let the packagizer merge several packages that have the same name
                fp.setAllowMerge(true);
                fp.add(ret);
            }
            long estimatedFileSize = 0;
            final AbstractVariant variant = variantInfo.getVariant();
            switch (variant.getType()) {
            case VIDEO:
            case DASH_AUDIO:
            case DASH_VIDEO:
                final StreamCollection audioStreams = clip.getStreams(variant.getBaseVariant().getiTagAudio());
                if (audioStreams != null && audioStreams.size() > 0) {
                    for (YoutubeStreamData stream : audioStreams) {
                        if (stream.getContentLength() > 0) {
                            estimatedFileSize += stream.getContentLength();
                            break;
                        } else if (stream.estimatedContentLength() > 0) {
                            estimatedFileSize += stream.estimatedContentLength();
                            break;
                        }
                    }
                }
                final StreamCollection videoStreams = clip.getStreams(variant.getBaseVariant().getiTagVideo());
                if (videoStreams != null && videoStreams.size() > 0) {
                    for (YoutubeStreamData stream : videoStreams) {
                        if (stream.getContentLength() > 0) {
                            estimatedFileSize += stream.getContentLength();
                            break;
                        } else if (stream.estimatedContentLength() > 0) {
                            estimatedFileSize += stream.estimatedContentLength();
                            break;
                        }
                    }
                }
                break;
            case IMAGE:
                final StreamCollection dataStreams = clip.getStreams(variant.getiTagData());
                if (dataStreams != null && dataStreams.size() > 0) {
                    for (YoutubeStreamData stream : dataStreams) {
                        if (stream.getContentLength() > 0) {
                            estimatedFileSize += stream.getContentLength();
                            break;
                        } else if (stream.estimatedContentLength() > 0) {
                            estimatedFileSize += stream.estimatedContentLength();
                            break;
                        }
                    }
                }
                break;
            default:
                break;
            }
            if (estimatedFileSize > 0) {
                ret.setDownloadSize(estimatedFileSize);
            }
            ret.setAvailableStatus(AvailableStatus.TRUE);
            return ret;
        } catch (Exception e) {
            getLogger().log(e);
            return null;
        }
    }

    @Override
    public void setBrowser(Browser brr) {
        if (CFG_YOUTUBE.CFG.isProxyEnabled()) {
            final HTTPProxyStorable proxy = CFG_YOUTUBE.CFG.getProxy();
            if (proxy != null) {
                HTTPProxy prxy = HTTPProxy.getHTTPProxy(proxy);
                if (prxy != null) {
                    this.br.setProxy(prxy);
                } else {
                }
                return;
            }
        }
        super.setBrowser(brr);
    }

    private ArrayList<YoutubeClipData> parseListedPlaylist(YoutubeHelper helper, final Browser br, final String videoID, final String playlistID, final String referenceUrl) throws Exception {
        final ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        // user list it's not a playlist.... just a channel decryption. this can return incorrect information.
        final String playListTitle = extractWebsiteTitle(br);
        globalPropertiesForDownloadLink.put(YoutubeHelper.YT_PLAYLIST_TITLE, playListTitle);
        helper.parserJson();
        boolean isJson = false;
        Browser pbr = br.cloneBrowser();
        int counter = 1;
        int round = 0;
        String PAGE_CL = br.getRegex("'PAGE_CL': (\\d+)").getMatch(0);
        String PAGE_BUILD_LABEL = br.getRegex("'PAGE_BUILD_LABEL': \"(.*?)\"").getMatch(0);
        String VARIANTS_CHECKSUM = br.getRegex("'VARIANTS_CHECKSUM': \"(.*?)\"").getMatch(0);
        String INNERTUBE_CONTEXT_CLIENT_VERSION = br.getRegex("INNERTUBE_CONTEXT_CLIENT_VERSION: \"(.*?)\"").getMatch(0);
        String INNERTUBE_CONTEXT_CLIENT_NAME = br.getRegex("INNERTUBE_CONTEXT_CLIENT_NAME: \"(.*?)\"").getMatch(0);
        final Set<String> playListDupes = new HashSet<String>();
        while (true) {
            if (this.isAbort()) {
                throw new InterruptedException();
            }
            String jsonPage = null, nextPage = null;
            checkErrors(pbr);
            // this will speed up searches. we know this wont be present..
            final String[] videos = round > 0 && isJson ? null : pbr.getRegex("href=(\"|')(/watch\\?v=[A-Za-z0-9\\-_]+.*?)\\1").getColumn(1);
            int before = playListDupes.size();
            if (videos != null && videos.length > 0) {
                for (String relativeUrl : videos) {
                    if (relativeUrl.contains("list=" + playlistID)) {
                        final String id = getVideoIDByUrl(relativeUrl);
                        playListDupes.add(id);
                        ret.add(new YoutubeClipData(id, counter++));
                    }
                }
                jsonPage = pbr.getRegex("/browse_ajax\\?action_continuation=\\d+&amp;continuation=[a-zA-Z0-9%]+").getMatch(-1);
                nextPage = pbr.getRegex("<a href=(\"|')(/playlist\\?list=" + playlistID + "\\&amp;page=\\d+)\\1[^\r\n]+>Next").getMatch(1);
            } else {
                isJson = true;
                if (round == 0) {
                    final Map<String, Object> ytInitialData = helper.getYtInitialData();
                    if (ytInitialData != null) {
                        final List<Object> pl = (List<Object>) JavaScriptEngineFactory.walkJson(ytInitialData, "contents/twoColumnBrowseResultsRenderer/tabs/{}/tabRenderer/content/sectionListRenderer/contents/{}/itemSectionRenderer/contents/{}/playlistVideoListRenderer/contents");
                        if (pl != null) {
                            for (final Object p : pl) {
                                final Map<String, Object> vid = (Map<String, Object>) p;
                                final String id = (String) JavaScriptEngineFactory.walkJson(vid, "playlistVideoRenderer/videoId");
                                if (id != null) {
                                    playListDupes.add(id);
                                    ret.add(new YoutubeClipData(id, counter++));
                                }
                            }
                            // continuation
                            final Map<String, Object> c = (Map<String, Object>) JavaScriptEngineFactory.walkJson(ytInitialData, "contents/twoColumnBrowseResultsRenderer/tabs/{}/tabRenderer/content/sectionListRenderer/contents/{}/itemSectionRenderer/contents/{}/playlistVideoListRenderer/continuations/{0}/nextContinuationData");
                            if (c != null) {
                                final String ctoken = (String) c.get("continuation");
                                final String itct = (String) c.get("clickTrackingParams");
                                if (ctoken != null && itct != null) {
                                    jsonPage = "/browse_ajax?ctoken=" + Encoding.urlEncode(ctoken) + "&itct=" + Encoding.urlEncode(itct);
                                }
                            }
                            if (helper.getYtCfgSet() != null) {
                                if (PAGE_CL == null && helper.getYtCfgSet().containsKey("PAGE_CL")) {
                                    PAGE_CL = String.valueOf(helper.getYtCfgSet().get("PAGE_CL"));
                                }
                                if (PAGE_BUILD_LABEL == null && helper.getYtCfgSet().containsKey("PAGE_BUILD_LABEL")) {
                                    PAGE_BUILD_LABEL = String.valueOf(helper.getYtCfgSet().get("PAGE_BUILD_LABEL"));
                                }
                                if (VARIANTS_CHECKSUM == null && helper.getYtCfgSet().containsKey("VARIANTS_CHECKSUM")) {
                                    VARIANTS_CHECKSUM = String.valueOf(helper.getYtCfgSet().get("VARIANTS_CHECKSUM"));
                                }
                                if (INNERTUBE_CONTEXT_CLIENT_VERSION == null && helper.getYtCfgSet().containsKey("INNERTUBE_CONTEXT_CLIENT_VERSION")) {
                                    INNERTUBE_CONTEXT_CLIENT_VERSION = String.valueOf(helper.getYtCfgSet().get("INNERTUBE_CONTEXT_CLIENT_VERSION"));
                                }
                                if (INNERTUBE_CONTEXT_CLIENT_NAME == null && helper.getYtCfgSet().containsKey("INNERTUBE_CONTEXT_CLIENT_NAME")) {
                                    INNERTUBE_CONTEXT_CLIENT_NAME = String.valueOf(helper.getYtCfgSet().get("INNERTUBE_CONTEXT_CLIENT_NAME"));
                                }
                            }
                        }
                    }
                } else {
                    // secondary pages are pure json
                    final Object object = JavaScriptEngineFactory.jsonToJavaObject(pbr.toString());
                    final Map<String, Object> map;
                    if (object instanceof Map) {
                        map = (Map<String, Object>) object;
                    } else if (object instanceof List) {
                        final List<Object> list = (List<Object>) object;
                        Map<String, Object> found = null;
                        for (final Object entry : list) {
                            if (entry instanceof Map && ((Map) entry).containsKey("response")) {
                                found = (Map<String, Object>) entry;
                                break;
                            }
                        }
                        if (found != null) {
                            map = found;
                        } else {
                            throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                        }
                    } else {
                        throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFECT);
                    }
                    if (map != null) {
                        final List<Object> pl = (List<Object>) JavaScriptEngineFactory.walkJson(map, "response/continuationContents/playlistVideoListContinuation/contents");
                        if (pl != null) {
                            for (final Object p : pl) {
                                final Map<String, Object> vid = (Map<String, Object>) p;
                                final String id = (String) JavaScriptEngineFactory.walkJson(vid, "playlistVideoRenderer/videoId");
                                if (id != null) {
                                    playListDupes.add(id);
                                    ret.add(new YoutubeClipData(id, counter++));
                                }
                            }
                            // continuation
                            final String continuation = (String) JavaScriptEngineFactory.walkJson(map, "response/continuationContents/playlistVideoListContinuation/continuations/{}/nextContinuationData/continuation");
                            if (continuation != null) {
                                final String clickTrackingParams = (String) JavaScriptEngineFactory.walkJson(map, "response/continuationContents/playlistVideoListContinuation/continuations/{}/nextContinuationData/clickTrackingParams");
                                if (clickTrackingParams != null) {
                                    jsonPage = "/browse_ajax?ctoken=" + URLEncode.encodeURIComponent(continuation) + "&itct=" + URLEncode.encodeURIComponent(clickTrackingParams);
                                }
                            }
                            if (jsonPage == null) {
                                final String url = (String) JavaScriptEngineFactory.walkJson(map, "endpoint/urlEndpoint/url");
                                if (url != null) {
                                    jsonPage = url;
                                }
                            }
                        }
                    }
                }
            }
            if (playListDupes.size() == before) {
                logger.info("no new video found, abort");
                // no videos in the last round. we are probably done here
                break;
            }
            // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
            if (jsonPage != null) {
                jsonPage = HTMLEntities.unhtmlentities(jsonPage);
                pbr = br.cloneBrowser();
                if (PAGE_CL != null) {
                    pbr.getHeaders().put("X-YouTube-Page-CL", PAGE_CL);
                }
                if (PAGE_BUILD_LABEL != null) {
                    pbr.getHeaders().put("X-YouTube-Page-Label", PAGE_BUILD_LABEL);
                }
                if (VARIANTS_CHECKSUM != null) {
                    pbr.getHeaders().put("X-YouTube-Variants-Checksum", VARIANTS_CHECKSUM);
                }
                if (INNERTUBE_CONTEXT_CLIENT_VERSION != null) {
                    pbr.getHeaders().put("X-YouTube-Client-Version", INNERTUBE_CONTEXT_CLIENT_VERSION);
                }
                if (INNERTUBE_CONTEXT_CLIENT_NAME != null) {
                    pbr.getHeaders().put("X-YouTube-Client-Name", INNERTUBE_CONTEXT_CLIENT_NAME);
                }
                // anti ddos
                round = antiDdosSleep(round);
                helper.getPage(pbr, jsonPage);
                if (!isJson) {
                    String output = pbr.toString();
                    output = PluginJSonUtils.unescape(output);
                    output = output.replaceAll("\\s+", " ");
                    pbr.getRequest().setHtmlCode(output);
                }
            } else if (nextPage != null) {
                // OLD! doesn't always present. Depends on server playlist backend code.!
                nextPage = HTMLEntities.unhtmlentities(nextPage);
                round = antiDdosSleep(round);
                helper.getPage(pbr, nextPage);
            } else {
                logger.info("no next page found, abort");
                break;
            }
        }
        logger.info("parsePlaylist method returns: " + ret.size() + " VideoID's!");
        return ret;
    }

    private ArrayList<YoutubeClipData> parseUnlistedPlaylist(YoutubeHelper helper, final Browser br, final String videoID, final String playlistID, final String referenceUrl) throws Exception {
        return null;
    }

    /**
     * Parse a playlist id and return all found video ids
     *
     * @param decryptedLinks
     * @param dupeCheckSet
     * @param base
     * @param playlistID
     * @param videoIdsToAdd
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public ArrayList<YoutubeClipData> parsePlaylist(YoutubeHelper helper, final String videoID, final String playlistID, final String referenceUrl) throws Exception {
        // this returns the html5 player
        if (StringUtils.isNotEmpty(playlistID)) {
            if (!helper.getLoggedIn()) {
                /*
                 * Only set User-Agent if we're not logged in because login session can be bound to User-Agent and tinkering around with
                 * different User-Agents and the same cookies is just a bad idea!
                 */
                // firefox gets different result than chrome! lets hope switching wont cause issue.
                br.getHeaders().put("User-Agent", UserAgents.stringUserAgent(BrowserName.Chrome));
            }
            br.getHeaders().put("Accept-Charset", null);
            Browser brc = br.cloneBrowser();
            helper.getPage(brc, getBase() + "/playlist?list=" + playlistID);
            if (brc.containsHTML("\"This playlist type is unviewable")) {
                brc = br.cloneBrowser();
                helper.getPage(brc, referenceUrl);
                return parseUnlistedPlaylist(helper, brc, videoID, playlistID, referenceUrl);
            } else {
                return parseListedPlaylist(helper, brc, videoID, playlistID, referenceUrl);
            }
        }
        return null;
    }

    protected String extractWebsiteTitle(final Browser br) {
        return Encoding.htmlOnlyDecode(br.getRegex("<meta name=\"title\"\\s+[^<>]*content=\"(.*?)(?:\\s*-\\s*Youtube\\s*)?\"").getMatch(0));
    }

    /**
     * @param round
     * @return
     * @throws InterruptedException
     */
    protected int antiDdosSleep(int round) throws InterruptedException {
        sleep(((DDOS_WAIT_MAX * (Math.min(DDOS_INCREASE_FACTOR, round++))) / DDOS_INCREASE_FACTOR), getCurrentLink().getCryptedLink());
        return round;
    }

    public ArrayList<YoutubeClipData> parseChannelgrid(YoutubeHelper helper, String channelID) throws Exception {
        Browser li = br.cloneBrowser();
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        int counter = 1;
        int round = 0;
        if (StringUtils.isNotEmpty(channelID)) {
            String pageUrl = null;
            while (true) {
                round++;
                if (this.isAbort()) {
                    throw new InterruptedException();
                }
                String content = null;
                if (pageUrl == null) {
                    // this returns the html5 player
                    helper.getPage(br, getBase() + "/channel/" + channelID + "/videos?view=0");
                    checkErrors(br);
                    content = br.toString();
                } else {
                    li = br.cloneBrowser();
                    helper.getPage(li, pageUrl);
                    checkErrors(li);
                    content = Encoding.unicodeDecode(li.toString());
                }
                String[] videos = new Regex(content, "href=\"(/watch\\?v=[A-Za-z0-9\\-_]+)").getColumn(0);
                if (videos != null) {
                    for (String relativeUrl : videos) {
                        final String id = getVideoIDByUrl(relativeUrl);
                        ret.add(new YoutubeClipData(id, counter++));
                    }
                }
                // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
                String nextPage = Encoding.htmlDecode(new Regex(content, "data-uix-load-more-href=\"(/[^<>\"]*?)\"").getMatch(0));
                if (nextPage != null) {
                    pageUrl = getBase() + nextPage;
                    // anti ddos
                    round = antiDdosSleep(round);
                } else {
                    break;
                }
            }
            logger.info("parseChannelgrid method returns: " + ret.size() + " VideoID's!");
        }
        return ret;
    }

    public ArrayList<YoutubeClipData> parseUsergrid(YoutubeHelper helper, String userID) throws Exception {
        // http://www.youtube.com/user/Gronkh/videos
        // channel: http://www.youtube.com/channel/UCYJ61XIK64sp6ZFFS8sctxw
        if (false && userID != null) {
            /** TEST CODE for 1050 playlist max size issue. below comment is incorrect, both grid and channelid return 1050. raztoki **/
            // this format only ever returns 1050 results, its a bug on youtube end. We can resolve this by finding the youtube id and let
            // parseChannelgrid(channelid) find the results.
            ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
            Browser li = br.cloneBrowser();
            li.getPage(getBase() + "/user/" + userID + "/videos?view=0");
            this.channelID = li.getRegex("'CHANNEL_ID', \"(UC[^\"]+)\"").getMatch(0);
            if (StringUtils.isNotEmpty(this.channelID)) {
                return ret;
            }
        }
        Browser li = br.cloneBrowser();
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        int counter = 1;
        if (StringUtils.isNotEmpty(userID)) {
            String pageUrl = null;
            int round = 0;
            while (true) {
                if (this.isAbort()) {
                    throw new InterruptedException();
                }
                String content = null;
                if (pageUrl == null) {
                    // this returns the html5 player
                    helper.getPage(br, getBase() + "/user/" + userID + "/videos?view=0");
                    checkErrors(br);
                    content = br.toString();
                } else {
                    try {
                        li = br.cloneBrowser();
                        helper.getPage(li, pageUrl);
                    } catch (final BrowserException b) {
                        if (li.getHttpConnection() != null && li.getHttpConnection().getResponseCode() == 400) {
                            logger.warning("Youtube issue!:" + b);
                            return ret;
                        } else {
                            throw b;
                        }
                    }
                    checkErrors(li);
                    content = Encoding.unicodeDecode(li.toString());
                }
                String[] videos = new Regex(content, "href=\"(/watch\\?v=[A-Za-z0-9\\-_]+)").getColumn(0);
                if (videos != null) {
                    for (String relativeUrl : videos) {
                        final String id = getVideoIDByUrl(relativeUrl);
                        ret.add(new YoutubeClipData(id, counter++));
                    }
                }
                // Several Pages: http://www.youtube.com/playlist?list=FL9_5aq5ZbPm9X1QH0K6vOLQ
                String nextPage = Encoding.htmlDecode(new Regex(content, "data-uix-load-more-href=\"(/[^<>\"]+)\"").getMatch(0));
                if (nextPage != null) {
                    pageUrl = getBase() + nextPage;
                    round = antiDdosSleep(round);
                } else {
                    break;
                }
            }
            logger.info("parseUsergrid method returns: " + ret.size() + " VideoID's!");
        }
        return ret;
    }

    /**
     * parses 'video_ids=' array, primarily used with watch_videos link
     */
    public ArrayList<YoutubeClipData> parseVideoIds(String video_ids) throws IOException, InterruptedException {
        // /watch_videos?title=Trending&video_ids=0KSOMA3QBU0,uT3SBzmDxGk,X7Xf8DsTWgs,72WhEqeS6AQ,Qc9c12q3mrc,6l7J1i1OkKs,zeu2tI-tqvs,o3mP3mJDL2k,jYdaQJzcAcw&feature=c4-overview&type=0&more_url=
        ArrayList<YoutubeClipData> ret = new ArrayList<YoutubeClipData>();
        int counter = 1;
        if (StringUtils.isNotEmpty(video_ids)) {
            String[] videos = new Regex(video_ids, "([A-Za-z0-9\\-_]+)").getColumn(0);
            if (videos != null) {
                for (String vid : videos) {
                    ret.add(new YoutubeClipData(vid, counter++));
                }
            }
        }
        return ret;
    }

    private void checkErrors(Browser br) throws InterruptedException {
        if (br.containsHTML(">404 Not Found<")) {
            throw new InterruptedException("404 Not Found");
        } else if (br.containsHTML("iframe style=\"display:block;border:0;\" src=\"/error")) {
            throw new InterruptedException("Unknown Error");
        } else if (br.containsHTML("<h2>\\s*This channel does not exist\\.\\s*</h2>")) {
            throw new InterruptedException("Channel does not exist.");
        }
    }

    @Override
    public void init() {
        Browser.setRequestIntervalLimitGlobal(this.getHost(), 100);
    }

    /* NO OVERRIDE!! */
    public boolean hasCaptcha(CryptedLink link, jd.plugins.Account acc) {
        return false;
    }
}