package org.jdownloader.gui.views.components.packagetable.columns;

import jd.controlling.linkcrawler.CrawledLink;
import jd.controlling.linkcrawler.CrawledPackage;
import jd.controlling.packagecontroller.AbstractNode;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;

import org.appwork.swing.exttable.columns.ExtTextColumn;
import org.jdownloader.gui.translate._GUI;

public class ChecksumColumn extends ExtTextColumn<AbstractNode> {

    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    public ChecksumColumn() {
        super(_GUI._.checksumcolumnmd5());

    }

    @Override
    public boolean isDefaultVisible() {
        return false;
    }

    @Override
    protected boolean isEditable(final AbstractNode obj, final boolean enabled) {
        if (obj instanceof CrawledLink) return true;
        if (obj instanceof DownloadLink) return true;
        return false;
    }

    @Override
    public boolean isEnabled(final AbstractNode obj) {
        if (obj instanceof CrawledPackage) { return ((CrawledPackage) obj).getView().isEnabled(); }
        if (obj instanceof FilePackage) { return ((FilePackage) obj).getView().isEnabled(); }
        return obj.isEnabled();
    }

    @Override
    protected void setStringValue(String value, AbstractNode object) {
        DownloadLink dl = null;
        if (object instanceof CrawledLink) {
            dl = ((CrawledLink) object).getDownloadLink();
        } else if (object instanceof DownloadLink) {
            dl = ((DownloadLink) object);
        }
        if (dl != null) {

            dl.setMD5Hash(value);
        }
    }

    @Override
    public String getStringValue(AbstractNode value) {
        DownloadLink dl = null;
        if (value instanceof CrawledLink) {
            dl = ((CrawledLink) value).getDownloadLink();
        } else if (value instanceof DownloadLink) {
            dl = ((DownloadLink) value);
        }
        if (dl != null) return dl.getMD5Hash();
        return null;

    }

}
