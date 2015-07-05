package models;
import play.Logger;
import play.db.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.util.Date;

/**
 * Created by Siori on 15/07/06.
 */

public class Audience3rdAccessLog {
    /**
     * CREATE TABLE `audience_3rd_access_log` (
     `id` bigint(20) NOT NULL AUTO_INCREMENT,
     `audience_cookie_id` varchar(80) NOT NULL DEFAULT '',
     `access_host_name` varchar(255) NOT NULL DEFAULT '',
     `access_url` varchar(4096) NOT NULL DEFAULT '',
     `access_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     `is_first` tinyint(1) NOT NULL,
     `ip` varchar(15) DEFAULT NULL,
     `ua` varchar(255) DEFAULT NULL,
     `created_at` datetime NOT NULL,
     `updated_at` datetime NOT NULL,
     PRIMARY KEY (`id`),
     KEY `audience_cookie_id_index` (`audience_cookie_id`),
     KEY `access_host_name_index` (`access_host_name`)
     ) ENGINE=InnoDB DEFAULT CHARSET=utf8;
     */

    public Long id;
    public String audienceCookieId;
    public String accessHostName;
    public String accessUrl;
    public Date access_time;
    public int isFirst;
    public String ip;
    public String ua;
    public Date created_at;
    public Date updated_at;


    // https://www.playframework.com/documentation/2.4.x/JavaDatabase
    public static void create(Audience3rdAccessLog audience3rdAccessLog) {
        Connection connection = DB.getConnection();
        try {
            String query = "INSERT INTO audience_3rd_access_log " +
                    "(audience_cookie_id, access_host_name, access_url, access_time, is_first, ip, ua, created_at, updated_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,?) ";

            PreparedStatement stmt = connection.prepareStatement(query);

            stmt.setString(1, audience3rdAccessLog.audienceCookieId);
            stmt.setString(2, audience3rdAccessLog.accessHostName);
            stmt.setString(3, audience3rdAccessLog.accessUrl);
            stmt.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            stmt.setInt(5, audience3rdAccessLog.isFirst);
            stmt.setString(6, audience3rdAccessLog.ip);
            stmt.setString(7, audience3rdAccessLog.ua);
            stmt.setTimestamp(8, new Timestamp(System.currentTimeMillis()));
            stmt.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
            stmt.executeUpdate();

            stmt.close();

            connection.close();
        }
        catch (Exception e) {
            Logger.debug(e.toString());
        }
    }
}
