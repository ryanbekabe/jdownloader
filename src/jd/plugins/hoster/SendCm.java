//jDownloader - Downloadmanager
//Copyright (C) 2013  JD-Team support@jdownloader.org
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
package jd.plugins.hoster;

import java.util.ArrayList;
import java.util.List;

import org.jdownloader.plugins.components.XFileSharingProBasic;

import jd.PluginWrapper;
import jd.parser.html.Form;
import jd.parser.html.InputField;
import jd.plugins.Account;
import jd.plugins.Account.AccountType;
import jd.plugins.DownloadLink;
import jd.plugins.HostPlugin;
import jd.plugins.LinkStatus;
import jd.plugins.PluginException;

@HostPlugin(revision = "$Revision$", interfaceVersion = 3, names = {}, urls = {})
public class SendCm extends XFileSharingProBasic {
    public SendCm(final PluginWrapper wrapper) {
        super(wrapper);
        this.enablePremium(super.getPurchasePremiumURL());
    }

    /**
     * DEV NOTES XfileSharingProBasic Version SEE SUPER-CLASS<br />
     * mods: See overridden functions<br />
     * limit-info:<br />
     * captchatype-info: 2020-11-06: null<br />
     * other:<br />
     */
    public static List<String[]> getPluginDomains() {
        final List<String[]> ret = new ArrayList<String[]>();
        // each entry in List<String[]> will result in one PluginForHost, Plugin.getHost() will return String[0]->main domain
        ret.add(new String[] { "send.cm", "sendit.cloud", "usersfiles.com" });
        return ret;
    }

    public static String[] getAnnotationNames() {
        return buildAnnotationNames(getPluginDomains());
    }

    @Override
    public String[] siteSupportedNames() {
        return buildSupportedNames(getPluginDomains());
    }

    public static String[] getAnnotationUrls() {
        return XFileSharingProBasic.buildAnnotationUrls(getPluginDomains());
    }

    @Override
    public String rewriteHost(final String host) {
        /* 2022-07-27: sendit.cloud and usersfiles.com have been morged from another plugin into this one. */
        return this.rewriteHost(getPluginDomains(), host);
    }

    @Override
    public boolean isResumeable(final DownloadLink link, final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return true;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return true;
        } else {
            /* Free(anonymous) and unknown account type */
            return true;
        }
    }

    @Override
    public int getMaxChunks(final Account account) {
        final AccountType type = account != null ? account.getType() : null;
        if (AccountType.FREE.equals(type)) {
            /* Free Account */
            return 1;
        } else if (AccountType.PREMIUM.equals(type) || AccountType.LIFETIME.equals(type)) {
            /* Premium account */
            return 0;
        } else {
            /* Free(anonymous) and unknown account type */
            return 1;
        }
    }

    @Override
    public int getMaxSimultaneousFreeAnonymousDownloads() {
        return -1;
    }

    @Override
    public int getMaxSimultaneousFreeAccountDownloads() {
        return -1;
    }

    @Override
    public int getMaxSimultanPremiumDownloadNum() {
        return -1;
    }

    @Override
    public boolean loginWebsite(final DownloadLink downloadLink, final Account account, final boolean validateCookies) throws Exception {
        try {
            return super.loginWebsite(downloadLink, account, validateCookies);
        } catch (final PluginException e) {
            Form twoFAForm = null;
            final String formKey2FA = "new_ip_token";
            final Form[] forms = br.getForms();
            for (final Form form : forms) {
                final InputField twoFAField = form.getInputField(formKey2FA);
                if (twoFAField != null) {
                    twoFAForm = form;
                    break;
                }
            }
            if (twoFAForm == null) {
                /* Login failed */
                throw e;
            }
            logger.info("2FA code required");
            final DownloadLink dl_dummy;
            if (this.getDownloadLink() != null) {
                dl_dummy = this.getDownloadLink();
            } else {
                dl_dummy = new DownloadLink(this, "Account", this.getHost(), "https://" + account.getHoster(), true);
            }
            String twoFACode = getUserInput("Enter verification code sent to your E-Mail", dl_dummy);
            if (twoFACode != null) {
                twoFACode = twoFACode.trim();
            }
            if (twoFACode == null || !twoFACode.matches("\\d{6}")) {
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiges Format der 2-faktor-Authentifizierung!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid 2-factor-authentication code format!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            logger.info("Submitting 2FA code");
            twoFAForm.put(formKey2FA, twoFACode);
            this.submitForm(twoFAForm);
            if (!this.isLoggedin(br)) {
                if ("de".equalsIgnoreCase(System.getProperty("user.language"))) {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nUngültiger 2-faktor-Authentifizierungscode!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                } else {
                    throw new PluginException(LinkStatus.ERROR_PREMIUM, "\r\nInvalid 2-factor-authentication code!", PluginException.VALUE_ID_PREMIUM_DISABLE);
                }
            }
            account.saveCookies(br.getCookies(getMainPage()), "");
            return true;
        }
    }

    @Override
    protected boolean supports_availablecheck_alt() {
        /* 2020-11-06 */
        return false;
    }

    @Override
    protected boolean supports_availablecheck_filename_abuse() {
        /* 2021-09-29 */
        return false;
    }
}