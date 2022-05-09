package jd.plugins.decrypter;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import org.appwork.utils.StringUtils;
import org.jdownloader.plugins.controller.LazyPlugin;

import jd.PluginWrapper;
import jd.controlling.ProgressController;
import jd.http.Browser;
import jd.parser.html.HTMLParser;
import jd.plugins.CryptedLink;
import jd.plugins.DecrypterPlugin;
import jd.plugins.DownloadLink;
import jd.plugins.PluginException;
import jd.plugins.PluginForDecrypt;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

@DecrypterPlugin(revision = "$Revision$", interfaceVersion = 2, names = {}, urls = {})
public abstract class AbstractPastebinCrawler extends PluginForDecrypt {
    public AbstractPastebinCrawler(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public LazyPlugin.FEATURE[] getFeatures() {
        return new LazyPlugin.FEATURE[] { LazyPlugin.FEATURE.PASTEBIN };
    }

    /** Use this if you want to change the URL added by the user before processing it. */
    protected void correctCryptedLink(final CryptedLink param) {
    }

    /**
     * Use this to control which URLs should be returned and which should get skipped. </br>
     * Default = Allow all results
     */
    protected boolean allowResult(final String url) {
        return true;
    }

    /** Returns unique contentID which is expected to be in the given url. */
    abstract String getFID(final String url);

    @Override
    public ArrayList<DownloadLink> decryptIt(final CryptedLink param, ProgressController progress) throws Exception {
        /* TODO: Implement logic of pastebin settings once available: https://svn.jdownloader.org/issues/90043 */
        this.preProcess(param);
        final ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        final String plaintxt = getPastebinText(this.br);
        if (plaintxt == null) {
            logger.warning("Could not find pastebin textfield");
            return decryptedLinks;
        }
        final PluginForHost sisterPlugin = JDUtilities.getPluginForHost(this.getHost());
        if (sisterPlugin != null) {
            final DownloadLink textfile = getDownloadlinkForHosterplugin(param, plaintxt);
            decryptedLinks.add(textfile);
        }
        /* TODO: Differentiate between URLs that we support (= have plugins for) and those we don't support. */
        final String[] links = HTMLParser.getHttpLinks(plaintxt, "");
        logger.info("Found " + links.length + " URLs in plaintext");
        for (final String link : links) {
            decryptedLinks.add(createDownloadlink(link));
        }
        return decryptedLinks;
    }

    protected DownloadLink getDownloadlinkForHosterplugin(final CryptedLink link, final String pastebinText) {
        final DownloadLink textfile = this.createDownloadlink(link.getCryptedUrl());
        if (StringUtils.isEmpty(pastebinText)) {
            return null;
        }
        try {
            textfile.setDownloadSize(pastebinText.getBytes("UTF-8").length);
        } catch (final UnsupportedEncodingException ignore) {
            ignore.printStackTrace();
        }
        /* TODO: Set filename according to user preference */
        textfile.setFinalFileName(this.getFID(link.getCryptedUrl()) + ".txt");
        textfile.setAvailable(true);
        return textfile;
    }

    /** Accesses URL, checks if content looks like it's available and handles password/captcha until plaintext is available in HTML. */
    protected abstract void preProcess(final CryptedLink param) throws IOException, PluginException;

    /** Collects metadata which will be used later. */
    protected PastebinMetadata crawlMetadata(final CryptedLink param, final Browser br) {
        final PastebinMetadata metadata = new PastebinMetadata(this.getFID(param.getCryptedUrl()));
        metadata.setPastebinText(getPastebinText(br));
        return metadata;
    }

    protected abstract String getPastebinText(final Browser br);

    protected class PastebinMetadata {
        private String contentID     = null;
        private String title         = null;
        private Long   date          = null;
        private String username      = null;
        private String description   = null;
        private String pastebinText  = null;
        private String password      = null;
        private String fileExtension = ".txt";

        public String getContentID() {
            return contentID;
        }

        public void setContentID(String contentID) {
            this.contentID = contentID;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public Long getDate() {
            return date;
        }

        public String getDateFormatted() {
            if (this.date == null) {
                return null;
            } else {
                return new SimpleDateFormat("yyyy-MM-dd").format(new Date(this.date.longValue()));
            }
        }

        public void setDate(Long date) {
            this.date = date;
        }

        public PastebinMetadata() {
        }

        public PastebinMetadata(final String contentID) {
            this.contentID = contentID;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getPastebinText() {
            return pastebinText;
        }

        public void setPastebinText(String pastebinText) {
            this.pastebinText = pastebinText;
        }

        public String getFileExtension() {
            return fileExtension;
        }

        public void setFileExtension(String fileExtension) {
            this.fileExtension = fileExtension;
        }

        public String getFilename() {
            /* contentID needs to be always given! */
            if (this.contentID == null) {
                return null;
            }
            if (this.date != null && this.username != null && this.title != null) {
                return this.getDateFormatted() + "_" + this.title + "_" + this.username + "_" + this.contentID + this.getFileExtension();
            } else if (this.date != null && this.username != null) {
                return this.getDateFormatted() + "_" + this.username + "_" + this.contentID + this.getFileExtension();
            } else if (this.date != null) {
                return this.getDateFormatted() + "_" + this.contentID + this.getFileExtension();
            } else {
                return this.contentID + this.getFileExtension();
            }
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
