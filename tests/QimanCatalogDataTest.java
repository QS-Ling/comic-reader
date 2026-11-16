package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
import java.util.*;
public class QimanCatalogDataTest {
    public static void main(String[] args)throws Exception{
        List<String> rows=Files.readAllLines(Paths.get("app/src/main/assets/qiman-catalog.tsv"));Set<String> urls=new HashSet<>();
        for(String row:rows){
            String[] fields=row.split("\t",-1);
            if(fields.length!=3||!fields[0].equals(SiteIndex.normalize(fields[0]))||!SiteIndex.isComic(fields[0])||fields[1].trim().isEmpty()||!urls.add(fields[0]))throw new AssertionError("Invalid catalog row "+row);
            if(!fields[2].isEmpty()&&!fields[2].equals(SiteIndex.coverUrl(fields[2],SiteIndex.ORIGIN)))throw new AssertionError("Unsafe cover");
        }
        if(urls.size()<5000)throw new AssertionError("Broad catalog missing");
        QimanSearchIndex index=new QimanSearchIndex(new FileInputStream("app/src/main/assets/qiman-catalog.tsv"));
        if(index.size()!=urls.size())throw new AssertionError("Not all persisted rows load");
        for(String word:Arrays.asList("闪现","元尊","斗罗","学院","重生"))if(index.search(word).isEmpty())throw new AssertionError("Missing query "+word);
        System.out.println("Catalog data verified: "+urls.size()+" unique canonical comic URLs, titles and safe covers; local keyword search passed.");
    }
}
