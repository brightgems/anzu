package controllers;

import play.Play;
import play.mvc.*;
import play.Logger;
import views.html.index;
// Java8 lib
import java.util.Base64;


import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class MicroDmpCore extends Controller {

    // COOKIEはドメインごとに区別されるのでここではMDMPなどフレームワークの固有名詞は不要だが、ローカルホストをよく開発で使うのでここではつけておく
    private static final String COOKIE_ID_KEY = "MICRO_DMP_3RD_COOKIE_ID";

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

        processingCookieId();

        response().setContentType("image/gif");
        return ok(onePixcelGifBytes);
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

        response().setCookie(COOKIE_ID_KEY, generateUniqueId(), COOKIE_MAX_AFTER_AGE);
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

}
