package app.jingman.reader;
import java.io.*;
import java.util.*;
public class QimanCatalogScannerTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static String book(String id,String title){return "<a href='/comic/"+id+"' title='"+title+"'>"+title+"</a>";}
    public static void main(String[] args)throws Exception{
        String origin=SiteIndex.ORIGIN;
        Map<String,String> pages=new HashMap<>();
        pages.put(origin+"/","<div class='banner'>"+book("Home","首页书目")+"</div>");
        pages.put(origin+"/category","<div class='mainForm'>"+book("A","第一部")+"<a href='/category/page/2'>下一页</a><a href='/category/tags/531'>原创</a><a href='/category/list/3'>韩漫</a><a href='https://evil.test/category/tags/1'>bad</a></div>");
        pages.put(origin+"/category/page/2","<div class='mainForm'>"+book("B","第二部")+"<a href='/category/page/2'>下一页</a></div>");
        pages.put(origin+"/category/tags/531","<div class='mainForm'>"+book("A","第一部")+"<a href='/category/tags/531/page/2'>下一页</a></div>");
        pages.put(origin+"/category/tags/531/page/2","<div class='mainForm'>"+book("C","标签第二页")+"<a href='/category/list/3/page/99'>下一页</a></div>");
        pages.put(origin+"/category/list/3","<div class='mainForm'>"+book("D","韩漫作品")+"</div>");
        pages.put(origin+"/category/order/hits","<div class='mainForm'></div>");
        pages.put(origin+"/category/order/addtime","<div class='mainForm'></div>");
        QimanSearchIndex index=new QimanSearchIndex(new ByteArrayInputStream(new byte[0]));
        QimanCatalogScanner scanner=new QimanCatalogScanner(index,null);
        Map<String,Integer> requests=new HashMap<>();
        QimanCatalogScanner.Reader reader=url->{requests.put(url,requests.getOrDefault(url,0)+1);String html=pages.get(url);if(html==null)throw new IOException("Unexpected URL "+url);return html;};
        scanner.step(reader);scanner.step(reader);
        check(scanner.remaining()>=5,"discover taxonomy and main pagination");
        int remaining=scanner.remaining();
        try{scanner.step(url->{throw new IOException("offline");});throw new AssertionError("expected failure");}catch(IOException expected){}
        check(scanner.remaining()==remaining,"failed page remains queued");
        try{scanner.step(url->"<html>Homepage fallback, not a listing</html>");throw new AssertionError("expected malformed-page failure");}catch(IOException expected){}
        check(scanner.remaining()==remaining,"malformed page remains queued");
        ByteArrayOutputStream checkpoint=new ByteArrayOutputStream();scanner.write(checkpoint);
        byte[] state=checkpoint.toByteArray();
        index=new QimanSearchIndex(new ByteArrayInputStream(state));
        scanner=new QimanCatalogScanner(index,new ByteArrayInputStream(state));
        check(scanner.done()==2&&scanner.remaining()==remaining,"resume pending state");
        int guard=0;while(scanner.hasNext()){check(++guard<20,"bounded traversal");scanner.step(reader);}
        check(index.size()==5,"all unique books from independent listings");
        check(index.search("标签第二页").size()==1,"filter pagination included");
        for(int count:requests.values())check(count==1,"no refetch after resume or cyclic pagination");
        check(!requests.containsKey(origin+"/category/list/3/page/99"),"reject next link outside current filter");
        checkpoint.reset();scanner.write(checkpoint);
        QimanCatalogScanner nextScan=new QimanCatalogScanner(index,new ByteArrayInputStream(checkpoint.toByteArray()));
        check(nextScan.done()==0&&nextScan.remaining()==2,"complete scan starts refresh next time");
        check(!QimanCatalogScanner.allowed("https://evil.test/category")&&!QimanCatalogScanner.allowed(origin+"/comic/A"),"strict official catalog origins");
        QimanCatalogScanner partial=new QimanCatalogScanner(index,null);
        partial.step(reader);partial.step(reader);
        while(partial.hasNext()){
            try{partial.step(url->{if(url.equals(origin+"/category/list/3"))throw new IOException("unavailable category");return pages.get(url);});}
            catch(IOException unavailable){partial.deferFailure();}
        }
        check(partial.remaining()==1,"one failed root must not block other roots");
        checkpoint.reset();partial.write(checkpoint);
        QimanCatalogScanner retry=new QimanCatalogScanner(index,new ByteArrayInputStream(checkpoint.toByteArray()));
        check(retry.hasNext()&&retry.remaining()==1,"retry failed work on next scan");retry.step(reader);check(retry.remaining()==0,"failed root eventually completes");
        System.out.println("Catalog scanner tests passed: taxonomy, pagination, deduplication, resume, failure retry, malformed response, empty categories, cycles, and origin isolation.");
    }
}
