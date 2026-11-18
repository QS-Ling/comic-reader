package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
public class BuildQimanHomeSnapshot {
    public static void main(String[] args)throws Exception{
        String url=SiteIndex.ORIGIN+"/";StringBuilder name=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(url.getBytes("UTF-8")))name.append(String.format(Locale.ROOT,"%02x",b&255));
        Path path=Paths.get("build/qiman-scan/"+name+".html");String html=new String(Files.readAllBytes(path),"UTF-8");SiteIndex.Page page=SiteIndex.parseHome(html,url);
        if(page.books.size()<20)throw new AssertionError("Official homepage snapshot incomplete");
        QimanSearchIndex catalog=new QimanSearchIndex(new FileInputStream("app/src/main/assets/qiman-catalog.tsv"));Map<String,SiteIndex.Entry> known=new HashMap<>();for(SiteIndex.Entry e:catalog.entries())known.put(e.url,e);
        for(int i=0;i<page.books.size();i++){SiteIndex.Entry e=page.books.get(i),match=known.get(e.url);if(e.cover.isEmpty()&&match!=null)page.books.set(i,new SiteIndex.Entry(e.url,e.title,3,match.cover));}
        try(OutputStream out=new FileOutputStream("app/src/main/assets/qiman-home.tsv")){QimanHomeCache.write(page,Files.getLastModifiedTime(path).toMillis(),out);}
        System.out.println("Bundled official homepage: "+page.books.size()+" books in recommendation order.");
    }
}
