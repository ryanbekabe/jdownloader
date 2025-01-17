package org.jdownloader;

import java.awt.Component;
import java.awt.Graphics;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.Locale;

import javax.swing.Icon;
import javax.swing.ImageIcon;

import jd.config.Property;
import jd.controlling.faviconcontroller.FavIconRequestor;
import jd.controlling.faviconcontroller.FavIcons;
import jd.http.Browser;
import jd.plugins.PluginForHost;
import jd.utils.JDUtilities;

import org.appwork.swing.components.IDIcon;
import org.appwork.swing.components.IconIdentifier;
import org.appwork.utils.images.IconIO;
import org.appwork.utils.images.Interpolation;
import org.jdownloader.gui.IconKey;
import org.jdownloader.images.AbstractIcon;
import org.jdownloader.images.NewTheme;

public class DomainInfo implements FavIconRequestor, Comparable<DomainInfo>, Icon, IDIcon {
    private static final HashMap<String, String> HARDCODEDFAVICONS = new HashMap<String, String>();
    static {
        HARDCODEDFAVICONS.put("http", IconKey.ICON_URL);// 'http links' results in 'http'
        HARDCODEDFAVICONS.put("ftp", IconKey.ICON_URL);
        HARDCODEDFAVICONS.put("directhttp", IconKey.ICON_URL);
        HARDCODEDFAVICONS.put("f4m", IconKey.ICON_URL);
        HARDCODEDFAVICONS.put("m3u8", IconKey.ICON_URL);
        HARDCODEDFAVICONS.put("updaterequired", IconKey.ICON_URL);
        HARDCODEDFAVICONS.put("jdlog", IconKey.ICON_URL);
        HARDCODEDFAVICONS.put("cloudcache", IconKey.ICON_URL);
        HARDCODEDFAVICONS.put("usenet", IconKey.ICON_LOGO_NZB);
        HARDCODEDFAVICONS.put("genericusenet", IconKey.ICON_LOGO_NZB);
    }
    private static final int                     WIDTH             = 16;
    private static final int                     HEIGHT            = 16;
    private final String                         domain;
    private final IconIdentifier                 iconIdentifier;

    private DomainInfo(String tld, String domain) {
        this.tld = Property.dedupeString(tld);
        if (domain == null || domain.equals(tld)) {
            this.domain = tld;
        } else {
            this.domain = Property.dedupeString(domain);
        }
        this.iconIdentifier = new IconIdentifier("DomainInfo", tld);
    }

    public String toString() {
        return tld;
    }

    private final String tld;

    public String getDomain() {
        return domain;
    }

    public String getTld() {
        return tld;
    }

    protected Icon hosterIcon = null;

    /**
     * Returns a
     *
     * @return
     */
    public Icon getFavIcon(final boolean updatePermission) {
        Icon icon = hosterIcon;
        if (icon == null) {
            final String hardcodedFavIcon = HARDCODEDFAVICONS.get(tld);
            if (hardcodedFavIcon != null) {
                icon = NewTheme.I().getIcon(hardcodedFavIcon, -1);
            } else {
                icon = FavIcons.getFavIcon(tld, this, updatePermission);
                if (icon == null && !updatePermission) {
                    icon = FavIcons.getDefaultIcon(tld, false);
                }
            }
            if (icon != null) {
                icon = new ImageIcon(IconIO.getCroppedImage(IconIO.toBufferedImage(icon)));
                icon = IconIO.getScaledInstance(icon, WIDTH, HEIGHT, Interpolation.BICUBIC);
            }
            if (updatePermission) {
                hosterIcon = icon;
            }
        }
        return icon;
    }

    public Icon getFavIcon() {
        return getFavIcon(true);
    }

    public Icon setFavIcon(Icon icon) {
        if (icon == null) {
            icon = hosterIcon;
            if (icon != null) {
                return icon;
            }
            icon = FavIcons.getFavIcon(getTld(), this);
        }
        if (icon != null) {
            icon = new ImageIcon(IconIO.getCroppedImage(IconIO.toBufferedImage(icon)));
            icon = IconIO.getScaledInstance(icon, WIDTH, HEIGHT, Interpolation.BICUBIC);
        }
        hosterIcon = icon;
        return icon;
    }

    private static final HashMap<String, WeakReference<DomainInfo>> CACHE = new HashMap<String, WeakReference<DomainInfo>>();

    public static DomainInfo getInstance(String domain) {
        if (domain != null) {
            String lcaseTld = domain.toLowerCase(Locale.ENGLISH);
            int index = lcaseTld.indexOf(" ");
            if (index > 0) {
                // for examle recaptcha.com (google)
                lcaseTld = lcaseTld.substring(0, index);
            }
            index = lcaseTld.indexOf("/");
            if (index > 0) {
                // for examle recaptcha.com/bla
                lcaseTld = lcaseTld.substring(0, index);
            }
            synchronized (CACHE) {
                DomainInfo ret = null;
                WeakReference<DomainInfo> domainInfo = CACHE.get(lcaseTld);
                if (domainInfo == null || (ret = domainInfo.get()) == null) {
                    ret = new DomainInfo(Browser.getHost(lcaseTld), lcaseTld);
                    CACHE.put(lcaseTld, new WeakReference<DomainInfo>(ret));
                }
                return ret;
            }
        }
        return null;
    }

    /**
     * WARNING: MAY RETURN null if size is too big returns a high quality icon for this domain. most domains do not support this and will
     * return null; the icon is NOT cached. use with care
     *
     * @param i
     * @return
     */
    public Icon getIcon(int size) {
        return getIcon(size, true);
    }

    public Icon getIcon(int size, final boolean updatePermission) {
        Icon ret = null;
        final String tld = getTld();
        if (NewTheme.I().hasIcon("fav/big." + tld)) {
            ret = new AbstractIcon("fav/big." + tld, -1);
        }
        if (ret == null && NewTheme.I().hasIcon("fav/" + tld)) {
            ret = new AbstractIcon("fav/" + tld, -1);
        }
        if (ret != null && ret.getIconHeight() >= size && ret.getIconWidth() >= size) {
            return IconIO.getScaledInstance(ret, size, size);
        }
        final String hardcodedFavIcon = HARDCODEDFAVICONS.get(tld);
        if (hardcodedFavIcon != null) {
            ret = NewTheme.I().getIcon(hardcodedFavIcon, -1);
        } else {
            ret = FavIcons.getFavIcon(tld, null, updatePermission);
            if (ret == null && !updatePermission) {
                ret = FavIcons.getDefaultIcon(tld, false);
            }
        }
        if (ret != null && (ret.getIconHeight() >= size && ret.getIconWidth() >= size)) {
            return IconIO.getScaledInstance(ret, size, size);
        }
        return null;
    }

    @Deprecated
    public PluginForHost findPlugin() {
        return JDUtilities.getPluginForHost(getTld());
    }

    public int compareTo(DomainInfo o) {
        return getTld().compareTo(o.getTld());
    }

    @Override
    public void paintIcon(Component c, Graphics g, int x, int y) {
        getFavIcon().paintIcon(c, g, x, y);
    }

    @Override
    public int getIconWidth() {
        return WIDTH;
    }

    @Override
    public int getIconHeight() {
        return HEIGHT;
    }

    @Override
    public IconIdentifier getIdentifier() {
        return iconIdentifier;
    }
}
