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

package jd.plugins.hoster;

import java.io.IOException;

import jd.PluginWrapper;
import jd.gui.UserIO;
import jd.http.Encoding;
import jd.parser.Regex;
import jd.parser.html.Form;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;
import jd.plugins.PluginForHost;
import jd.plugins.DownloadLink.AvailableStatus;

//turkfile by pspzockerscene
@HostPlugin(revision = "$Revision$", interfaceVersion = 2, names = { "turkfile.com" }, urls = { "http://[\\w\\.]*?turkfile\\.com/[a-z|0-9]+/.+" }, flags = { 0 })
public class TurkFileCom extends PluginForHost {

    public TurkFileCom(PluginWrapper wrapper) {
        super(wrapper);
    }

    @Override
    public String getAGBLink() {
        return "http://www.turkfile.com/tos.html";
    }

    // @Override
    public AvailableStatus requestFileInformation(DownloadLink link) throws IOException, PluginException {
        this.setBrowserExclusive();
        br.setCookie("http://www.turkfile.com", "lang", "english");
        br.getPage(link.getDownloadURL());
        if (br.containsHTML("No such file with this filename")) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        if (br.containsHTML("No such user exist")) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        if (br.containsHTML("File Not Found")) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        String filename = Encoding.htmlDecode(br.getRegex("<h2>Download File (.*?)</h2>").getMatch(0));
        String filesize = br.getRegex("</font> ((.*?))</font>").getMatch(0);
        if (filename == null || filesize == null) throw new PluginException(LinkStatus.ERROR_FILE_NOT_FOUND);
        link.setName(filename);
        link.setDownloadSize(Regex.getSize(filesize));
        return AvailableStatus.TRUE;
    }

    // @Override
    @Override
    public void handleFree(DownloadLink downloadLink) throws Exception, PluginException {
        requestFileInformation(downloadLink);
        br.setFollowRedirects(true);
        // Form um auf free zu "klicken"
        Form DLForm0 = br.getForm(0);
        if (DLForm0 == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFEKT);
        DLForm0.remove("method_premium");
        br.submitForm(DLForm0);
        // Form um auf "Datei herunterladen" zu klicken
        Form DLForm = br.getFormbyProperty("name", "F1");
        if (DLForm == null) throw new PluginException(LinkStatus.ERROR_PLUGIN_DEFEKT);
        if (br.containsHTML("<br><b>Password:</b>")) {
                String pass = UserIO.getInstance().requestInputDialog("Password");
                DLForm.put("password", pass);
            
        }
        sleep(25000l, downloadLink);
        br.openDownload(downloadLink, DLForm, false, 1);
        {
            if (!(dl.getConnection().isContentDisposition())) {
                br.followConnection();
                dl.getConnection().disconnect();

                {
                    if (br.containsHTML("Wrong password")) throw new PluginException(LinkStatus.ERROR_RETRY);
                    logger.warning("Wrong password!");
                    dl.getConnection().disconnect();
                }
                dl.getConnection().disconnect();
            } else {

                dl.startDownload();
            }
        }
    }

    @Override
    public void reset() {
    }

    // @Override
    public int getMaxSimultanFreeDownloadNum() {
        return 20;
    }

    @Override
    public void resetDownloadlink(DownloadLink link) {
    }

}