package app.jingman.reader;
import java.io.*;
import java.util.*;

/** Store real official homepage entries, independently of the much larger search index. */
public final class QimanHomeCache {
    static final Object FILE_LOCK=new Object();
    public static SiteIndex.Page read(InputStream source)throws IOException {
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        try(InputStream in=source){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(bytes.size()+n>1_000_000)throw new IOException("首页快照过大");bytes.write(buffer,0,n);}}
        byte[] data=bytes.toByteArray();long time=0;
        try(BufferedReader in=new BufferedReader(new InputStreamReader(new ByteArrayInputStream(data),"UTF-8"))){String line;while((line=in.readLine())!=null)if(line.startsWith("#home-updated\t")){try{time=Long.parseLong(line.substring(14));}catch(NumberFormatException e){throw new IOException("首页快照时间无效");}}}
        QimanSearchIndex index=new QimanSearchIndex(new ByteArrayInputStream(data));
        if(index.size()==0||index.size()>500||time<=0)throw new IOException("首页快照无效");
        SiteIndex.Page page=new SiteIndex.Page();page.books.addAll(index.entries());page.savedHome=true;page.snapshotTime=time;return page;
    }
    public static void write(SiteIndex.Page page,long time,OutputStream out)throws IOException {
        QimanSearchIndex index=new QimanSearchIndex(new ByteArrayInputStream(new byte[0]));for(SiteIndex.Entry entry:page.books)index.add(entry);
        if(index.size()==0||index.size()>500||time<=0)throw new IOException("官网首页未返回有效书目");
        ByteArrayOutputStream data=new ByteArrayOutputStream();index.write(data);
        out.write(("#home-updated\t"+time+"\n").getBytes("UTF-8"));data.writeTo(out);out.flush();
    }
    public static SiteIndex.Page withNote(SiteIndex.Page saved,String note){
        SiteIndex.Page result=new SiteIndex.Page();result.books.addAll(saved.books);result.savedHome=true;result.snapshotTime=saved.snapshotTime;result.sourceNote=note;return result;
    }
    public static boolean sameBooks(SiteIndex.Page a,SiteIndex.Page b){
        if(a.books.size()!=b.books.size())return false;
        for(int i=0;i<a.books.size();i++){SiteIndex.Entry x=a.books.get(i),y=b.books.get(i);if(!x.url.equals(y.url)||!x.title.equals(y.title)||!x.cover.equals(y.cover))return false;}return true;
    }
}
