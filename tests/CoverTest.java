package app.jingman.reader;
import java.nio.file.*;
public class CoverTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String fixture(String name)throws Exception{return new String(Files.readAllBytes(Paths.get("build/site-"+name+".html")),"UTF-8");}
    public static void main(String[] args)throws Exception{
        String base=SiteIndex.ORIGIN+"/comic/Zr2gL5nd86",html=fixture("detail");
        String cover=SiteIndex.detailCover(html,base);
        check(cover.equals(SiteIndex.ORIGIN+"/attachment/comic/2026/06/29/202606291719168801.webp"),"correct detail cover, not sidebar recommendation");
        String changed=html.replace(cover,SiteIndex.ORIGIN+"/attachment/comic/new.webp?rev=2");
        check(SiteIndex.detailCover(changed,base).endsWith("/new.webp?rev=2"),"website cover changes propagate");
        SiteIndex.Page home=SiteIndex.parse(fixture("home"),SiteIndex.ORIGIN+"/");
        SiteIndex.Page category=SiteIndex.parse(fixture("category"),SiteIndex.ORIGIN+"/category");
        check(home.books.stream().filter(e->!e.cover.isEmpty()).count()>20,"home previews");
        check(category.books.stream().filter(e->!e.cover.isEmpty()).count()==10,"all category previews");
        String lazy="<div class='mainSearch'><a href='/comic/A'><img src='/lazy.gif' data-src='/attachment/comic/a.webp' alt='A'></a><a href='/comic/A' title='Name'>Name</a></div>";
        SiteIndex.Entry a=SiteIndex.parse(lazy,base).books.get(0);
        check(a.title.equals("Name")&&a.cover.endsWith("/a.webp"),"title merge preserves lazy cover");
        check(SiteIndex.coverUrl("https://evil.test/attachment/comic/a.webp",base).isEmpty(),"reject external cover");
        check(SiteIndex.coverUrl("/tracking.gif",base).isEmpty(),"reject non-cover asset");
        check(SiteIndex.detailCover("<script><div class='comicInfo'><img src='/attachment/comic/bad.webp'></script>",base).isEmpty(),"no scripted preview");
        System.out.println("Cover parsing passed: original detail, refreshed cover, homepage/category thumbnails, lazy images, safe URLs.");
    }
}
