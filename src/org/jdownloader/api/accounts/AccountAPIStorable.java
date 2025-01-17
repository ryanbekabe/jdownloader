package org.jdownloader.api.accounts;

import org.appwork.storage.Storable;
import org.appwork.storage.StorableValidatorIgnoresMissingSetter;
import org.jdownloader.myjdownloader.client.json.JsonMap;

import jd.plugins.Account;

@Deprecated
public class AccountAPIStorable implements Storable {
    private Account acc;
    private JsonMap infoMap = null;

    @StorableValidatorIgnoresMissingSetter
    public long getUUID() {
        Account lacc = acc;
        if (lacc != null) {
            return lacc.getId().getID();
        }
        return 0;
    }

    @StorableValidatorIgnoresMissingSetter
    public String getHostname() {
        Account lacc = acc;
        if (lacc != null) {
            return lacc.getHoster();
        }
        return null;
    }

    public JsonMap getInfoMap() {
        return infoMap;
    }

    public void setInfoMap(JsonMap infoMap) {
        this.infoMap = infoMap;
    }

    @SuppressWarnings("unused")
    private AccountAPIStorable(/* Storable */) {
    }

    public AccountAPIStorable(Account acc) {
        this.acc = acc;
    }
}
