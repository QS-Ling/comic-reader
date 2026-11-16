package app.jingman.reader;
import java.io.*;
import java.nio.file.*;
public class BuildQimanCatalog {
    public static void main(String[] args)throws Exception{
        QimanSearchIndex index=new QimanSearchIndex(new ByteArrayInputStream(new byte[0]));
        for(int i=0;i<=50;i++){
            String name=i==0?"home":Integer.toString(i);
            String base=SiteIndex.ORIGIN+(i==0?"/":i==1?"/category":"/category/page/"+i);
            String html=new String(Files.readAllBytes(Paths.get("build/qiman-catalog/"+name+".html")),"UTF-8");
            SiteIndex.Page page=SiteIndex.parse(html,base);
            if(page.books.isEmpty())throw new AssertionError("Missing official books on page "+name);
            for(SiteIndex.Entry e:page.books)index.add(e);
        }
        if(index.size()<500||index.search("元尊").isEmpty()||index.search("斗罗").isEmpty())throw new AssertionError("Incomplete seed catalog");
        index.write(new FileOutputStream("app/src/main/assets/qiman-catalog.tsv"));
        System.out.println("Official catalog seed: "+index.size()+" books; 元尊 and 斗罗 matches verified.");
    }
}
