package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class BuildScannedQimanCatalog {
    public static void main(String[] args)throws Exception{
        QimanSearchIndex index=new QimanSearchIndex(new ByteArrayInputStream(new byte[0]));
        boolean progress=args.length>0;
        List<String> lines=Files.readAllLines(Paths.get(progress?"build/qiman-scan/manifest-progress.tsv":"build/qiman-scan/manifest.tsv"));
        for(String line:lines){
            String[] fields=line.split("\t",2);String url=fields[0];
            String html=new String(Files.readAllBytes(Paths.get(fields[1])),"UTF-8");
            if(!QimanCatalogScanner.allowed(url))throw new AssertionError("Unsafe scan route "+url);
            if(!url.equals(SiteIndex.ORIGIN+"/")&&!html.matches("(?s).*class=[\"'][^\"']*\\bmainForm\\b.*"))throw new AssertionError("Unexpected listing "+url);
            for(SiteIndex.Entry entry:SiteIndex.parse(html,url).books)index.add(entry);
        }
        if(index.size()<=528||index.search("闪现").isEmpty())throw new AssertionError("Catalog did not expand");
        if(!progress)index.write(new FileOutputStream("app/src/main/assets/qiman-catalog.tsv"));
        System.out.println("Expanded official catalog: "+index.size()+" unique books from "+lines.size()+" listing pages.");
    }
}
