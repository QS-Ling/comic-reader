package app.jingman.reader;
import java.nio.file.*;
import java.util.*;
public class SiteIndexTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String fixture(String name)throws Exception{return new String(Files.readAllBytes(Paths.get("build/site-"+name+".html")),"UTF-8");}
    public static void main(String[] args)throws Exception{
        for(String value:Arrays.asList("qimanwu.app","/qimanwu.app","https://qimanwu.app","https://m.qimanwu.app/","www.qimanwu.app/"))check(SiteIndex.normalize(value).equals(SiteIndex.ORIGIN+"/"),"root alias "+value);
        check(SiteIndex.normalize("/category/page/2").endsWith("/category/page/2"),"pagination route");
        check(SiteIndex.isComic(SiteIndex.normalize("qimanwu.app/comic/Zr2gL5nd86")),"comic route");
        for(String value:Arrays.asList("https://evil.test/","javascript:alert(1)","https://qimanwu.app.evil.test/","https://name@qimanwu.app/","https://qimanwu.app/../admin","https://qimanwu.app:444/")){
            boolean rejected=false;try{SiteIndex.normalize(value);}catch(IllegalArgumentException e){rejected=true;}check(rejected,"unsafe route "+value);
        }
        SiteIndex.Page home=SiteIndex.parse(fixture("home"),SiteIndex.ORIGIN+"/");
        SiteIndex.Page category=SiteIndex.parse(fixture("category"),SiteIndex.ORIGIN+"/category");
        SiteIndex.Page update=SiteIndex.parse(fixture("update"),SiteIndex.ORIGIN+"/custom/update");
        SiteIndex.Page top=SiteIndex.parse(fixture("top"),SiteIndex.ORIGIN+"/custom/top");
        check(home.books.size()>10,"homepage books");check(category.books.size()==10,"category listing only");
        check(category.next.endsWith("/category/page/2"),"next page");check(category.filters.size()>20,"category filters");
        check(update.books.size()>10,"updates");check(top.books.size()==10,"rank listing only");
        SiteIndex.Page empty=SiteIndex.parse(fixture("search"),SiteIndex.searchUrl("闪现"));check(empty.books.isEmpty(),"empty search must not include header links");
        String malicious="<div class='mainSearch'><script><a href='/comic/Bad'>fake</a></script><a href='https://evil.test/comic/Bad'>bad</a><a href='/comic/Good' title='A &amp; B'>ok</a><a href='/comic/Good'>第1话</a></div>";
        SiteIndex.Page clean=SiteIndex.parse(malicious,SiteIndex.ORIGIN+"/");check(clean.books.size()==1&&clean.books.get(0).title.equals("A & B"),"strip scripts, reject external links, deduplicate title");
        System.out.println("Site navigation passed: home="+home.books.size()+", category="+category.books.size()+", updates="+update.books.size()+", ranks="+top.books.size()+", filters="+category.filters.size()+"; root aliases, pagination, empty search, unsafe routes.");
    }
}
