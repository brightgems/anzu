package controllers;

import models.Audience3rdAccessLog;
import play.Play;
import play.mvc.*;
import play.Logger;
import views.html.index;
// Java8 lib
import java.net.URL;
import java.util.Base64;


import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MicroDmpCore extends Controller {

    // COOKIEはドメインごとに区別されるのでここではMDMPなどフレームワークの固有名詞は不要だが、ローカルホストをよく開発で使うのでここではつけておく
    private static final String COOKIE_ID_KEY = "MICRO_DMP_3RD_COOKIE_ID";

    private static final String COOKIE_OPT_OUT_KEY = "MICRO_DMP_OPT_OUT";

    // オプトアウトが有効である場合の値（今回はフラグとして扱うので1に設定、APでは0に設定することはない
    private static final String COOKIE_OPT_OUT_VALUE = "1";

    // Cookieの有効期限は1年間にしておく(対象のサービスに1年間はアクセスしなくてもオーディエンスとして扱う）
    private static final int COOKIE_MAX_AFTER_AGE = 31622400;

    //ref https://css-tricks.com/snippets/html/base64-encode-of-1x1px-transparent-gif/
    private static final String onePixelGifBase64 = "R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7";

    // Base64はJava8からのメソッドなので注意
    public static byte [] onePixcelGifBytes = Base64.getDecoder().decode(onePixelGifBase64);

    public static String macAdress = getMacAddress();

    public Result index() {
        return ok(index.render("Your new application is ready."));
    }

    // pixcel tracking
    // ref https://support.google.com/dfp_premium/answer/1347585?hl=ja
    public Result pixcelTracking() {

        // ブラウザのDNTとDMPのDNT設定が有効の場合トラッキング処理はしない
        if (isDNT()) {
            return ok();
        }

        // ブラウザにオプトアウトのCookieがある場合トラッキング処理はしない
        if(isOptOut()) {
            return ok();
        }

        processingCookieId();

        response().setContentType("image/gif");
        return ok(onePixcelGifBytes);
    }

    /**
     * オプトアウトのリクエストを処理します。
     * Cookieにオプトアウトの設定をします。
     *
     * @return
     */
    public Result optOut(){
        // FIXME　本来はオプトアウトのクッキーの有効期限は無限が好ましい
        response().setCookie(COOKIE_OPT_OUT_KEY, "1", COOKIE_MAX_AFTER_AGE);
        Logger.debug("set optout cookie!");
        return ok();
    }


    /**
     * CookieIDの処理を行う
     * CookieIDがあれば有効期限を更新
     * CookieIDがなければCokkieIDを発行し設定
     *
     * @return false=Cookie発行済み true=Cookie新規発行
     */
    private static void processingCookieId() {

        //name() まで呼ぶとnullpoになる
        if(request().cookie(COOKIE_ID_KEY) != null) {
            Http.Cookie cookie = request().cookie(COOKIE_ID_KEY);
            Logger.debug("Cookie Exist!");
            Logger.debug(cookie.value());
            response().setCookie(COOKIE_ID_KEY, cookie.value(), COOKIE_MAX_AFTER_AGE);

            saveAudience3rdAccessLog(cookie.value(), false);
            return;
        }
        // IDがなかったらID生成

        /*
        public void setCookie(String name, String value);
        public void setCookie(String name, String value, Integer maxAge);
        public void setCookie(String name, String value, Integer maxAge, String path);
        public void setCookie(String name, String value, Integer maxAge, String path, String domain);
        public void setCookie(String name, String value, Integer maxAge, String path, String domain, boolean secure, boolean httpOnly);
         */

        String id =  generateUniqueId();
        response().setCookie(COOKIE_ID_KEY, id, COOKIE_MAX_AFTER_AGE);
        saveAudience3rdAccessLog(id, true);
        return;
    }

    /**
     * ミリセカンド、ナノセカンド、MACアドレスを使用してユニークなCookieIDを生成する
     *
     * @return CookieID（SHA256のHEX）
     */
    private static String generateUniqueId() {
        StringBuffer id = new StringBuffer();

        // 1/1000秒まで
        long now = System.currentTimeMillis();
        long nano = System.nanoTime();

        // TODO ちゃんとやるなら乱数生成などユニーク精度を高めるロジックを入れる
        String baseString = String.valueOf(now) + " " + String.valueOf(nano) + " " + macAdress;
        Logger.debug(baseString);

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(baseString.getBytes());
            byte[] digest = md.digest();

            for (int i = 0; i < digest.length; i++) {
                id.append(String.format("%02x", digest[i]));
            }
            Logger.debug(id.toString());
        }
        catch (NoSuchAlgorithmException e) {
            //TODO
        }
        return id.toString();
    }


    /**
     * MACアドレスを取得する
     * 複数のマシンでこのAPサーバーを動かした時にCOOKIE_IDをユニークにするためサーバーのMACアドレスを使用する
     * 複数のマシン間でMACアドレスが異なることが保証されている時にこのメソッドを使用する。
     * クラウド環境や仮想ネットワークインターフェースだとMACアドレスが同じになる可能性があるので注意。
     *
     * @return　動作マシンのMACアドレスを文字列化したもの
     */
    public static String getMacAddress() {
        String macAdress = "";
        Enumeration<NetworkInterface> nic = null;
        try {
            nic = NetworkInterface.getNetworkInterfaces();

            for (; nic.hasMoreElements(); ) {
                NetworkInterface n = nic.nextElement();
                Logger.debug(n.getName());
                Logger.debug(n.getDisplayName());
                byte [] b = n.getHardwareAddress();
                if (b == null) continue;
                String singleIfAdress = "";
                for(int i=0;i<b.length;i++) {
                    singleIfAdress+=String.format("%02x", b[i]);
                }
                Logger.debug(singleIfAdress);
                macAdress+=singleIfAdress;
            }
        }
        catch (SocketException e) {
            // TODO
        }
        return macAdress;
    }

    /**
     * DNT判定処理
     *
     * @return ブラウザのDNTとDMPのDNT設定が有効の場合true、それ以外の場合false
     */
    public static boolean isDNT() {
        Http.Request req = play.mvc.Http.Context.current().request();
        String rawRequestDntFlag = req.getHeader("DNT");
        boolean requestDntFlag = false;

        Logger.debug("rawRequestDntFlag=" + rawRequestDntFlag);

        if (rawRequestDntFlag != null){
            requestDntFlag = Integer.valueOf(rawRequestDntFlag) == 1 ? true : false;
        }

        if(requestDntFlag == false) {
            return false;
        }

        Boolean applicationDntFlag = Play.application().configuration().getBoolean("ad.dnt.enable");
        Logger.debug("applicationDntFlag=" + applicationDntFlag);

        if (applicationDntFlag) {
            Logger.debug("--- DNT ON ---");
            return true;
        }

        return false;
    }

    /**
     * リクエストのCookie値を取得しOPTOUTしているオーディエンスかを判定します
     * @return
     */
    public static boolean isOptOut() {
        if(request().cookie(COOKIE_OPT_OUT_KEY) != null) {
            Http.Cookie cookie = request().cookie(COOKIE_OPT_OUT_KEY);
            Logger.debug("OptOut Cookie Value=" + cookie.value());

            if (cookie.value().equals(COOKIE_OPT_OUT_VALUE)) {
                Logger.debug("is OptOut Cookie.");
                return true;
            }
        }

        return false;
    }

    public static void saveAudience3rdAccessLog(String cookieId, boolean isFirst) {

        // http://nginx.org/en/docs/http/ngx_http_core_module.html
        // Embedded Variables
        Audience3rdAccessLog audience3rdAccessLog = new Audience3rdAccessLog();
        audience3rdAccessLog.audienceCookieId = cookieId;
        audience3rdAccessLog.isFirst = isFirst ? 1 : 0;
        audience3rdAccessLog.ua = request().getHeader("User-Agent");
        audience3rdAccessLog.ip = request().getHeader("X-Real-IP");
        audience3rdAccessLog.accessHostName = request().getHeader("Referer") != null ? url2Hostname(request().getHeader("Referer")) : "";
        audience3rdAccessLog.accessUrl = request().getHeader("Referer") != null ? request().getHeader("Referer") : "";

        Audience3rdAccessLog.create(audience3rdAccessLog);

    }

    public static String url2Hostname(String url) {
        String hostname = "";
        // FIXME 高速化する場合は自前で文字列を分解する
        try {
            URL urlObject = new URL(url);
            hostname =  urlObject.getHost();
        }
        catch (Exception e) {
            // TODO
            Logger.debug(e.toString());

        }
        return hostname;
    }

}
