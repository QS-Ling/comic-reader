package app.jingman.reader;

/** Source selection and URL policy shared by navigation, covers and readers. */
public final class Sources {
    public static boolean baozi(String url){return BaoziSource.isUrl(url);}
    public static String normalize(String input){String value=input.trim();return BaoziSource.isUrl(value)||value.matches("^(?:(?:www|app|appcn)\\.)?baozimh\\.com(?:[/?#].*)?$")?BaoziSource.normalize(value):SiteIndex.normalize(value);}
    public static boolean isComic(String url){return baozi(url)?BaoziSource.isComic(url):SiteIndex.isComic(url);}
    public static String name(String url){return baozi(url)?"包子漫画":"奇漫屋";}
    public static String home(String url){return baozi(url)?BaoziSource.HOME:SiteIndex.ORIGIN+"/";}
    public static String category(String url){return baozi(url)?BaoziSource.ORIGIN+"/baozimhapp/classify":SiteIndex.ORIGIN+"/category";}
    public static SiteIndex.Page parse(String html,String url){return baozi(url)?BaoziSource.parse(html,url):SiteIndex.parse(html,url);}
    public static String detailCover(String html,String url){return baozi(url)?BaoziSource.cover(html):SiteIndex.detailCover(html,url);}
    public static String coverUrl(String address,String base){String image=BaoziSource.imageUrl(address,true);return image.isEmpty()?SiteIndex.coverUrl(address,base):image;}
}
