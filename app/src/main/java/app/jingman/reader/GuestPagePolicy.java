package app.jingman.reader;
import java.net.*;
import java.util.*;
import java.util.regex.*;

/** Only supported public documents and standard Cloudflare resources enter verification views. */
public final class GuestPagePolicy {
    static boolean site(String host){return Arrays.asList("appcn.baozimh.com","app.baozimh.com","www.qimanwu.app","qimanwu.app").contains(host);}
    private static boolean secure(URI u){return "https".equals(u.getScheme())&&u.getUserInfo()==null&&(u.getPort()==-1||u.getPort()==443);}
    public static boolean page(String address){
        try{URI u=URI.create(address);if(!secure(u)||!site(u.getHost()))return false;
            if(BaoziSource.isUrl(address)){BaoziSource.normalize(address);return true;}
            QimanSource.normalize(address);return true;
        }catch(Exception e){return false;}
    }
    public static boolean challenge(String address){try{URI u=URI.create(address);return secure(u)&&("challenges.cloudflare.com".equals(u.getHost())||site(u.getHost())&&u.getPath()!=null&&u.getPath().startsWith("/cdn-cgi/challenge-platform/"));}catch(Exception e){return false;}}
    public static boolean matches(String expected,String actual){
        if(!page(expected)||!page(actual)||Sources.baozi(expected)!=Sources.baozi(actual))return false;
        URI a=URI.create(expected),b=URI.create(actual);
        if(!Sources.baozi(expected)&&SiteIndex.isSearch(expected)&&SiteIndex.isSearch(actual))return SiteIndex.searchQuery(expected).equals(SiteIndex.searchQuery(actual));
        if(!Objects.equals(a.getPath(),b.getPath()))return false;
        if(a.getPath().endsWith("/search"))return Sources.baozi(expected)?BaoziSource.query(expected,"q","").equals(BaoziSource.query(actual,"q",""))&&BaoziSource.query(expected,"page","1").equals(BaoziSource.query(actual,"page","1")):SiteIndex.searchQuery(expected).equals(SiteIndex.searchQuery(actual));
        return !a.getPath().endsWith("/classify")||Objects.equals(a.getQuery(),b.getQuery());
    }
    public static boolean ready(String html,String url){return Sources.baozi(url)?Pattern.compile("(?is)\\bid\\s*=\\s*['\"]__nuxt['\"]").matcher(html).find():QimanSource.readable(html,url);}
    public static boolean hasChallenge(String html){return Pattern.compile("(?is)cf-chl|challenges\\.cloudflare\\.com|/cdn-cgi/challenge-platform/|just a moment|turnstile").matcher(html).find();}
    public static String clearanceCookies(String all){
        if(all==null)return "";StringBuilder result=new StringBuilder();for(String pair:all.split(";")){String p=pair.trim();if(p.startsWith("cf_clearance=")||p.startsWith("__cf_bm=")){if(result.length()>0)result.append("; ");result.append(p);}}return result.toString();
    }
}
