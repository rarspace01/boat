package pirateboat.torrent;

import pirateboat.utilities.HttpHelper;

public abstract class HttpUser {
    public final HttpHelper httpHelper;

    public HttpUser(HttpHelper httpHelper) {
        this.httpHelper = httpHelper;
    }
}
