package app.jingman.reader;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

/** Traverse official first-level filters and their pagination; persist work for resuming. */
public final class QimanCatalogScanner {
    public interface Reader { String read(String url)throws IOException; }
    private static final Pattern LISTING=Pattern.compile("(?is)<div\\b[^>]*class=[\"'][^\"']*\\bmainForm\\b[^\"']*[\"']");
    public final QimanSearchIndex index;
    private final LinkedHashSet<String> pending=new LinkedHashSet<>(),visited=new LinkedHashSet<>();
    private final Set<String> deferred=new HashSet<>();
    private String current;
    private boolean expanded;
    public QimanCatalogScanner(QimanSearchIndex index,InputStream checkpoint)throws IOException {
        this.index=index;
        boolean version=false;
        if(checkpoint!=null)try(BufferedReader in=new BufferedReader(new InputStreamReader(checkpoint,"UTF-8"))){
            String line;while((line=in.readLine())!=null){
                String[] fields=line.split("\t",-1);if(fields.length!=2)continue;
                if(fields[0].equals("#scan-version"))version=fields[1].equals("1");
                else if(fields[0].equals("#expanded"))expanded=fields[1].equals("true");
                else if(fields[0].equals("#pending")&&allowed(fields[1]))pending.add(fields[1]);
                else if(fields[0].equals("#visited")&&allowed(fields[1]))visited.add(fields[1]);
            }
        }
        pending.removeAll(visited);
        // A completed scan starts a fresh traversal next time; partial scans resume.
        if(!version||pending.isEmpty()){pending.clear();visited.clear();expanded=false;enqueue(SiteIndex.ORIGIN+"/");enqueue(SiteIndex.ORIGIN+"/category");}
    }
    static boolean allowed(String url){
        return url.equals(SiteIndex.ORIGIN+"/")||url.matches(Pattern.quote(SiteIndex.ORIGIN)+"/category(?:/(?:tags|list|status|order)/[A-Za-z0-9]+)?(?:/page/[1-9][0-9]*)?");
    }
    private void enqueue(String url){if(url!=null&&allowed(url)&&!visited.contains(url))pending.add(url);}
    public boolean hasNext(){for(String url:pending)if(!deferred.contains(url))return true;return false;}
    public void deferFailure(){if(current!=null&&pending.contains(current))deferred.add(current);}
    public int done(){return visited.size();}
    public int remaining(){return pending.size();}
    public void step(Reader reader)throws IOException {
        if(!hasNext())return;
        if(visited.size()>=20000)throw new IOException("扫描已达 20000 页，已保存进度，尚未完成");
        String url=null;for(String candidate:pending)if(!deferred.contains(candidate)){url=candidate;break;}
        current=url;String html=reader.read(url);
        boolean category=!url.equals(SiteIndex.ORIGIN+"/");
        if(category&&!LISTING.matcher(html).find())throw new IOException("官网分类返回异常页面，扫描进度已保留");
        SiteIndex.Page page=SiteIndex.parse(html,url);
        for(SiteIndex.Entry entry:page.books)index.add(entry);
        // Remove work only after parsing succeeds, so failed requests can be retried.
        pending.remove(url);visited.add(url);
        if(url.equals(SiteIndex.ORIGIN+"/category")&&!expanded){
            for(SiteIndex.Entry filter:page.filters)enqueue(filter.url);
            enqueue(SiteIndex.ORIGIN+"/category/order/addtime");enqueue(SiteIndex.ORIGIN+"/category/order/hits");expanded=true;
        }
        if(category&&page.next!=null){
            String root=url.replaceFirst("/page/[0-9]+$","");
            if(page.next.matches(Pattern.quote(root)+"/page/[1-9][0-9]*"))enqueue(page.next);
        }
    }
    public void write(OutputStream out)throws IOException {
        StringBuilder meta=new StringBuilder("#scan-version\t1\n#expanded\t"+expanded+"\n");
        for(String url:pending)meta.append("#pending\t").append(url).append('\n');
        for(String url:visited)meta.append("#visited\t").append(url).append('\n');
        out.write(meta.toString().getBytes("UTF-8"));
        ByteArrayOutputStream data=new ByteArrayOutputStream();index.write(data);data.writeTo(out);out.flush();
    }
}
