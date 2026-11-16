package app.jingman.reader;
import java.nio.file.*;
public class CoverFallbackTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String fixture(String name)throws Exception{return new String(Files.readAllBytes(Paths.get("build/site-"+name+".html")),"UTF-8");}
    public static void main(String[] args)throws Exception{
        String[][] books={{"threshold","qA8BaW9R2v"},{"steward","54K33mlYKx"},{"mermaid","OP81k3rEKb"},{"rootless","VrM5vxDdK0"},{"seaside","VrM5vrJdK0"}};
        SiteIndex.Page page=SiteIndex.parse(fixture("home-current"),SiteIndex.ORIGIN+"/");
        for(String[] book:books){
            String url=SiteIndex.ORIGIN+"/comic/"+book[1];
            SiteIndex.Entry entry=page.books.stream().filter(e->e.url.equals(url)).findFirst().orElseThrow(AssertionError::new);
            check(entry.cover.isEmpty(),"reproduce screenshot: no inline cover");
            String detail=fixture("detail-"+book[0]);int[] calls={0};
            String resolved=CoverSource.resolve(entry.cover,entry.url,u->{calls[0]++;check(u.equals(url),"correct detail page");return detail;});
            check(!resolved.isEmpty()&&resolved.equals(SiteIndex.detailCover(detail,url)),"correct fallback cover");
            check(calls[0]==1,"one detail lookup");
            check(CoverSource.resolve(resolved,url,u->{throw new AssertionError("unnecessary detail fetch");}).equals(resolved),"inline image direct path");
            System.out.println(book[0]+": fallback resolved");
        }
        boolean missing=false;try{CoverSource.resolve("",books[0][1],u->"");}catch(java.io.IOException e){missing=true;}check(missing,"invalid detail rejected");
        boolean noImage=false;try{CoverSource.resolve("",SiteIndex.ORIGIN+"/comic/Test",u->"<div class='comicInfo'>No image</div>");}catch(java.io.IOException e){noImage=e.getMessage().equals("原站暂无封面");}check(noImage,"explicit no-cover error");
        if(args.length>0&&args[0].equals("--live")){
            String url=SiteIndex.ORIGIN+"/comic/54K33mlYKx";
            String cover=CoverSource.resolve("",url,CoverSource::detail);
            byte[] bytes=CoverSource.image(cover);
            check(bytes.length>1000,"live image body");
            Files.write(Paths.get("build/verified-fallback-cover.webp"),bytes);
            System.out.println("Production fallback HTTP path: "+bytes.length+" bytes -> "+cover);
        }
        System.out.println("Missing-cover regression passed for all five screenshot books, inline fast path and explicit errors.");
    }
}
